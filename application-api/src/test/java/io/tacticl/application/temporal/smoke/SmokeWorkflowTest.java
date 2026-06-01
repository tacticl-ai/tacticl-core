package io.tacticl.application.temporal.smoke;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proves the Temporal Java SDK is wired correctly and the
 * {@link TestWorkflowEnvironment} harness works end-to-end.
 *
 * <p>This is the foundation Phase 3+ workflow tests will build on — every later
 * workflow test follows the same pattern: spin up {@link TestWorkflowEnvironment},
 * register the workflow impl on a task queue, mock activities, exercise the
 * workflow via the client stub.
 */
class SmokeWorkflowTest {

    private static final String TASK_QUEUE = "cloud-agent-session-tq";

    private TestWorkflowEnvironment testEnv;
    private WorkflowClient client;

    @BeforeEach
    void setUp() {
        testEnv = TestWorkflowEnvironment.newInstance();
        Worker worker = testEnv.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(SmokeWorkflowImpl.class);
        testEnv.start();
        client = testEnv.getWorkflowClient();
    }

    @AfterEach
    void tearDown() {
        testEnv.close();
    }

    @Test
    void ping_returnsPong() {
        SmokeWorkflow stub = client.newWorkflowStub(
                SmokeWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(TASK_QUEUE)
                        .build());

        String result = stub.ping();

        assertEquals("pong", result);
    }
}
