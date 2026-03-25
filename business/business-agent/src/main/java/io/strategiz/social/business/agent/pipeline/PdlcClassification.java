package io.strategiz.social.business.agent.pipeline;

import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineTier;
import java.util.List;
import java.util.Map;

/**
 * Output of the PDLC depth classifier.
 *
 * <p>Produced by {@link PdlcClassifierService} for {@code code} and {@code devops} sparks.
 * Encodes the recommended pipeline tier, the named playbook to run, per-dimension rubric
 * scores, the confidence of the classification, and chain-of-thought reasoning for
 * traceability and analytics.</p>
 *
 * <p>Confidence gating (applied by the caller):
 * <ul>
 *   <li>> 0.85 — auto-route, inform user</li>
 *   <li>0.50–0.85 — propose via checkpoint</li>
 *   <li>&lt; 0.50 — ask user directly</li>
 * </ul>
 * </p>
 *
 * @param tier             recommended pipeline depth (SIMPLE, PLAYBOOK, FULL_PDLC)
 * @param playbook         playbook name (e.g., "BUG_FIX", "FULL_PDLC"), null for SIMPLE
 * @param confidence       classification confidence [0.0, 1.0]
 * @param activatedRoles   roles to execute (empty list for SIMPLE)
 * @param skippedRoles     roles not needed for this spark
 * @param dimensionScores  per-dimension rubric scores (scope, risk, domainBreadth, etc.)
 * @param reasoning        chain-of-thought explanation from the classifier
 */
public record PdlcClassification(
		PipelineTier tier,
		String playbook,
		double confidence,
		List<PdlcRole> activatedRoles,
		List<PdlcRole> skippedRoles,
		Map<String, Integer> dimensionScores,
		String reasoning) {

	/**
	 * Returns {@code true} if this classification recommends human confirmation before routing.
	 * Confidence between 0.50 and 0.85 (inclusive) triggers a checkpoint proposal.
	 */
	public boolean needsConfirmation() {
		return confidence >= 0.50 && confidence <= 0.85;
	}

	/**
	 * Returns {@code true} if confidence is below 0.50 — caller should ask the user directly
	 * how to proceed rather than proposing a specific path.
	 */
	public boolean needsUserInput() {
		return confidence < 0.50;
	}

	/**
	 * Returns {@code true} if the classifier is confident enough to auto-route without
	 * user confirmation (confidence > 0.85).
	 */
	public boolean isAutoRoute() {
		return confidence > 0.85;
	}

	/**
	 * Constructs a SIMPLE fallback classification used when the engine is unavailable or
	 * the spark type does not warrant PDLC depth analysis.
	 */
	public static PdlcClassification simple() {
		return new PdlcClassification(
				PipelineTier.SIMPLE,
				null,
				0.0,
				List.of(),
				List.of(),
				Map.of(),
				"Defaulted to SIMPLE (no classification performed)");
	}

}
