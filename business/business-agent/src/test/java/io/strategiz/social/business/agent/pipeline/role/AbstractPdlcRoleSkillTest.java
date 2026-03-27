package io.strategiz.social.business.agent.pipeline.role;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.cidadel.business.ai.engine.AiEngineRouterService;
import io.cidadel.framework.ai.engine.AiEngine;
import io.cidadel.framework.ai.engine.AiEngineRegistry;
import io.cidadel.framework.ai.engine.model.AiEngineResult;
import io.strategiz.social.business.agent.pipeline.PlaybookConfig;
import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineArtifact;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbstractPdlcRoleSkillTest {

	@Mock
	private AiEngineRouterService engineRouterService;

	@Mock
	private AiEngineRegistry engineRegistry;

	@Mock
	private AiEngine overrideEngine;

	@Mock
	private RoleToolFilter roleToolFilter;

	// --- buildPrompt tests ---

	@Test
	void buildPrompt_includesOriginalRequest() {
		TestableRoleSkill skill = new TestableRoleSkill(engineRouterService, engineRegistry, roleToolFilter);
		RoleContext ctx = createContext("Build a notification system", null, 0, Map.of());

		String prompt = skill.exposeBuildPrompt(ctx);

		assertTrue(prompt.contains("## User Request"));
		assertTrue(prompt.contains("Build a notification system"));
	}

	@Test
	void buildPrompt_includesReworkFeedback() {
		TestableRoleSkill skill = new TestableRoleSkill(engineRouterService, engineRegistry, roleToolFilter);
		RoleContext ctx = createContext("Build feature", "Missing error handling in UserService", 2, Map.of());

		String prompt = skill.exposeBuildPrompt(ctx);

		assertTrue(prompt.contains("## Rework Required (Iteration 2)"));
		assertTrue(prompt.contains("Missing error handling in UserService"));
	}

	@Test
	void buildPrompt_excludesReworkSectionWhenNull() {
		TestableRoleSkill skill = new TestableRoleSkill(engineRouterService, engineRegistry, roleToolFilter);
		RoleContext ctx = createContext("Build feature", null, 0, Map.of());

		String prompt = skill.exposeBuildPrompt(ctx);

		assertFalse(prompt.contains("Rework Required"));
	}

	@Test
	void buildPrompt_includesUpstreamArtifacts() {
		TestableRoleSkill skill = new TestableRoleSkill(engineRouterService, engineRegistry, roleToolFilter);

		PipelineArtifact pmArtifact = new PipelineArtifact();
		pmArtifact.setRole(PdlcRole.PM);
		pmArtifact.setContent(Map.of("content", "Requirements: build auth flow"));

		PipelineArtifact researchArtifact = new PipelineArtifact();
		researchArtifact.setRole(PdlcRole.RESEARCHER);
		researchArtifact.setContent(Map.of("summary", "Found existing OAuth implementation"));

		Map<PdlcRole, PipelineArtifact> upstream = new LinkedHashMap<>();
		upstream.put(PdlcRole.PM, pmArtifact);
		upstream.put(PdlcRole.RESEARCHER, researchArtifact);

		RoleContext ctx = createContext("Build auth", null, 0, upstream);

		String prompt = skill.exposeBuildPrompt(ctx);

		assertTrue(prompt.contains("## Previous Role Outputs"));
		assertTrue(prompt.contains("### PM"));
		assertTrue(prompt.contains("Requirements: build auth flow"));
		assertTrue(prompt.contains("### RESEARCHER"));
		assertTrue(prompt.contains("Found existing OAuth implementation"));
	}

	@Test
	void buildPrompt_excludesUpstreamSectionWhenEmpty() {
		TestableRoleSkill skill = new TestableRoleSkill(engineRouterService, engineRegistry, roleToolFilter);
		RoleContext ctx = createContext("Simple request", null, 0, Map.of());

		String prompt = skill.exposeBuildPrompt(ctx);

		assertFalse(prompt.contains("Previous Role Outputs"));
	}

	@Test
	void buildPrompt_includesGitContext() {
		TestableRoleSkill skill = new TestableRoleSkill(engineRouterService, engineRegistry, roleToolFilter);
		GitContext git = new GitContext("acme/my-service", "main", "feat/auth", "abc123");
		RoleContext ctx = new RoleContext("run-1", "parent-1", "child-1", "user-1",
				"Build feature", Map.of(), null, Map.of(), git, null, 0);

		String prompt = skill.exposeBuildPrompt(ctx);

		assertTrue(prompt.contains("## Git Context"));
		assertTrue(prompt.contains("acme/my-service"));
		assertTrue(prompt.contains("feat/auth"));
		assertTrue(prompt.contains("abc123"));
	}

	@Test
	void buildPrompt_excludesGitContextWhenNull() {
		TestableRoleSkill skill = new TestableRoleSkill(engineRouterService, engineRegistry, roleToolFilter);
		RoleContext ctx = createContext("Build feature", null, 0, Map.of());

		String prompt = skill.exposeBuildPrompt(ctx);

		assertFalse(prompt.contains("Git Context"));
	}

	// --- execute tests ---

	@Test
	void execute_successfulEngineResult_returnsCompleted() {
		TestableRoleSkill skill = new TestableRoleSkill(engineRouterService, engineRegistry, roleToolFilter);

		AiEngineResult engineResult = AiEngineResult.success("Generated requirements", "api-anthropic", "claude-sonnet-4");
		engineResult.setTotalTokens(1500);
		when(engineRouterService.executeStep(eq("TEST_STEP"), any())).thenReturn(engineResult);

		RoleContext ctx = createContext("Build feature", null, 0, Map.of());
		RoleResult result = skill.execute(ctx);

		assertTrue(result.outcome() == RoleOutcome.COMPLETED);
		assertTrue(result.summary().contains("Generated requirements"));
		assertTrue(result.metrics().tokens() == 1500);
	}

	@Test
	void execute_failedEngineResult_returnsFailed() {
		TestableRoleSkill skill = new TestableRoleSkill(engineRouterService, engineRegistry, roleToolFilter);

		AiEngineResult engineResult = AiEngineResult.error("rate limit exceeded", "api-anthropic");
		when(engineRouterService.executeStep(eq("TEST_STEP"), any())).thenReturn(engineResult);

		RoleContext ctx = createContext("Build feature", null, 0, Map.of());
		RoleResult result = skill.execute(ctx);

		assertTrue(result.outcome() == RoleOutcome.FAILED);
		assertTrue(result.rejectionReason().contains("rate limit exceeded"));
	}

	@Test
	void execute_engineThrows_returnsFailed() {
		TestableRoleSkill skill = new TestableRoleSkill(engineRouterService, engineRegistry, roleToolFilter);

		when(engineRouterService.executeStep(eq("TEST_STEP"), any()))
				.thenThrow(new RuntimeException("connection timeout"));

		RoleContext ctx = createContext("Build feature", null, 0, Map.of());
		RoleResult result = skill.execute(ctx);

		assertTrue(result.outcome() == RoleOutcome.FAILED);
		assertTrue(result.rejectionReason().contains("connection timeout"));
	}

	@Test
	void execute_modelOverride_setsModelOnRequest() {
		TestableRoleSkill skill = new TestableRoleSkill(engineRouterService, engineRegistry, roleToolFilter);

		AiEngineResult engineResult = AiEngineResult.success("Output with overridden model", "api-anthropic",
				"claude-opus-4-6");
		engineResult.setTotalTokens(2000);
		// The router is still called (no engineIdOverride), model is pre-set on the request
		when(engineRouterService.executeStep(eq("TEST_STEP"), any())).thenReturn(engineResult);

		RoleContext ctx = new RoleContext("run-1", "parent-1", "child-1", "user-1",
				"Build feature", Map.of(), null, Map.of(), null, null, 0, null, "claude-opus-4-6");
		RoleResult result = skill.execute(ctx);

		assertTrue(result.outcome() == RoleOutcome.COMPLETED);
		// The router was called (no engine override), model was set on request by executeWithEngine
		verify(engineRouterService).executeStep(eq("TEST_STEP"), any());
	}

	@Test
	void execute_engineIdOverride_callsEngineDirectly() {
		TestableRoleSkill skill = new TestableRoleSkill(engineRouterService, engineRegistry, roleToolFilter);

		AiEngineResult overrideResult = AiEngineResult.success("Output from override engine", "openai-agentic",
				"gpt-4o");
		overrideResult.setTotalTokens(1800);
		when(engineRegistry.getEngine("openai-agentic")).thenReturn(Optional.of(overrideEngine));
		when(overrideEngine.execute(any())).thenReturn(overrideResult);

		RoleContext ctx = new RoleContext("run-1", "parent-1", "child-1", "user-1",
				"Build feature", Map.of(), null, Map.of(), null, null, 0, "openai-agentic", null);
		RoleResult result = skill.execute(ctx);

		assertTrue(result.outcome() == RoleOutcome.COMPLETED);
		// Router must NOT be called when engine override is active
		verify(engineRouterService, never()).executeStep(anyString(), any());
		verify(overrideEngine).execute(any());
	}

	// --- helpers ---

	private RoleContext createContext(String request, String reworkFeedback, int reworkIteration,
			Map<PdlcRole, PipelineArtifact> upstream) {
		return new RoleContext("run-1", "parent-1", "child-1", "user-1",
				request, Map.of(), null, upstream, null, reworkFeedback, reworkIteration);
	}

	/**
	 * Concrete subclass of AbstractPdlcRoleSkill for testing the abstract base class methods.
	 */
	private static class TestableRoleSkill extends AbstractPdlcRoleSkill {

		TestableRoleSkill(AiEngineRouterService engineRouterService, AiEngineRegistry engineRegistry,
				RoleToolFilter roleToolFilter) {
			super(engineRouterService, engineRegistry, roleToolFilter);
		}

		@Override
		public PdlcRole getRole() {
			return PdlcRole.PM;
		}

		@Override
		public String getSystemPrompt() {
			return "Test system prompt";
		}

		@Override
		public List<String> getAvailableTools() {
			return List.of("search_web");
		}

		@Override
		public String getAiSdlcStepName() {
			return "TEST_STEP";
		}

		@Override
		public SuccessCriteria getSuccessCriteria() {
			return new SuccessCriteria("Test criteria", "TEST");
		}

		/** Expose protected buildPrompt for testing. */
		String exposeBuildPrompt(RoleContext ctx) {
			return buildPrompt(ctx);
		}
	}

}
