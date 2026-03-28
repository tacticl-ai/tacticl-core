package io.strategiz.social.business.agent.pipeline;

import io.cidadel.business.ai.engine.AiEngineRouterService;
import io.cidadel.framework.ai.engine.model.AiEngineRequest;
import io.cidadel.framework.ai.engine.model.AiEngineResult;
import io.strategiz.social.data.entity.AiSdlcStep;
import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineTier;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Stage 2 PDLC depth classifier — determines whether a spark needs SIMPLE execution,
 * a named PLAYBOOK, or a FULL_PDLC (12-role) pipeline.
 *
 * <p>Only runs for {@code code} and {@code devops} spark types. All other types return
 * {@link PdlcClassification#simple()} immediately.</p>
 *
 * <p>Uses a multi-dimensional rubric with chain-of-thought reasoning:
 * <ul>
 *   <li>Scope (25%) — number of components, files, and systems affected</li>
 *   <li>Risk (20%) — greenfield vs modification, data model changes</li>
 *   <li>Domain breadth (15%) — backend-only, frontend-only, full-stack</li>
 *   <li>Integration surface (15%) — external APIs, auth, databases</li>
 *   <li>Testing complexity (15%) — unit sufficient vs E2E/integration needed</li>
 *   <li>Reversibility (10%) — easy rollback vs hard (migrations, schema changes)</li>
 * </ul>
 * </p>
 *
 * <p>Confidence gating:
 * <ul>
 *   <li>&gt; 0.85 — auto-route</li>
 *   <li>0.50–0.85 — propose via checkpoint ({@link PdlcClassification#needsConfirmation()})</li>
 *   <li>&lt; 0.50 — ask user directly ({@link PdlcClassification#needsUserInput()})</li>
 * </ul>
 * </p>
 *
 * <p>On any engine failure or JSON parse error, falls back to SIMPLE with confidence 0.0.</p>
 */
@Service
public class PdlcClassifierService {

	private static final Logger log = LoggerFactory.getLogger(PdlcClassifierService.class);

	private static final Set<String> ELIGIBLE_SPARK_TYPES = Set.of("code", "devops");

	private static final JsonMapper MAPPER = new JsonMapper();

	/**
	 * Ordered role sequences for each named playbook, matching the spec's playbook table.
	 * FULL_PDLC includes the complete 12-role pipeline (Tester ∥ Security, Tech Writer ∥ DevOps are
	 * parallel groups — ordering here reflects execution sequence for traceability).
	 */
	private static final Map<String, List<PdlcRole>> PLAYBOOK_ROLES = new HashMap<>();

	static {
		PLAYBOOK_ROLES.put("FULL_PDLC", List.of(
				PdlcRole.PM, PdlcRole.RESEARCHER, PdlcRole.ARCHITECT, PdlcRole.DESIGNER,
				PdlcRole.PLANNER, PdlcRole.IMPLEMENTER, PdlcRole.REVIEWER,
				PdlcRole.TESTER, PdlcRole.SECURITY_ANALYST, PdlcRole.TECHNICAL_WRITER,
				PdlcRole.DEVOPS, PdlcRole.RETRO_ANALYST));
		PLAYBOOK_ROLES.put("BUG_FIX", List.of(
				PdlcRole.RESEARCHER, PdlcRole.IMPLEMENTER, PdlcRole.REVIEWER, PdlcRole.TESTER));
		PLAYBOOK_ROLES.put("SMALL_FEATURE", List.of(
				PdlcRole.PM, PdlcRole.IMPLEMENTER, PdlcRole.REVIEWER, PdlcRole.TESTER));
		PLAYBOOK_ROLES.put("REFACTOR", List.of(
				PdlcRole.RESEARCHER, PdlcRole.ARCHITECT, PdlcRole.IMPLEMENTER,
				PdlcRole.REVIEWER, PdlcRole.TESTER));
		PLAYBOOK_ROLES.put("INFRA_CHANGE", List.of(
				PdlcRole.ARCHITECT, PdlcRole.DEVOPS, PdlcRole.SECURITY_ANALYST));
		PLAYBOOK_ROLES.put("DOCS_ONLY", List.of(
				PdlcRole.RESEARCHER, PdlcRole.TECHNICAL_WRITER));
		PLAYBOOK_ROLES.put("UI_CHANGE", List.of(
				PdlcRole.DESIGNER, PdlcRole.IMPLEMENTER, PdlcRole.REVIEWER, PdlcRole.TESTER));
		PLAYBOOK_ROLES.put("SECURITY_PATCH", List.of(
				PdlcRole.SECURITY_ANALYST, PdlcRole.RESEARCHER, PdlcRole.IMPLEMENTER,
				PdlcRole.TESTER, PdlcRole.DEVOPS));
	}

	/**
	 * Keyword patterns mapped to playbook names for heuristic fallback.
	 * Used when the LLM classifier fails or returns SIMPLE for code/devops sparks.
	 */
	/**
	 * Keyword patterns ordered from most specific to most general — first match wins.
	 * Uses LinkedHashMap to guarantee iteration order.
	 */
	private static final Map<String, Pattern> PLAYBOOK_KEYWORD_PATTERNS = new LinkedHashMap<>();

	static {
		// Most specific first — order matters because first-match wins in heuristicFallback
		PLAYBOOK_KEYWORD_PATTERNS.put("SECURITY_PATCH",
				Pattern.compile("\\b(security|vulnerab|cve|patch|auth\\s*bypass|xss|injection|owasp)\\b",
						Pattern.CASE_INSENSITIVE));
		PLAYBOOK_KEYWORD_PATTERNS.put("INFRA_CHANGE",
				Pattern.compile("\\b(infra|deploy|ci|cd|pipeline|docker|cloud\\s*build|terraform|k8s|kubernetes)\\b",
						Pattern.CASE_INSENSITIVE));
		PLAYBOOK_KEYWORD_PATTERNS.put("DOCS_ONLY",
				Pattern.compile("\\b(docs?|readme|comment|javadoc|changelog)\\b",
						Pattern.CASE_INSENSITIVE));
		PLAYBOOK_KEYWORD_PATTERNS.put("UI_CHANGE",
				Pattern.compile("\\b(ui|css|layout|design|frontend|component|screen|widget)\\b",
						Pattern.CASE_INSENSITIVE));
		PLAYBOOK_KEYWORD_PATTERNS.put("REFACTOR",
				Pattern.compile("\\b(clean\\s*up|refactor|restructur|dead\\s*code|rename|reorganiz|consolidat|simplif)\\b",
						Pattern.CASE_INSENSITIVE));
		// Most general last — "fix" and "bug" are broad catch-alls
		PLAYBOOK_KEYWORD_PATTERNS.put("BUG_FIX",
				Pattern.compile("\\b(fix|bug|error|broken|crash|npe|null\\s*pointer|exception|fail|regression)\\b",
						Pattern.CASE_INSENSITIVE));
	}

	private final AiEngineRouterService aiEngineRouterService;

	public PdlcClassifierService(AiEngineRouterService aiEngineRouterService) {
		this.aiEngineRouterService = aiEngineRouterService;
	}

	/**
	 * Classify the pipeline depth for a spark.
	 *
	 * @param title       the spark title
	 * @param description the spark description (may be null)
	 * @param sparkType   the Stage 1 spark type (code, social, research, devops, creative, data)
	 * @return the PDLC classification; always returns {@link PdlcClassification#simple()} for
	 *         non-eligible spark types or on engine failure
	 */
	public PdlcClassification classifyDepth(String title, String description, String sparkType) {
		if (!ELIGIBLE_SPARK_TYPES.contains(sparkType)) {
			log.debug("[PDLC-CLASSIFIER] Skipping depth classification for spark type '{}' (not code/devops)", sparkType);
			return PdlcClassification.simple();
		}

		try {
			AiEngineRequest request = new AiEngineRequest();
			request.setPrompt(buildClassificationPrompt(title, description));

			AiEngineResult result = aiEngineRouterService.executeStep(
					AiSdlcStep.SPARK_CLASSIFICATION.name(), request);

			if (!result.isSuccess()) {
				log.warn("[PDLC-CLASSIFIER] Engine returned error: {}, using heuristic fallback", result.getError());
				return heuristicFallback(title, description);
			}

			PdlcClassification classification = parseClassification(result.getContent());
			return applyCodeDevopsFloor(classification, title, description);
		}
		catch (Exception e) {
			log.error("[PDLC-CLASSIFIER] Classification failed for title='{}': {}, using heuristic fallback",
					title, e.getMessage(), e);
			return heuristicFallback(title, description);
		}
	}

	/**
	 * For code/devops sparks, enforce a PLAYBOOK minimum tier. Code tasks almost always
	 * require pipeline execution — the simple cloud agent path lacks repo-modification tools.
	 */
	private PdlcClassification applyCodeDevopsFloor(PdlcClassification classification,
			String title, String description) {
		if (classification.tier() != PipelineTier.SIMPLE) {
			return classification;
		}

		log.info("[PDLC-CLASSIFIER] Escalating SIMPLE → PLAYBOOK for code/devops spark title='{}'", title);
		return heuristicFallback(title, description);
	}

	/**
	 * Keyword-based heuristic to select a playbook when the LLM classifier fails or
	 * returns SIMPLE for a code/devops spark. Matches title + description against known
	 * keyword patterns; defaults to SMALL_FEATURE if no pattern matches.
	 */
	PdlcClassification heuristicFallback(String title, String description) {
		String combined = (title != null ? title : "") + " " + (description != null ? description : "");
		String matchedPlaybook = null;

		for (Map.Entry<String, Pattern> entry : PLAYBOOK_KEYWORD_PATTERNS.entrySet()) {
			if (entry.getValue().matcher(combined).find()) {
				matchedPlaybook = entry.getKey();
				break;
			}
		}

		if (matchedPlaybook == null) {
			matchedPlaybook = "SMALL_FEATURE";
		}

		List<PdlcRole> activatedRoles = resolveActivatedRoles(matchedPlaybook, PipelineTier.PLAYBOOK);
		List<PdlcRole> skippedRoles = resolveSkippedRoles(activatedRoles);

		log.info("[PDLC-CLASSIFIER] Heuristic fallback: playbook='{}' for title='{}'", matchedPlaybook, title);

		return new PdlcClassification(
				PipelineTier.PLAYBOOK,
				matchedPlaybook,
				0.6,
				activatedRoles,
				skippedRoles,
				Map.of(),
				"Heuristic fallback: matched playbook " + matchedPlaybook + " from keywords");
	}

	// --- Prompt ---

	private String buildClassificationPrompt(String title, String description) {
		return "You are a PDLC depth classifier. Analyze the following development task and classify "
				+ "it into one of three pipeline tiers: SIMPLE, PLAYBOOK, or FULL_PDLC.\n\n"
				+ "Use this multi-dimensional rubric (score each dimension 1-5):\n"
				+ "  - scope (25%): components/files/systems affected. 1=single file, 5=cross-service system\n"
				+ "  - risk (20%): greenfield vs modification, data model changes. 1=read-only, 5=schema migration\n"
				+ "  - domainBreadth (15%): 1=backend-only or frontend-only, 5=full-stack with mobile/infra\n"
				+ "  - integrationSurface (15%): external APIs, auth, databases. 1=none, 5=multiple external APIs+auth\n"
				+ "  - testingComplexity (15%): 1=unit tests sufficient, 5=E2E/integration/load testing needed\n"
				+ "  - reversibility (10%): 1=trivial rollback, 5=irreversible (migrations, schema, data transforms)\n\n"
				+ "Playbook selection:\n"
				+ "  SIMPLE     — weighted score < 2.0 (trivial task, single-agent execution)\n"
				+ "  PLAYBOOK   — weighted score 2.0–3.5; choose the closest named playbook:\n"
				+ "               BUG_FIX, SMALL_FEATURE, REFACTOR, INFRA_CHANGE, DOCS_ONLY, UI_CHANGE, SECURITY_PATCH\n"
				+ "  FULL_PDLC  — weighted score > 3.5 (major feature or new system requiring all 12 roles)\n\n"
				+ "Respond with ONLY a JSON object — no markdown, no explanation outside the JSON:\n"
				+ "{\n"
				+ "  \"tier\": \"FULL_PDLC\",\n"
				+ "  \"playbook\": \"FULL_PDLC\",\n"
				+ "  \"confidence\": 0.91,\n"
				+ "  \"dimensions\": {\"scope\": 5, \"risk\": 4, \"domainBreadth\": 3, "
				+ "\"integrationSurface\": 4, \"testingComplexity\": 4, \"reversibility\": 3},\n"
				+ "  \"reasoning\": \"Explain why in 1-3 sentences.\"\n"
				+ "}\n\n"
				+ "Task title: " + title + "\n"
				+ "Task description: " + (description != null ? description : "") + "\n\n"
				+ "JSON:";
	}

	// --- JSON Parsing ---

	private PdlcClassification parseClassification(String response) {
		try {
			String json = extractJson(response);
			JsonNode root = MAPPER.readTree(json);

			PipelineTier tier = parseTier(root);
			String playbook = parsePlaybook(root, tier);
			double confidence = parseConfidence(root);
			Map<String, Integer> dimensionScores = parseDimensions(root);
			String reasoning = parseReasoning(root);

			List<PdlcRole> activatedRoles = resolveActivatedRoles(playbook, tier);
			List<PdlcRole> skippedRoles = resolveSkippedRoles(activatedRoles);

			log.debug("[PDLC-CLASSIFIER] tier={} playbook={} confidence={} reasoning={}",
					tier, playbook, confidence, reasoning);

			return new PdlcClassification(tier, playbook, confidence, activatedRoles, skippedRoles,
					dimensionScores, reasoning);
		}
		catch (Exception e) {
			log.warn("[PDLC-CLASSIFIER] Failed to parse classification response: '{}' — {}",
					response, e.getMessage());
			return PdlcClassification.simple();
		}
	}

	/** Extracts the JSON object from the response, tolerating surrounding prose. */
	private String extractJson(String response) {
		String trimmed = response.trim();
		int start = trimmed.indexOf('{');
		int end = trimmed.lastIndexOf('}');
		if (start >= 0 && end > start) {
			return trimmed.substring(start, end + 1);
		}
		return trimmed;
	}

	private PipelineTier parseTier(JsonNode root) {
		if (!root.has("tier")) {
			return PipelineTier.SIMPLE;
		}
		String tierStr = root.get("tier").asText("SIMPLE").trim().toUpperCase();
		try {
			return PipelineTier.valueOf(tierStr);
		}
		catch (IllegalArgumentException e) {
			log.warn("[PDLC-CLASSIFIER] Unknown tier '{}', defaulting to SIMPLE", tierStr);
			return PipelineTier.SIMPLE;
		}
	}

	private String parsePlaybook(JsonNode root, PipelineTier tier) {
		if (tier == PipelineTier.SIMPLE) {
			return null;
		}
		if (!root.has("playbook") || root.get("playbook").isNull()) {
			// Default: FULL_PDLC tier → "FULL_PDLC" playbook; PLAYBOOK tier → null (caller must handle)
			return tier == PipelineTier.FULL_PDLC ? "FULL_PDLC" : null;
		}
		String pb = root.get("playbook").asText("").trim().toUpperCase();
		if (pb.isEmpty() || pb.equals("NULL")) {
			return null;
		}
		return pb;
	}

	private double parseConfidence(JsonNode root) {
		if (!root.has("confidence")) {
			return 0.5;
		}
		double raw = root.get("confidence").asDouble(0.5);
		// Clamp to [0.0, 1.0]
		return Math.max(0.0, Math.min(1.0, raw));
	}

	private Map<String, Integer> parseDimensions(JsonNode root) {
		Map<String, Integer> scores = new HashMap<>();
		JsonNode dims = root.path("dimensions");
		if (dims.isMissingNode() || dims.isNull()) {
			return scores;
		}
		for (Map.Entry<String, JsonNode> entry : dims.properties()) {
			scores.put(entry.getKey(), entry.getValue().asInt(0));
		}
		return scores;
	}

	private String parseReasoning(JsonNode root) {
		if (!root.has("reasoning")) {
			return "";
		}
		return root.get("reasoning").asText("");
	}

	// --- Role Resolution ---

	private List<PdlcRole> resolveActivatedRoles(String playbook, PipelineTier tier) {
		if (tier == PipelineTier.SIMPLE || playbook == null) {
			return List.of();
		}
		List<PdlcRole> roles = PLAYBOOK_ROLES.get(playbook);
		if (roles == null) {
			log.warn("[PDLC-CLASSIFIER] Unknown playbook '{}' — no roles mapped, defaulting to empty", playbook);
			return List.of();
		}
		return roles;
	}

	private List<PdlcRole> resolveSkippedRoles(List<PdlcRole> activatedRoles) {
		List<PdlcRole> all = new ArrayList<>(Arrays.asList(PdlcRole.values()));
		all.removeAll(activatedRoles);
		return all;
	}

}
