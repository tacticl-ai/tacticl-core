package io.strategiz.social.business.agent.pipeline;

import io.strategiz.social.data.entity.PipelineEventType;
import io.strategiz.social.data.entity.PipelineRun;
import io.strategiz.social.data.entity.UserConfig;
import io.strategiz.social.data.repository.PipelineRunRepository;
import java.math.BigDecimal;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Manages cost tracking and ceiling enforcement for PDLC pipeline runs. Compares the run's
 * accumulated cost against the user's configured ceiling and warning threshold, emits events
 * when thresholds are crossed, and prevents pipelines from starting when spending is disabled.
 */
@Service
public class PipelineCostManager {

	private static final Logger log = LoggerFactory.getLogger(PipelineCostManager.class);

	private final PipelineEventEmitter pipelineEventEmitter;

	private final PipelineRunRepository pipelineRunRepository;

	public PipelineCostManager(PipelineEventEmitter pipelineEventEmitter,
			PipelineRunRepository pipelineRunRepository) {
		this.pipelineEventEmitter = pipelineEventEmitter;
		this.pipelineRunRepository = pipelineRunRepository;
	}

	/**
	 * Check the run's current accumulated cost against the user's pipeline cost ceiling and
	 * warning threshold. Emits {@code COST_THRESHOLD_WARNING} or {@code COST_CEILING_REACHED}
	 * events as appropriate.
	 *
	 * @param run    the pipeline run whose cost is being evaluated
	 * @param config the user's configuration containing ceiling and threshold settings
	 * @return {@link CostCheckResult#CEILING_REACHED} if {@code totalCost >= pipelineCostCeiling},
	 *         {@link CostCheckResult#WARNING} if {@code totalCost >= ceiling * warningThreshold},
	 *         {@link CostCheckResult#OK} otherwise
	 */
	public CostCheckResult checkCostCeiling(PipelineRun run, UserConfig config) {
		BigDecimal totalCost = run.getTotalCost() != null ? run.getTotalCost() : BigDecimal.ZERO;
		BigDecimal ceiling = config.getPipelineCostCeiling() != null
				? config.getPipelineCostCeiling()
				: BigDecimal.ZERO;

		// Ceiling check (>= ceiling)
		if (totalCost.compareTo(ceiling) >= 0) {
			log.warn("[COST] Ceiling reached: run={} totalCost={} ceiling={}",
					run.getId(), totalCost, ceiling);
			pipelineEventEmitter.emitEvent(run, PipelineEventType.COST_CEILING_REACHED, null,
					Map.of("totalCost", totalCost, "ceiling", ceiling));
			return CostCheckResult.CEILING_REACHED;
		}

		// Warning check (>= ceiling * warningThreshold)
		double threshold = config.getCostWarningThreshold();
		BigDecimal warningBoundary = ceiling.multiply(BigDecimal.valueOf(threshold));
		if (totalCost.compareTo(warningBoundary) >= 0) {
			log.info("[COST] Warning threshold crossed: run={} totalCost={} threshold={}",
					run.getId(), totalCost, warningBoundary);
			pipelineEventEmitter.emitEvent(run, PipelineEventType.COST_THRESHOLD_WARNING, null,
					Map.of("totalCost", totalCost, "ceiling", ceiling, "warningThreshold", threshold));
			return CostCheckResult.WARNING;
		}

		return CostCheckResult.OK;
	}

	/**
	 * Determine whether a new pipeline run may be started for this user. Returns {@code false}
	 * when the user's spending limit is zero (spending disabled) to prevent accidental cost
	 * accumulation before the user has explicitly enabled AI spend.
	 *
	 * @param config the user's configuration
	 * @return {@code false} if {@code spendingLimit} is zero or null, {@code true} otherwise
	 */
	public boolean canStartPipeline(UserConfig config) {
		BigDecimal limit = config.getSpendingLimit();
		if (limit == null || limit.compareTo(BigDecimal.ZERO) == 0) {
			log.info("[COST] Pipeline start blocked: spendingLimit is zero");
			return false;
		}
		return true;
	}

	/**
	 * Add role-level token and cost metrics to the pipeline run's cumulative totals and persist
	 * the updated run to Firestore.
	 *
	 * @param run      the pipeline run to update
	 * @param tokens   the number of tokens consumed by the completed role
	 * @param roleCost the cost incurred by the completed role
	 */
	public void updateCumulativeCost(PipelineRun run, long tokens, BigDecimal roleCost) {
		run.setTotalTokens(run.getTotalTokens() + tokens);

		BigDecimal current = run.getTotalCost() != null ? run.getTotalCost() : BigDecimal.ZERO;
		BigDecimal addition = roleCost != null ? roleCost : BigDecimal.ZERO;
		run.setTotalCost(current.add(addition));

		pipelineRunRepository.save(run);

		log.debug("[COST] Updated cumulative cost: run={} totalTokens={} totalCost={}",
				run.getId(), run.getTotalTokens(), run.getTotalCost());
	}

}
