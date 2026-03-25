package io.strategiz.social.business.agent.pipeline;

import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PdlcRoleKnowledge;
import io.strategiz.social.data.repository.PdlcRoleKnowledgeRepository;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Manages role-specific knowledge for the PDLC pipeline. Supports adding, retrieving, and
 * relevance-scoring knowledge entries to improve role performance over time.
 *
 * <p>Embeddings are placeholders; real vector similarity search can be wired later by replacing
 * the keyword-based scoring in {@link #queryKnowledge}.
 */
@Service
public class KnowledgeBaseService {

	private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

	private static final String CATEGORY_BEST_PRACTICE = "BEST_PRACTICE";

	private static final double MIN_RELEVANCE = 0.0;

	private static final double MAX_RELEVANCE = 10.0;

	private static final int TOP_K_BOOTSTRAP_CHECK = 1;

	private final PdlcRoleKnowledgeRepository knowledgeRepository;

	public KnowledgeBaseService(PdlcRoleKnowledgeRepository knowledgeRepository) {
		this.knowledgeRepository = knowledgeRepository;
	}

	/**
	 * Add a new knowledge entry for a role.
	 *
	 * @param role     The PDLC role this knowledge applies to
	 * @param category Knowledge category (e.g., BEST_PRACTICE, ANTI_PATTERN, RETRO_LEARNING)
	 * @param content  The knowledge content text
	 * @param source   Origin of the knowledge (e.g., "bootstrap", "retro-{pipelineRunId}")
	 * @return The generated knowledge entry ID
	 */
	public String addKnowledge(PdlcRole role, String category, String content, String source) {
		String knowledgeId = UUID.randomUUID().toString();

		PdlcRoleKnowledge entry = new PdlcRoleKnowledge();
		entry.setId(knowledgeId);
		entry.setRole(role);
		entry.setCategory(category);
		entry.setContent(content);
		// TODO: Replace with real OpenAI embedding call when wired
		entry.setEmbedding(List.of());
		entry.setSource(source);
		entry.setRelevanceScore(1.0);
		entry.setCreatedAt(Instant.now());

		knowledgeRepository.save(entry);

		log.debug("Added knowledge entry {} for role {} category {}", knowledgeId, role, category);
		return knowledgeId;
	}

	/**
	 * Query knowledge entries most relevant to the given context.
	 *
	 * <p>Currently uses keyword overlap scoring. TODO: Replace with vector similarity search when
	 * embeddings are wired.
	 *
	 * @param role        The PDLC role to query knowledge for
	 * @param contextText The context text to score against
	 * @param topK        Maximum number of results to return
	 * @return Top-K knowledge entries sorted by combined relevance and keyword score
	 */
	public List<PdlcRoleKnowledge> queryKnowledge(PdlcRole role, String contextText, int topK) {
		List<PdlcRoleKnowledge> all = knowledgeRepository.findByRole(role);
		if (all.isEmpty()) {
			return List.of();
		}

		String[] contextWords = tokenize(contextText);

		return all.stream()
			.sorted(Comparator.comparingDouble(
				(PdlcRoleKnowledge k) -> k.getRelevanceScore() * keywordMatchScore(k.getContent(), contextWords))
				.reversed())
			.limit(topK)
			.toList();
	}

	/**
	 * Adjust the relevance score of a knowledge entry by the given delta.
	 *
	 * @param knowledgeId The ID of the knowledge entry to update
	 * @param delta       Amount to add (positive) or subtract (negative) from the score
	 */
	public void updateRelevance(String knowledgeId, double delta) {
		Optional<PdlcRoleKnowledge> found = knowledgeRepository.findById(knowledgeId);
		if (found.isEmpty()) {
			log.warn("Knowledge entry not found for relevance update: {}", knowledgeId);
			return;
		}

		PdlcRoleKnowledge entry = found.get();
		double updated = Math.max(MIN_RELEVANCE, Math.min(MAX_RELEVANCE, entry.getRelevanceScore() + delta));
		entry.setRelevanceScore(updated);
		knowledgeRepository.save(entry);

		log.debug("Updated relevance for {} by {}: new score {}", knowledgeId, delta, updated);
	}

	/**
	 * Retrieve all knowledge entries for a given role.
	 *
	 * @param role The PDLC role
	 * @return All knowledge entries for the role
	 */
	public List<PdlcRoleKnowledge> getKnowledgeForRole(PdlcRole role) {
		return knowledgeRepository.findByRole(role);
	}

	/**
	 * Bootstrap initial best-practice knowledge entries for all 12 PDLC roles.
	 *
	 * <p>Only runs if no knowledge entries exist (idempotent).
	 */
	@PostConstruct
	public void bootstrapKnowledge() {
		// Check if any knowledge exists to avoid duplicates on restart
		List<PdlcRoleKnowledge> existing = knowledgeRepository.findByRole(PdlcRole.PM);
		if (!existing.isEmpty()) {
			log.debug("Knowledge base already bootstrapped, skipping");
			return;
		}

		log.info("Bootstrapping knowledge base with best-practice entries for all PDLC roles");

		Map<PdlcRole, List<String>> bootstrapEntries = Map.ofEntries(
			Map.entry(PdlcRole.PM, List.of(
				"Always define measurable acceptance criteria with clear pass/fail conditions.",
				"Scope boundaries explicitly — document what is NOT in scope to prevent scope creep.",
				"Identify stakeholder dependencies and external blockers before the sprint begins."
			)),
			Map.entry(PdlcRole.RESEARCHER, List.of(
				"Cite primary sources and verify claims against at least two independent references.",
				"Distinguish between proven facts, industry consensus, and opinions in research outputs.",
				"Focus research on unknowns that materially affect design decisions."
			)),
			Map.entry(PdlcRole.ARCHITECT, List.of(
				"Document architectural decisions (ADRs) with the rationale and rejected alternatives.",
				"Design for failure — assume every external dependency will be unavailable.",
				"Prefer simple, well-understood patterns over novel solutions unless complexity is justified."
			)),
			Map.entry(PdlcRole.DESIGNER, List.of(
				"Validate UX patterns against accessibility standards (WCAG 2.1 AA minimum).",
				"Design mobile-first and test layouts at 320px width before scaling up.",
				"Provide loading, empty, and error states for every interactive component."
			)),
			Map.entry(PdlcRole.PLANNER, List.of(
				"Break tasks to a granularity where a single task can be completed in under a day.",
				"Surface dependencies between tasks explicitly to prevent blocked work.",
				"Include testing and documentation tasks in the plan, not as afterthoughts."
			)),
			Map.entry(PdlcRole.IMPLEMENTER, List.of(
				"Handle error cases for all external API calls — never assume a request will succeed.",
				"Follow existing code patterns in the codebase for consistency and maintainability.",
				"Write self-documenting code; add comments only for non-obvious intent, not mechanics."
			)),
			Map.entry(PdlcRole.REVIEWER, List.of(
				"Check for OWASP top 10 vulnerabilities on every review touching auth or data input.",
				"Verify error handling on all API boundaries and ensure failures are surfaced correctly.",
				"Confirm that new code has adequate test coverage before approving."
			)),
			Map.entry(PdlcRole.TESTER, List.of(
				"Test both happy path and error cases for every feature under review.",
				"Verify edge cases with boundary values — zero, negative, max, and empty inputs.",
				"Regression-test areas of the codebase known to be affected by the change."
			)),
			Map.entry(PdlcRole.SECURITY_ANALYST, List.of(
				"Treat all user-supplied input as untrusted — validate and sanitize at every boundary.",
				"Review authentication and authorization logic for privilege escalation risks.",
				"Check for secrets or credentials hardcoded in source code or configuration."
			)),
			Map.entry(PdlcRole.TECHNICAL_WRITER, List.of(
				"Write documentation from the reader's perspective — what do they need to accomplish?",
				"Include working code examples for every public API or integration point.",
				"Keep docs versioned alongside the code they describe to prevent drift."
			)),
			Map.entry(PdlcRole.DEVOPS, List.of(
				"Ensure rollback procedures are documented and tested before deploying to production.",
				"Alert on meaningful signals (latency, error rate, saturation) — not just uptime.",
				"Infrastructure changes should be code-reviewed with the same rigor as application code."
			)),
			Map.entry(PdlcRole.RETRO_ANALYST, List.of(
				"Focus retrospective analysis on systemic issues, not individual blame.",
				"Produce actionable recommendations with clear owners and deadlines.",
				"Track whether previous retro action items were resolved before opening new ones."
			))
		);

		for (Map.Entry<PdlcRole, List<String>> entry : bootstrapEntries.entrySet()) {
			PdlcRole role = entry.getKey();
			for (String knowledge : entry.getValue()) {
				addKnowledge(role, CATEGORY_BEST_PRACTICE, knowledge, "bootstrap");
			}
		}

		log.info("Knowledge base bootstrap complete");
	}

	/**
	 * Build a formatted knowledge context block for injection into a role's system prompt.
	 *
	 * @param role         The PDLC role
	 * @param sparkContext The spark context text used to score relevance
	 * @return Formatted knowledge block, or empty string if no knowledge exists
	 */
	public String buildKnowledgeContext(PdlcRole role, String sparkContext) {
		List<PdlcRoleKnowledge> topKnowledge = queryKnowledge(role, sparkContext, 5);
		if (topKnowledge.isEmpty()) {
			return "";
		}

		StringBuilder sb = new StringBuilder("## Role Knowledge\n\n");
		for (PdlcRoleKnowledge knowledge : topKnowledge) {
			sb.append("- ").append(knowledge.getContent()).append("\n");
		}

		return sb.toString().stripTrailing();
	}

	// --- Internal helpers ---

	private String[] tokenize(String text) {
		if (text == null || text.isBlank()) {
			return new String[0];
		}
		return Arrays.stream(text.toLowerCase().split("[\\W_]+"))
			.filter(w -> w.length() > 2)
			.toArray(String[]::new);
	}

	private double keywordMatchScore(String content, String[] contextWords) {
		if (contextWords.length == 0 || content == null || content.isBlank()) {
			// No context: treat all entries as equally relevant
			return 1.0;
		}
		String lowerContent = content.toLowerCase();
		long matches = Arrays.stream(contextWords).filter(lowerContent::contains).count();
		return (double) matches / contextWords.length;
	}

}
