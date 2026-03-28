package io.strategiz.social.business.agent.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.cidadel.business.ai.engine.AiEngineRouterService;
import io.cidadel.business.ai.engine.AiEngineUnavailableException;
import io.cidadel.framework.ai.engine.model.AiEngineRequest;
import io.cidadel.framework.ai.engine.model.AiEngineResult;
import io.strategiz.social.data.entity.AiSdlcStep;
import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineTier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PdlcClassifierServiceTest {

	@Mock
	private AiEngineRouterService aiEngineRouterService;

	@InjectMocks
	private PdlcClassifierService pdlcClassifierService;

	// --- Eligible type: code spark classified as FULL_PDLC ---

	@Test
	void classifyDepth_codeSparkWithComplexDescription_returnsFullPdlc() {
		String json = """
				{
				  "tier": "FULL_PDLC",
				  "playbook": "FULL_PDLC",
				  "confidence": 0.91,
				  "dimensions": {"scope": 5, "risk": 4, "domainBreadth": 4, "integrationSurface": 4, "testingComplexity": 4, "reversibility": 3},
				  "reasoning": "New notification system touching WebSocket, backend, and mobile layers."
				}
				""";
		AiEngineResult result = AiEngineResult.success(json, "api-anthropic", "claude-haiku-4-5");
		when(aiEngineRouterService.executeStep(
				eq(AiSdlcStep.SPARK_CLASSIFICATION.name()), any(AiEngineRequest.class)))
				.thenReturn(result);

		PdlcClassification classification = pdlcClassifierService.classifyDepth(
				"Build notification system",
				"Add real-time WebSocket notifications with push support across backend, API, and mobile app",
				"code");

		assertEquals(PipelineTier.FULL_PDLC, classification.tier());
		assertEquals("FULL_PDLC", classification.playbook());
		assertEquals(0.91, classification.confidence(), 0.001);
		assertFalse(classification.activatedRoles().isEmpty());
		assertTrue(classification.activatedRoles().contains(PdlcRole.PM));
		assertTrue(classification.activatedRoles().contains(PdlcRole.IMPLEMENTER));
		assertTrue(classification.activatedRoles().contains(PdlcRole.RETRO_ANALYST));
		assertEquals(12, classification.activatedRoles().size());
		assertTrue(classification.skippedRoles().isEmpty());
		assertFalse(classification.dimensionScores().isEmpty());
		assertEquals(5, classification.dimensionScores().get("scope"));
		assertNotNull(classification.reasoning());
		assertTrue(classification.isAutoRoute());
		assertFalse(classification.needsConfirmation());
		assertFalse(classification.needsUserInput());
	}

	// --- Eligible type: devops spark classified as playbook ---

	@Test
	void classifyDepth_devopsSparkWithModerateComplexity_returnsPlaybook() {
		String json = """
				{
				  "tier": "PLAYBOOK",
				  "playbook": "INFRA_CHANGE",
				  "confidence": 0.78,
				  "dimensions": {"scope": 2, "risk": 3, "domainBreadth": 1, "integrationSurface": 2, "testingComplexity": 2, "reversibility": 3},
				  "reasoning": "CI/CD config update with moderate infrastructure risk."
				}
				""";
		AiEngineResult result = AiEngineResult.success(json, "api-anthropic", "claude-haiku-4-5");
		when(aiEngineRouterService.executeStep(
				eq(AiSdlcStep.SPARK_CLASSIFICATION.name()), any(AiEngineRequest.class)))
				.thenReturn(result);

		PdlcClassification classification = pdlcClassifierService.classifyDepth(
				"Update Cloud Build config",
				"Add staging deployment step to CI/CD pipeline",
				"devops");

		assertEquals(PipelineTier.PLAYBOOK, classification.tier());
		assertEquals("INFRA_CHANGE", classification.playbook());
		assertEquals(0.78, classification.confidence(), 0.001);
		assertTrue(classification.activatedRoles().contains(PdlcRole.DEVOPS));
		assertTrue(classification.activatedRoles().contains(PdlcRole.SECURITY_ANALYST));
		assertTrue(classification.needsConfirmation());
		assertFalse(classification.isAutoRoute());
		assertFalse(classification.needsUserInput());
	}

	// --- Ineligible type: social spark skips classification ---

	@Test
	void classifyDepth_socialSpark_returnsSimpleWithoutCallingEngine() {
		PdlcClassification classification = pdlcClassifierService.classifyDepth(
				"Post to Twitter",
				"Share a product update on Twitter",
				"social");

		assertEquals(PipelineTier.SIMPLE, classification.tier());
		assertNull(classification.playbook());
		assertEquals(0.0, classification.confidence(), 0.001);
		assertTrue(classification.activatedRoles().isEmpty());
		assertTrue(classification.skippedRoles().isEmpty());
	}

	@Test
	void classifyDepth_researchSpark_returnsSimpleWithoutCallingEngine() {
		PdlcClassification classification = pdlcClassifierService.classifyDepth(
				"Research competitors",
				"Find pricing info for top 5 competitors",
				"research");

		assertEquals(PipelineTier.SIMPLE, classification.tier());
		assertNull(classification.playbook());
	}

	// --- Engine failure uses heuristic fallback (not SIMPLE) for code/devops ---

	@Test
	void classifyDepth_engineReturnsError_usesHeuristicFallback() {
		AiEngineResult error = AiEngineResult.error("rate limit exceeded", "api-anthropic");
		when(aiEngineRouterService.executeStep(
				eq(AiSdlcStep.SPARK_CLASSIFICATION.name()), any(AiEngineRequest.class)))
				.thenReturn(error);

		PdlcClassification classification = pdlcClassifierService.classifyDepth(
				"Fix login bug",
				"Users are getting 500 error on login",
				"code");

		// Should use heuristic fallback → BUG_FIX (matches "fix" and "bug")
		assertEquals(PipelineTier.PLAYBOOK, classification.tier());
		assertEquals("BUG_FIX", classification.playbook());
		assertEquals(0.6, classification.confidence(), 0.001);
		assertFalse(classification.activatedRoles().isEmpty());
	}

	@Test
	void classifyDepth_engineThrowsException_usesHeuristicFallback() {
		when(aiEngineRouterService.executeStep(
				eq(AiSdlcStep.SPARK_CLASSIFICATION.name()), any(AiEngineRequest.class)))
				.thenThrow(new AiEngineUnavailableException("SPARK_CLASSIFICATION",
						"No engine configured for step"));

		PdlcClassification classification = pdlcClassifierService.classifyDepth(
				"Add feature X",
				"Implement feature X in the backend",
				"code");

		// No specific keyword match → defaults to SMALL_FEATURE
		assertEquals(PipelineTier.PLAYBOOK, classification.tier());
		assertEquals("SMALL_FEATURE", classification.playbook());
		assertEquals(0.6, classification.confidence(), 0.001);
	}

	// --- Malformed JSON uses heuristic fallback ---

	@Test
	void classifyDepth_malformedJson_usesHeuristicFallback() {
		AiEngineResult result = AiEngineResult.success(
				"I think this is a full pipeline task, definitely use all roles",
				"api-anthropic", "claude-haiku-4-5");
		when(aiEngineRouterService.executeStep(
				eq(AiSdlcStep.SPARK_CLASSIFICATION.name()), any(AiEngineRequest.class)))
				.thenReturn(result);

		PdlcClassification classification = pdlcClassifierService.classifyDepth(
				"Big refactor",
				"Refactor everything",
				"code");

		// "refactor" keyword → REFACTOR playbook
		assertEquals(PipelineTier.PLAYBOOK, classification.tier());
		assertEquals("REFACTOR", classification.playbook());
		assertEquals(0.6, classification.confidence(), 0.001);
	}

	@Test
	void classifyDepth_emptyResponse_usesHeuristicFallback() {
		AiEngineResult result = AiEngineResult.success("", "api-anthropic", "claude-haiku-4-5");
		when(aiEngineRouterService.executeStep(
				eq(AiSdlcStep.SPARK_CLASSIFICATION.name()), any(AiEngineRequest.class)))
				.thenReturn(result);

		PdlcClassification classification = pdlcClassifierService.classifyDepth(
				"Something",
				"A task",
				"code");

		// No keyword match → SMALL_FEATURE default
		assertEquals(PipelineTier.PLAYBOOK, classification.tier());
		assertEquals("SMALL_FEATURE", classification.playbook());
	}

	// --- SIMPLE floor escalation for code/devops ---

	@Test
	void classifyDepth_llmReturnsSimpleForCodeSpark_escalatesToPlaybook() {
		// LLM thinks it's simple, but code sparks should never be SIMPLE
		String json = """
				{
				  "tier": "SIMPLE",
				  "playbook": null,
				  "confidence": 0.95,
				  "dimensions": {"scope": 1, "risk": 1, "domainBreadth": 1, "integrationSurface": 1, "testingComplexity": 1, "reversibility": 1},
				  "reasoning": "Trivial one-liner change."
				}
				""";
		AiEngineResult result = AiEngineResult.success(json, "api-anthropic", "claude-haiku-4-5");
		when(aiEngineRouterService.executeStep(any(), any(AiEngineRequest.class))).thenReturn(result);

		PdlcClassification classification = pdlcClassifierService.classifyDepth(
				"Clean up dead code in strategiz-core",
				"Remove unused imports and dead methods",
				"code");

		// Should be escalated from SIMPLE → PLAYBOOK
		assertEquals(PipelineTier.PLAYBOOK, classification.tier());
		// "clean up" matches REFACTOR pattern
		assertEquals("REFACTOR", classification.playbook());
		assertFalse(classification.activatedRoles().isEmpty());
	}

	@Test
	void classifyDepth_llmReturnsSimpleForDevopsSpark_escalatesToPlaybook() {
		String json = "{\"tier\":\"SIMPLE\",\"playbook\":null,\"confidence\":0.90,\"dimensions\":{},\"reasoning\":\"Simple config change.\"}";
		AiEngineResult result = AiEngineResult.success(json, "api-anthropic", "claude-haiku-4-5");
		when(aiEngineRouterService.executeStep(any(), any(AiEngineRequest.class))).thenReturn(result);

		PdlcClassification classification = pdlcClassifierService.classifyDepth(
				"Update Docker config",
				"Change base image to Java 25",
				"devops");

		assertEquals(PipelineTier.PLAYBOOK, classification.tier());
		// "docker" matches INFRA_CHANGE
		assertEquals("INFRA_CHANGE", classification.playbook());
	}

	// --- Heuristic keyword matching ---

	@Test
	void heuristicFallback_bugFixKeywords_returnsBugFix() {
		PdlcClassification classification = pdlcClassifierService.heuristicFallback(
				"Fix NPE in UserService", "NullPointerException when calling getById");

		assertEquals(PipelineTier.PLAYBOOK, classification.tier());
		assertEquals("BUG_FIX", classification.playbook());
	}

	@Test
	void heuristicFallback_refactorKeywords_returnsRefactor() {
		PdlcClassification classification = pdlcClassifierService.heuristicFallback(
				"Clean up dead code", "Remove unused methods from the auth module");

		assertEquals(PipelineTier.PLAYBOOK, classification.tier());
		assertEquals("REFACTOR", classification.playbook());
	}

	@Test
	void heuristicFallback_infraKeywords_returnsInfraChange() {
		PdlcClassification classification = pdlcClassifierService.heuristicFallback(
				"Update CI pipeline", "Add a deploy step to Cloud Build");

		assertEquals(PipelineTier.PLAYBOOK, classification.tier());
		assertEquals("INFRA_CHANGE", classification.playbook());
	}

	@Test
	void heuristicFallback_securityKeywords_returnsSecurityPatch() {
		PdlcClassification classification = pdlcClassifierService.heuristicFallback(
				"Patch security vulnerability", "CVE-2026-1234 affects auth module");

		assertEquals(PipelineTier.PLAYBOOK, classification.tier());
		assertEquals("SECURITY_PATCH", classification.playbook());
	}

	@Test
	void heuristicFallback_noKeywordsMatch_returnsSmallFeature() {
		PdlcClassification classification = pdlcClassifierService.heuristicFallback(
				"Add user profile endpoint", "Create GET /api/users/me");

		assertEquals(PipelineTier.PLAYBOOK, classification.tier());
		assertEquals("SMALL_FEATURE", classification.playbook());
	}

	// --- Confidence gating thresholds ---

	@Test
	void confidenceGating_aboveThreshold_isAutoRoute() {
		String json = "{\"tier\":\"FULL_PDLC\",\"playbook\":\"FULL_PDLC\",\"confidence\":0.86,\"dimensions\":{},\"reasoning\":\"High confidence.\"}";
		AiEngineResult result = AiEngineResult.success(json, "api-anthropic", "claude-haiku-4-5");
		when(aiEngineRouterService.executeStep(any(), any(AiEngineRequest.class))).thenReturn(result);

		PdlcClassification classification = pdlcClassifierService.classifyDepth("Task", "Desc", "code");

		assertTrue(classification.isAutoRoute());
		assertFalse(classification.needsConfirmation());
		assertFalse(classification.needsUserInput());
	}

	@Test
	void confidenceGating_midRange_needsConfirmation() {
		String json = "{\"tier\":\"PLAYBOOK\",\"playbook\":\"BUG_FIX\",\"confidence\":0.70,\"dimensions\":{},\"reasoning\":\"Moderate confidence.\"}";
		AiEngineResult result = AiEngineResult.success(json, "api-anthropic", "claude-haiku-4-5");
		when(aiEngineRouterService.executeStep(any(), any(AiEngineRequest.class))).thenReturn(result);

		PdlcClassification classification = pdlcClassifierService.classifyDepth("Fix bug", "Desc", "code");

		assertFalse(classification.isAutoRoute());
		assertTrue(classification.needsConfirmation());
		assertFalse(classification.needsUserInput());
	}

	@Test
	void confidenceGating_belowThreshold_needsUserInput() {
		String json = "{\"tier\":\"PLAYBOOK\",\"playbook\":\"SMALL_FEATURE\",\"confidence\":0.30,\"dimensions\":{},\"reasoning\":\"Low confidence.\"}";
		AiEngineResult result = AiEngineResult.success(json, "api-anthropic", "claude-haiku-4-5");
		when(aiEngineRouterService.executeStep(any(), any(AiEngineRequest.class))).thenReturn(result);

		PdlcClassification classification = pdlcClassifierService.classifyDepth("Add thing", "Desc", "code");

		assertFalse(classification.isAutoRoute());
		assertFalse(classification.needsConfirmation());
		assertTrue(classification.needsUserInput());
	}

	@Test
	void confidenceGating_atLowerBoundary_needsConfirmation() {
		String json = "{\"tier\":\"PLAYBOOK\",\"playbook\":\"REFACTOR\",\"confidence\":0.50,\"dimensions\":{},\"reasoning\":\"Boundary case.\"}";
		AiEngineResult result = AiEngineResult.success(json, "api-anthropic", "claude-haiku-4-5");
		when(aiEngineRouterService.executeStep(any(), any(AiEngineRequest.class))).thenReturn(result);

		PdlcClassification classification = pdlcClassifierService.classifyDepth("Refactor", "Desc", "code");

		assertTrue(classification.needsConfirmation());
		assertFalse(classification.needsUserInput());
	}

	@Test
	void confidenceGating_atUpperBoundary_needsConfirmation() {
		String json = "{\"tier\":\"FULL_PDLC\",\"playbook\":\"FULL_PDLC\",\"confidence\":0.85,\"dimensions\":{},\"reasoning\":\"Upper boundary.\"}";
		AiEngineResult result = AiEngineResult.success(json, "api-anthropic", "claude-haiku-4-5");
		when(aiEngineRouterService.executeStep(any(), any(AiEngineRequest.class))).thenReturn(result);

		PdlcClassification classification = pdlcClassifierService.classifyDepth("Big task", "Desc", "code");

		assertTrue(classification.needsConfirmation());
		assertFalse(classification.isAutoRoute());
	}

	// --- Skipped roles are the complement of activated roles ---

	@Test
	void classifyDepth_bugFixPlaybook_skippedRolesAreComplement() {
		String json = "{\"tier\":\"PLAYBOOK\",\"playbook\":\"BUG_FIX\",\"confidence\":0.88,\"dimensions\":{},\"reasoning\":\"Bug fix.\"}";
		AiEngineResult result = AiEngineResult.success(json, "api-anthropic", "claude-haiku-4-5");
		when(aiEngineRouterService.executeStep(any(), any(AiEngineRequest.class))).thenReturn(result);

		PdlcClassification classification = pdlcClassifierService.classifyDepth("Fix crash", "NPE on startup", "code");

		assertEquals(PipelineTier.PLAYBOOK, classification.tier());
		assertEquals("BUG_FIX", classification.playbook());
		// BUG_FIX: RESEARCHER, IMPLEMENTER, REVIEWER, TESTER → 4 roles
		assertEquals(4, classification.activatedRoles().size());
		// Skipped = all 12 - 4 = 8
		assertEquals(8, classification.skippedRoles().size());
		// Verify no overlap
		for (PdlcRole role : classification.activatedRoles()) {
			assertFalse(classification.skippedRoles().contains(role),
					"Role " + role + " should not appear in both activated and skipped");
		}
	}

	// --- JSON tolerates surrounding prose (PLAYBOOK+ results pass through) ---

	@Test
	void classifyDepth_jsonWrappedInProse_playbookResult_passesThrough() {
		String response = "Here is my analysis:\n"
				+ "{\"tier\":\"PLAYBOOK\",\"playbook\":\"BUG_FIX\",\"confidence\":0.95,"
				+ "\"dimensions\":{\"scope\":1},\"reasoning\":\"Targeted fix.\"}\n"
				+ "That is my recommendation.";
		AiEngineResult result = AiEngineResult.success(response, "api-anthropic", "claude-haiku-4-5");
		when(aiEngineRouterService.executeStep(any(), any(AiEngineRequest.class))).thenReturn(result);

		PdlcClassification classification = pdlcClassifierService.classifyDepth("Simple fix", "Typo", "code");

		assertEquals(PipelineTier.PLAYBOOK, classification.tier());
		assertEquals("BUG_FIX", classification.playbook());
		assertEquals(0.95, classification.confidence(), 0.001);
	}

	@Test
	void classifyDepth_jsonWrappedInProse_simpleResult_escalatedToPlaybook() {
		// Even when LLM says SIMPLE wrapped in prose, it gets escalated for code sparks
		String response = "Here is my analysis:\n"
				+ "{\"tier\":\"SIMPLE\",\"playbook\":null,\"confidence\":0.95,"
				+ "\"dimensions\":{\"scope\":1},\"reasoning\":\"Trivial task.\"}\n"
				+ "That is my recommendation.";
		AiEngineResult result = AiEngineResult.success(response, "api-anthropic", "claude-haiku-4-5");
		when(aiEngineRouterService.executeStep(any(), any(AiEngineRequest.class))).thenReturn(result);

		PdlcClassification classification = pdlcClassifierService.classifyDepth("Fix typo", "Typo in readme", "code");

		// Escalated from SIMPLE → PLAYBOOK via heuristic
		// "readme" matches DOCS_ONLY (higher priority than BUG_FIX "fix")
		assertEquals(PipelineTier.PLAYBOOK, classification.tier());
		assertEquals("DOCS_ONLY", classification.playbook());
	}

}
