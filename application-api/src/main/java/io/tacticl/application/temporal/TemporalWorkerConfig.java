package io.tacticl.application.temporal;

import io.tacticl.application.temporal.smoke.SmokeWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bootstraps the in-JVM Temporal workers per SAD §3.2.
 *
 * <p>Three workers are registered, one per task queue:
 * <ul>
 *   <li>{@code cloud-agent-session-tq} — hosts {@code CloudAgentSessionWorkflow}
 *       (Phase 4) and the {@code SmokeWorkflow} placeholder used to verify the
 *       SDK is wired correctly.</li>
 *   <li>{@code pipeline-tq} — hosts {@code PipelineWorkflow} (Phase 3). No
 *       workflows registered yet; the worker polls but receives no work.</li>
 *   <li>{@code voice-activity-tq} — hosts low-latency voice activities such as
 *       {@code elevenlabs_speak} (Phase 5). Activities registered later.</li>
 * </ul>
 *
 * <p>Per SAD §3.1, v1 connects in plain trust on the private network (no
 * mTLS). The Spring Boot starter is intentionally not used — manual beans give
 * a clearer wiring story and avoid auto-config conflicts with the rest of the
 * application's hand-rolled configuration.
 *
 * <p>Workers are started via {@link ApplicationRunner} so that they only begin
 * polling after the full Spring context is ready. {@link #shutdown()} drains
 * them on JVM exit.
 */
@Configuration
@EnableConfigurationProperties(TemporalProperties.class)
public class TemporalWorkerConfig {

    private static final Logger log = LoggerFactory.getLogger(TemporalWorkerConfig.class);

    private WorkerFactory workerFactory;

    @Bean(destroyMethod = "shutdownNow")
    public WorkflowServiceStubs workflowServiceStubs(TemporalProperties props) {
        log.info("Initializing Temporal service stubs target={}", props.target());
        WorkflowServiceStubsOptions options = WorkflowServiceStubsOptions.newBuilder()
                .setTarget(props.target())
                .build();
        return WorkflowServiceStubs.newServiceStubs(options);
    }

    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs stubs, TemporalProperties props) {
        log.info("Creating Temporal WorkflowClient namespace={}", props.namespace());
        WorkflowClientOptions options = WorkflowClientOptions.newBuilder()
                .setNamespace(props.namespace())
                .build();
        return WorkflowClient.newInstance(stubs, options);
    }

    @Bean
    public WorkerFactory workerFactory(WorkflowClient client, TemporalProperties props) {
        WorkerFactory factory = WorkerFactory.newInstance(client);

        TemporalProperties.TaskQueues queues = props.taskQueues();

        // Cloud agent session worker — hosts CloudAgentSessionWorkflow (Phase 4)
        // and the smoke workflow used to verify the SDK is wired.
        Worker sessionWorker = factory.newWorker(queues.cloudAgentSession());
        sessionWorker.registerWorkflowImplementationTypes(SmokeWorkflowImpl.class);

        // Pipeline worker — hosts PipelineWorkflow (Phase 3). No workflows yet.
        factory.newWorker(queues.pipeline());

        // Voice activity worker — hosts low-latency voice activities (Phase 5).
        // No activities yet.
        factory.newWorker(queues.voiceActivity());

        this.workerFactory = factory;
        return factory;
    }

    /**
     * Starts the worker factory after the context is fully initialized so the
     * workers only begin polling Temporal once every dependency they need is
     * available.
     */
    @Bean
    public ApplicationRunner temporalWorkerStarter(WorkerFactory factory, TemporalProperties props) {
        return args -> {
            log.info("Starting Temporal workers namespace={} queues=[{}, {}, {}]",
                    props.namespace(),
                    props.taskQueues().cloudAgentSession(),
                    props.taskQueues().pipeline(),
                    props.taskQueues().voiceActivity());
            factory.start();
        };
    }

    @PreDestroy
    public void shutdown() {
        if (workerFactory != null) {
            log.info("Shutting down Temporal worker factory");
            workerFactory.shutdown();
        }
    }
}
