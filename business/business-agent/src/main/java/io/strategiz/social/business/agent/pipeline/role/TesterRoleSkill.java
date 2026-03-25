package io.strategiz.social.business.agent.pipeline.role;

import io.cidadel.business.ai.engine.AiEngineRouterService;
import io.cidadel.client.base.llm.model.ToolDefinition;
import io.cidadel.framework.ai.engine.model.AiEngineRequest;
import io.cidadel.framework.ai.engine.model.AiEngineResult;
import io.cidadel.framework.ai.engine.model.AiEngineToolDefinition;
import io.strategiz.social.data.entity.AiSdlcStep;
import io.strategiz.social.data.entity.PdlcRole;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Tester role skill. Writes and executes tests for the code produced by the Implementer.
 * Can REJECT work back to the Implementer if tests reveal bugs or the code is untestable.
 */
@Component
public class TesterRoleSkill extends AbstractPdlcRoleSkill {

	private static final Logger log = LoggerFactory.getLogger(TesterRoleSkill.class);

	private static final String REJECTION_MARKER = "REJECTED";

	private static final String SYSTEM_PROMPT = """
			You are a senior QA Engineer working within an automated PDLC pipeline.
			Your job is to write comprehensive tests for the code produced by the Implementer
			and verify the implementation meets all acceptance criteria from the PRD.

			## Your Responsibilities
			- Write unit tests for every new public method and service
			- Write integration tests for API endpoints and cross-component interactions
			- Test error paths: invalid input, missing data, service failures, timeouts
			- Verify acceptance criteria from the PRD are covered by tests
			- Test edge cases: empty collections, null values, boundary conditions
			- Use Mockito for unit tests, mock external dependencies appropriately
			- Follow the project's test conventions (JUnit 6, @ExtendWith(MockitoExtension.class))

			## Decision: PASS or REJECT
			After writing and reviewing tests, make a clear decision:
			- **PASSED** - All tests pass, code meets acceptance criteria
			- **REJECTED** - Tests reveal bugs or code is not testable as written

			## Output Format
			1. **Test Summary** - Overview of tests written and coverage assessment
			2. **Test Files** - List of test files created with test method descriptions
			3. **Test Results** - Pass/fail status for each test category
			4. **Coverage Gaps** - Any acceptance criteria not covered and why
			5. **Decision** - Either "PASSED" or "REJECTED: <reason>"
			6. **Bug Report** - If rejected, specific bugs found with reproduction steps

			## Quality Expectations
			- Every acceptance criterion must have at least one test
			- Unit tests must be fast and isolated (no real I/O)
			- Test names must describe the scenario: method_condition_expectedResult
			- Use constructor injection patterns matching the production code
			""";

	public TesterRoleSkill(AiEngineRouterService engineRouterService, RoleToolFilter roleToolFilter) {
		super(engineRouterService, roleToolFilter);
	}

	@Override
	public PdlcRole getRole() {
		return PdlcRole.TESTER;
	}

	@Override
	public String getSystemPrompt() {
		return SYSTEM_PROMPT;
	}

	@Override
	public List<String> getAvailableTools() {
		return List.of(
				"search_web", "browse_web",
				"github_read_file", "github_list_files", "github_search_code",
				"github_commit", "github_create_branch", "github_create_pr", "github_merge_pr");
	}

	@Override
	public String getAiSdlcStepName() {
		return AiSdlcStep.TEST_GENERATION.name();
	}

	@Override
	public SuccessCriteria getSuccessCriteria() {
		return new SuccessCriteria(
				"Test suite committed with passing tests covering all acceptance criteria",
				"TEST_RESULTS");
	}

	@Override
	public RoleResult execute(RoleContext ctx) {
		long start = System.currentTimeMillis();

		AiEngineRequest request = new AiEngineRequest();
		request.setPrompt(buildPrompt(ctx));
		request.setSystemPrompt(getSystemPrompt());
		request.setMetadata(Map.of(
				"sparkId", ctx.childSparkId(),
				"userId", ctx.userId(),
				"pipelineRunId", ctx.pipelineRunId(),
				"pdlcRole", getRole().name()));

		try {
			AiEngineResult result = engineRouterService.executeStep(getAiSdlcStepName(), request);
			long duration = System.currentTimeMillis() - start;

			RoleMetrics metrics = new RoleMetrics(
					result.getTotalTokens(),
					estimateCost(result.getTotalTokens(), result.getModel()),
					duration,
					result.getModel(),
					getAiSdlcStepName());

			if (!result.isSuccess()) {
				log.warn("[PDLC-ROLE] TESTER failed for spark={}: {}",
						ctx.childSparkId(), result.getError());
				return RoleResult.failed(result.getError(), metrics);
			}

			String content = result.getContent();

			if (content != null && content.contains(REJECTION_MARKER)) {
				log.info("[PDLC-ROLE] TESTER rejected work for spark={}, routing rework to IMPLEMENTER",
						ctx.childSparkId());
				return RoleResult.rejected(content, PdlcRole.IMPLEMENTER, metrics);
			}

			log.info("[PDLC-ROLE] TESTER passed for spark={} in {}ms",
					ctx.childSparkId(), duration);
			return RoleResult.completed(
					List.of(Map.of("content", content)),
					content,
					metrics);
		}
		catch (Exception e) {
			long duration = System.currentTimeMillis() - start;
			log.error("[PDLC-ROLE] TESTER threw exception for spark={}: {}",
					ctx.childSparkId(), e.getMessage(), e);
			RoleMetrics metrics = new RoleMetrics(0, BigDecimal.ZERO, duration, null, getAiSdlcStepName());
			return RoleResult.failed(e.getMessage(), metrics);
		}
	}

}
