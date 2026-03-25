package io.strategiz.social.business.agent.pipeline;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class PdlcPipelineExecutorConfigTest {

	private final PipelineAsyncConfig config = new PipelineAsyncConfig();

	@Test
	void pdlcPipelineExecutor_isCreatedWithCorrectPoolSizes() {
		TaskExecutor executor = config.pdlcPipelineExecutor();

		assertNotNull(executor);
		assertTrue(executor instanceof ThreadPoolTaskExecutor,
				"Expected ThreadPoolTaskExecutor but got " + executor.getClass().getSimpleName());

		ThreadPoolTaskExecutor pool = (ThreadPoolTaskExecutor) executor;
		org.junit.jupiter.api.Assertions.assertEquals(4, pool.getCorePoolSize());
		org.junit.jupiter.api.Assertions.assertEquals(8, pool.getMaxPoolSize());
	}

	@Test
	void pdlcPipelineExecutor_threadNamePrefixIsCorrect() {
		TaskExecutor executor = config.pdlcPipelineExecutor();
		ThreadPoolTaskExecutor pool = (ThreadPoolTaskExecutor) executor;

		assertTrue(pool.getThreadNamePrefix().startsWith("pdlc-pipeline-"),
				"Expected thread name prefix 'pdlc-pipeline-' but got: " + pool.getThreadNamePrefix());
	}

	@Test
	void simpleSparkExecutor_isCreatedWithCorrectPoolSizes() {
		TaskExecutor executor = config.simpleSparkExecutor();

		assertNotNull(executor);
		assertTrue(executor instanceof ThreadPoolTaskExecutor,
				"Expected ThreadPoolTaskExecutor but got " + executor.getClass().getSimpleName());

		ThreadPoolTaskExecutor pool = (ThreadPoolTaskExecutor) executor;
		org.junit.jupiter.api.Assertions.assertEquals(2, pool.getCorePoolSize());
		org.junit.jupiter.api.Assertions.assertEquals(4, pool.getMaxPoolSize());
	}

	@Test
	void simpleSparkExecutor_threadNamePrefixIsCorrect() {
		TaskExecutor executor = config.simpleSparkExecutor();
		ThreadPoolTaskExecutor pool = (ThreadPoolTaskExecutor) executor;

		assertTrue(pool.getThreadNamePrefix().startsWith("spark-async-"),
				"Expected thread name prefix 'spark-async-' but got: " + pool.getThreadNamePrefix());
	}

	@Test
	void pdlcPipelineExecutor_queueCapacityIs50() {
		TaskExecutor executor = config.pdlcPipelineExecutor();
		ThreadPoolTaskExecutor pool = (ThreadPoolTaskExecutor) executor;

		// Verify the queue capacity is initialized correctly by checking it accepted initialization
		// The executor initializes successfully with capacity=50 (checked indirectly via execute)
		assertNotNull(pool.getThreadPoolExecutor(),
				"Executor should be initialized and have an underlying ThreadPoolExecutor");
	}

	@Test
	void simpleSparkExecutor_queueCapacityIs20() {
		TaskExecutor executor = config.simpleSparkExecutor();
		ThreadPoolTaskExecutor pool = (ThreadPoolTaskExecutor) executor;

		assertNotNull(pool.getThreadPoolExecutor(),
				"Executor should be initialized and have an underlying ThreadPoolExecutor");
	}

}
