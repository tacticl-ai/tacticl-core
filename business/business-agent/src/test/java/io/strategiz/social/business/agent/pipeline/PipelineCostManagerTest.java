package io.strategiz.social.business.agent.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.strategiz.social.data.entity.PipelineEventType;
import io.strategiz.social.data.entity.PipelineRun;
import io.strategiz.social.data.entity.UserConfig;
import io.strategiz.social.data.repository.PipelineRunRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PipelineCostManagerTest {

	private static final String RUN_ID = "run-cost-001";

	private static final String SPARK_ID = "spark-cost-001";

	private static final String USER_ID = "user-cost-001";

	@Mock
	private PipelineEventEmitter pipelineEventEmitter;

	@Mock
	private PipelineRunRepository pipelineRunRepository;

	private PipelineCostManager costManager;

	@BeforeEach
	void setUp() {
		costManager = new PipelineCostManager(pipelineEventEmitter, pipelineRunRepository);
	}

	// --- helpers ---

	private PipelineRun createRun(BigDecimal totalCost) {
		PipelineRun run = new PipelineRun();
		run.setId(RUN_ID);
		run.setSparkId(SPARK_ID);
		run.setUserId(USER_ID);
		run.setTotalCost(totalCost);
		return run;
	}

	private UserConfig configWithCeiling(BigDecimal ceiling, double warningThreshold) {
		UserConfig config = new UserConfig();
		config.setPipelineCostCeiling(ceiling);
		config.setCostWarningThreshold(warningThreshold);
		return config;
	}

	// --- checkCostCeiling: OK ---

	@Test
	void checkCostCeiling_returnsOkWhenCostIsBelowWarningThreshold() {
		// ceiling=$50, threshold=80% → warning at $40; current=$30 → OK
		PipelineRun run = createRun(new BigDecimal("30.00"));
		UserConfig config = configWithCeiling(new BigDecimal("50"), 0.8);

		CostCheckResult result = costManager.checkCostCeiling(run, config);

		assertEquals(CostCheckResult.OK, result);
		verify(pipelineEventEmitter, never()).emitEvent(any(), any(), any(), any());
	}

	@Test
	void checkCostCeiling_returnsOkWhenCostIsZero() {
		PipelineRun run = createRun(BigDecimal.ZERO);
		UserConfig config = configWithCeiling(new BigDecimal("50"), 0.8);

		CostCheckResult result = costManager.checkCostCeiling(run, config);

		assertEquals(CostCheckResult.OK, result);
		verify(pipelineEventEmitter, never()).emitEvent(any(), any(), any(), any());
	}

	// --- checkCostCeiling: WARNING ---

	@Test
	void checkCostCeiling_returnsWarningWhenCostExceedsWarningThreshold() {
		// ceiling=$50, threshold=80% → warning at $40; current=$45 → WARNING
		PipelineRun run = createRun(new BigDecimal("45.00"));
		UserConfig config = configWithCeiling(new BigDecimal("50"), 0.8);

		CostCheckResult result = costManager.checkCostCeiling(run, config);

		assertEquals(CostCheckResult.WARNING, result);
		verify(pipelineEventEmitter).emitEvent(
				eq(run), eq(PipelineEventType.COST_THRESHOLD_WARNING), eq(null), any());
	}

	@Test
	void checkCostCeiling_returnsWarningAtExactWarningBoundary() {
		// ceiling=$50, threshold=80% → boundary=$40; current=$40 → WARNING
		PipelineRun run = createRun(new BigDecimal("40.00"));
		UserConfig config = configWithCeiling(new BigDecimal("50"), 0.8);

		CostCheckResult result = costManager.checkCostCeiling(run, config);

		assertEquals(CostCheckResult.WARNING, result);
		verify(pipelineEventEmitter).emitEvent(
				eq(run), eq(PipelineEventType.COST_THRESHOLD_WARNING), eq(null), any());
	}

	// --- checkCostCeiling: CEILING_REACHED ---

	@Test
	void checkCostCeiling_returnsCeilingReachedWhenCostEqualsCeiling() {
		// current=$50 == ceiling=$50 → CEILING_REACHED
		PipelineRun run = createRun(new BigDecimal("50.00"));
		UserConfig config = configWithCeiling(new BigDecimal("50"), 0.8);

		CostCheckResult result = costManager.checkCostCeiling(run, config);

		assertEquals(CostCheckResult.CEILING_REACHED, result);
		verify(pipelineEventEmitter).emitEvent(
				eq(run), eq(PipelineEventType.COST_CEILING_REACHED), eq(null), any());
	}

	@Test
	void checkCostCeiling_returnsCeilingReachedWhenCostExceedsCeiling() {
		// current=$55 > ceiling=$50 → CEILING_REACHED
		PipelineRun run = createRun(new BigDecimal("55.00"));
		UserConfig config = configWithCeiling(new BigDecimal("50"), 0.8);

		CostCheckResult result = costManager.checkCostCeiling(run, config);

		assertEquals(CostCheckResult.CEILING_REACHED, result);
		verify(pipelineEventEmitter).emitEvent(
				eq(run), eq(PipelineEventType.COST_CEILING_REACHED), eq(null), any());
	}

	@Test
	void checkCostCeiling_ceilingTakesPriorityOverWarning() {
		// cost >= ceiling should return CEILING_REACHED, not WARNING
		PipelineRun run = createRun(new BigDecimal("50.00"));
		UserConfig config = configWithCeiling(new BigDecimal("50"), 0.8);

		CostCheckResult result = costManager.checkCostCeiling(run, config);

		assertEquals(CostCheckResult.CEILING_REACHED, result);
		// COST_THRESHOLD_WARNING should NOT be emitted when ceiling is reached first
		verify(pipelineEventEmitter, never()).emitEvent(
				eq(run), eq(PipelineEventType.COST_THRESHOLD_WARNING), eq(null), any());
	}

	// --- canStartPipeline tests ---

	@Test
	void canStartPipeline_returnsFalseWhenSpendingLimitIsZero() {
		UserConfig config = new UserConfig();
		config.setSpendingLimit(BigDecimal.ZERO);

		boolean result = costManager.canStartPipeline(config);

		assertFalse(result, "Should block pipeline start when spending limit is zero");
	}

	@Test
	void canStartPipeline_returnsFalseWhenSpendingLimitIsNull() {
		UserConfig config = new UserConfig();
		config.setSpendingLimit(null);

		boolean result = costManager.canStartPipeline(config);

		assertFalse(result, "Should block pipeline start when spending limit is null");
	}

	@Test
	void canStartPipeline_returnsTrueWhenSpendingLimitIsPositive() {
		UserConfig config = new UserConfig();
		config.setSpendingLimit(new BigDecimal("100.00"));

		boolean result = costManager.canStartPipeline(config);

		assertTrue(result, "Should allow pipeline start when spending limit is set");
	}

	@Test
	void canStartPipeline_returnsTrueWhenSpendingLimitIsSmallPositive() {
		UserConfig config = new UserConfig();
		config.setSpendingLimit(new BigDecimal("0.01"));

		boolean result = costManager.canStartPipeline(config);

		assertTrue(result, "Should allow pipeline start for any positive spending limit");
	}

	// --- updateCumulativeCost tests ---

	@Test
	void updateCumulativeCost_addsToCumulativeTokensAndCost() {
		PipelineRun run = createRun(new BigDecimal("10.00"));
		run.setTotalTokens(5000L);

		costManager.updateCumulativeCost(run, 2000L, new BigDecimal("3.50"));

		assertEquals(7000L, run.getTotalTokens());
		assertEquals(new BigDecimal("13.50"), run.getTotalCost());
		verify(pipelineRunRepository).save(run);
	}

	@Test
	void updateCumulativeCost_handlesNullRoleCostAsZero() {
		PipelineRun run = createRun(new BigDecimal("5.00"));
		run.setTotalTokens(1000L);

		costManager.updateCumulativeCost(run, 500L, null);

		assertEquals(1500L, run.getTotalTokens());
		assertEquals(new BigDecimal("5.00"), run.getTotalCost());
		verify(pipelineRunRepository).save(run);
	}

	@Test
	void updateCumulativeCost_accumulates_acrossMultipleCalls() {
		PipelineRun run = createRun(BigDecimal.ZERO);
		run.setTotalTokens(0L);

		costManager.updateCumulativeCost(run, 1000L, new BigDecimal("1.00"));
		costManager.updateCumulativeCost(run, 2000L, new BigDecimal("2.50"));
		costManager.updateCumulativeCost(run, 500L, new BigDecimal("0.50"));

		assertEquals(3500L, run.getTotalTokens());
		assertEquals(new BigDecimal("4.00"), run.getTotalCost());
	}

}
