package io.strategiz.social.business.agent.pipeline.role;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.cidadel.business.ai.engine.AiEngineRouterService;
import io.cidadel.framework.ai.engine.model.AiEngineRequest;
import io.cidadel.framework.ai.engine.model.AiEngineResult;
import io.strategiz.social.data.entity.AiSdlcStep;
import io.strategiz.social.data.entity.PdlcRole;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReviewerRoleSkillTest {

	@Mock
	private AiEngineRouterService engineRouterService;

	// --- role config tests ---

	@Test
	void getRole_returnsReviewer() {
		ReviewerRoleSkill skill = new ReviewerRoleSkill(engineRouterService);

		assertEquals(PdlcRole.REVIEWER, skill.getRole());
	}

	@Test
	void getAiSdlcStepName_returnsCodeReview() {
		ReviewerRoleSkill skill = new ReviewerRoleSkill(engineRouterService);

		assertEquals(AiSdlcStep.CODE_REVIEW.name(), skill.getAiSdlcStepName());
	}

	@Test
	void getAvailableTools_containsReviewTools() {
		ReviewerRoleSkill skill = new ReviewerRoleSkill(engineRouterService);

		assertTrue(skill.getAvailableTools().contains("github_read_file"));
		assertTrue(skill.getAvailableTools().contains("github_create_pr"));
		assertTrue(skill.getAvailableTools().contains("github_review_pr"));
	}

	// --- execution tests ---

	@Test
	void execute_approvedReview_returnsCompleted() {
		ReviewerRoleSkill skill = new ReviewerRoleSkill(engineRouterService);

		String reviewContent = """
				## Review Summary
				Code looks good overall.
				## Findings
				1. (minor) Consider adding logging to the error path
				## Decision
				APPROVED
				""";
		AiEngineResult engineResult = AiEngineResult.success(reviewContent, "api-anthropic", "claude-sonnet-4");
		engineResult.setTotalTokens(2000);
		when(engineRouterService.executeStep(eq(AiSdlcStep.CODE_REVIEW.name()), any(AiEngineRequest.class)))
				.thenReturn(engineResult);

		RoleContext ctx = createContext();
		RoleResult result = skill.execute(ctx);

		assertEquals(RoleOutcome.COMPLETED, result.outcome());
		assertNotNull(result.summary());
		assertNull(result.rejectionReason());
		assertNull(result.reworkTarget());
	}

	@Test
	void execute_rejectedReview_returnsRejectedWithImplementerTarget() {
		ReviewerRoleSkill skill = new ReviewerRoleSkill(engineRouterService);

		String reviewContent = """
				## Review Summary
				Several critical issues found.
				## Findings
				1. (critical) No error handling in UserService.createUser()
				2. (critical) SQL injection risk in query builder
				## Decision
				REJECTED: Critical security and error handling issues must be addressed.
				## Rework Instructions
				- Add try/catch with proper exception types in UserService.createUser()
				- Use parameterized queries in the query builder
				""";
		AiEngineResult engineResult = AiEngineResult.success(reviewContent, "api-anthropic", "claude-sonnet-4");
		engineResult.setTotalTokens(2500);
		when(engineRouterService.executeStep(eq(AiSdlcStep.CODE_REVIEW.name()), any(AiEngineRequest.class)))
				.thenReturn(engineResult);

		RoleContext ctx = createContext();
		RoleResult result = skill.execute(ctx);

		assertEquals(RoleOutcome.REJECTED, result.outcome());
		assertEquals(PdlcRole.IMPLEMENTER, result.reworkTarget());
		assertNotNull(result.rejectionReason());
		assertTrue(result.rejectionReason().contains("REJECTED"));
	}

	@Test
	void execute_engineError_returnsFailed() {
		ReviewerRoleSkill skill = new ReviewerRoleSkill(engineRouterService);

		AiEngineResult engineResult = AiEngineResult.error("model overloaded", "api-anthropic");
		when(engineRouterService.executeStep(eq(AiSdlcStep.CODE_REVIEW.name()), any(AiEngineRequest.class)))
				.thenReturn(engineResult);

		RoleContext ctx = createContext();
		RoleResult result = skill.execute(ctx);

		assertEquals(RoleOutcome.FAILED, result.outcome());
	}

	@Test
	void execute_engineThrows_returnsFailed() {
		ReviewerRoleSkill skill = new ReviewerRoleSkill(engineRouterService);

		when(engineRouterService.executeStep(eq(AiSdlcStep.CODE_REVIEW.name()), any(AiEngineRequest.class)))
				.thenThrow(new RuntimeException("connection reset"));

		RoleContext ctx = createContext();
		RoleResult result = skill.execute(ctx);

		assertEquals(RoleOutcome.FAILED, result.outcome());
	}

	@Test
	void execute_metricsPopulated() {
		ReviewerRoleSkill skill = new ReviewerRoleSkill(engineRouterService);

		AiEngineResult engineResult = AiEngineResult.success("APPROVED", "api-anthropic", "claude-sonnet-4");
		engineResult.setTotalTokens(1000);
		when(engineRouterService.executeStep(eq(AiSdlcStep.CODE_REVIEW.name()), any(AiEngineRequest.class)))
				.thenReturn(engineResult);

		RoleContext ctx = createContext();
		RoleResult result = skill.execute(ctx);

		assertNotNull(result.metrics());
		assertEquals(1000, result.metrics().tokens());
		assertEquals("claude-sonnet-4", result.metrics().model());
		assertEquals(AiSdlcStep.CODE_REVIEW.name(), result.metrics().engine());
		assertTrue(result.metrics().durationMs() >= 0);
		assertNotNull(result.metrics().cost());
	}

	// --- helpers ---

	private RoleContext createContext() {
		return new RoleContext("run-1", "parent-1", "child-1", "user-1",
				"Fix the login bug", Map.of(), null, Map.of(), null, null, 0);
	}

}
