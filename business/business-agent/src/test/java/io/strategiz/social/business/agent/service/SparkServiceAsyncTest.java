package io.strategiz.social.business.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.Spark;
import io.strategiz.social.data.entity.SparkState;
import io.strategiz.social.data.repository.CheckpointRepository;
import io.strategiz.social.data.repository.ExecutionLogRepository;
import io.strategiz.social.data.repository.SparkRepository;
import io.strategiz.social.data.repository.TacticRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SparkServiceAsyncTest {

	private static final String USER_ID = "user-abc";

	private static final String PARENT_SPARK_ID = "spark-parent-001";

	@Mock
	private SparkRepository sparkRepository;

	@Mock
	private TacticRepository tacticRepository;

	@Mock
	private CheckpointRepository checkpointRepository;

	@Mock
	private ExecutionLogRepository executionLogRepository;

	@Mock
	private DeviceRoutingService deviceRoutingService;

	@Mock
	private ActivityBroadcaster activityBroadcaster;

	@Mock
	private SparkClassifierService sparkClassifierService;

	@Mock
	private SparkDispatchService sparkDispatchService;

	private SparkService sparkService;

	@BeforeEach
	void setUp() {
		sparkService = new SparkService(sparkRepository, tacticRepository, checkpointRepository,
				executionLogRepository, deviceRoutingService, activityBroadcaster,
				Optional.empty(), sparkClassifierService, sparkDispatchService);
	}

	// --- markAsync ---

	@Test
	void markAsync_setsPipelineExecutionModeToAsync() {
		Spark spark = new Spark();
		spark.setId(PARENT_SPARK_ID);
		spark.setUserId(USER_ID);
		spark.setStatus(SparkState.EXECUTING);

		when(sparkRepository.findById(PARENT_SPARK_ID)).thenReturn(Optional.of(spark));

		sparkService.markAsync(PARENT_SPARK_ID);

		ArgumentCaptor<Spark> captor = ArgumentCaptor.forClass(Spark.class);
		verify(sparkRepository).save(captor.capture(), eq(USER_ID));

		Spark saved = captor.getValue();
		assertEquals("ASYNC", saved.getPipelineExecutionMode());
	}

	@Test
	void markAsync_sparkNotFound_doesNotThrow() {
		when(sparkRepository.findById("nonexistent")).thenReturn(Optional.empty());

		// Should not throw — follows the same ifPresent pattern as other methods
		sparkService.markAsync("nonexistent");

		// No save should have been called
		verify(sparkRepository, org.mockito.Mockito.never()).save(any(), any());
	}

	// --- createChildSpark (simplified overload) ---

	@Test
	void createChildSpark_returnsNewSparkId() {
		String childId = sparkService.createChildSpark(PARENT_SPARK_ID, PdlcRole.PM, USER_ID);

		assertNotNull(childId);
		assertTrue(!childId.isBlank(), "Returned spark ID should not be blank");
	}

	@Test
	void createChildSpark_setsParentSparkId() {
		ArgumentCaptor<Spark> captor = ArgumentCaptor.forClass(Spark.class);

		sparkService.createChildSpark(PARENT_SPARK_ID, PdlcRole.ARCHITECT, USER_ID);

		verify(sparkRepository).save(captor.capture(), eq(USER_ID));
		assertEquals(PARENT_SPARK_ID, captor.getValue().getParentSparkId());
	}

	@Test
	void createChildSpark_setsPdlcRole() {
		ArgumentCaptor<Spark> captor = ArgumentCaptor.forClass(Spark.class);

		sparkService.createChildSpark(PARENT_SPARK_ID, PdlcRole.IMPLEMENTER, USER_ID);

		verify(sparkRepository).save(captor.capture(), eq(USER_ID));
		assertEquals(PdlcRole.IMPLEMENTER, captor.getValue().getPdlcRole());
	}

	@Test
	void createChildSpark_setsPipelineExecutionMode() {
		ArgumentCaptor<Spark> captor = ArgumentCaptor.forClass(Spark.class);

		sparkService.createChildSpark(PARENT_SPARK_ID, PdlcRole.REVIEWER, USER_ID);

		verify(sparkRepository).save(captor.capture(), eq(USER_ID));
		assertEquals("PIPELINE", captor.getValue().getPipelineExecutionMode());
	}

	@Test
	void createChildSpark_setsUserId() {
		ArgumentCaptor<Spark> captor = ArgumentCaptor.forClass(Spark.class);

		sparkService.createChildSpark(PARENT_SPARK_ID, PdlcRole.PM, USER_ID);

		verify(sparkRepository).save(captor.capture(), eq(USER_ID));
		assertEquals(USER_ID, captor.getValue().getUserId());
	}

	@Test
	void createChildSpark_setsStatusToPending() {
		ArgumentCaptor<Spark> captor = ArgumentCaptor.forClass(Spark.class);

		sparkService.createChildSpark(PARENT_SPARK_ID, PdlcRole.PM, USER_ID);

		verify(sparkRepository).save(captor.capture(), eq(USER_ID));
		assertEquals(SparkState.PENDING, captor.getValue().getStatus());
	}

	@Test
	void createChildSpark_returnsIdMatchingPersistedSpark() {
		ArgumentCaptor<Spark> captor = ArgumentCaptor.forClass(Spark.class);

		String returnedId = sparkService.createChildSpark(PARENT_SPARK_ID, PdlcRole.PM, USER_ID);

		verify(sparkRepository).save(captor.capture(), eq(USER_ID));
		assertEquals(returnedId, captor.getValue().getId(),
				"The returned ID must match the ID on the persisted spark");
	}

	@Test
	void createChildSpark_differentRolesDontShareSameId() {
		String pmId = sparkService.createChildSpark(PARENT_SPARK_ID, PdlcRole.PM, USER_ID);
		String archId = sparkService.createChildSpark(PARENT_SPARK_ID, PdlcRole.ARCHITECT, USER_ID);

		assertTrue(!pmId.equals(archId), "Each child spark should have a unique ID");
	}

}
