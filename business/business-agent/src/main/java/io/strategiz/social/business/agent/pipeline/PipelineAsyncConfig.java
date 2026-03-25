package io.strategiz.social.business.agent.pipeline;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Async configuration for PDLC pipeline execution. Provides two dedicated thread pools:
 * one for full multi-role pipeline runs, and one for lightweight async single-spark execution
 * (timeout-based hybrid routing).
 */
@Configuration
@EnableAsync
public class PipelineAsyncConfig {

	/**
	 * Executor for full PDLC pipeline runs (multi-role playbooks). Sized to support concurrent
	 * pipelines where each pipeline may occupy a thread for an extended duration while it
	 * iterates through PDLC roles sequentially.
	 */
	@Bean("pdlcPipelineExecutor")
	public TaskExecutor pdlcPipelineExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(4);
		executor.setMaxPoolSize(8);
		executor.setQueueCapacity(50);
		executor.setThreadNamePrefix("pdlc-pipeline-");
		executor.initialize();
		return executor;
	}

	/**
	 * Executor for lightweight async single-spark execution (timeout-based hybrid routing).
	 * Used by {@code VoiceAgentService#executeWithTimeout} when a spark is dispatched
	 * asynchronously while the controller awaits within a bounded timeout window.
	 */
	@Bean("simpleSparkExecutor")
	public TaskExecutor simpleSparkExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(20);
		executor.setThreadNamePrefix("spark-async-");
		executor.initialize();
		return executor;
	}

}
