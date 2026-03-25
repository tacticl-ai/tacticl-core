package io.strategiz.social.business.agent.pipeline.role;

import io.cidadel.business.ai.engine.AiEngineRouterService;
import io.cidadel.framework.ai.engine.model.AiEngineRequest;
import io.cidadel.framework.ai.engine.model.AiEngineResult;
import io.strategiz.social.data.entity.AiSdlcStep;
import io.strategiz.social.data.entity.PdlcRole;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Security Analyst role skill. Reviews code for security vulnerabilities following
 * OWASP guidelines, checks for credential exposure, injection risks, and authentication
 * bypasses. Can REJECT work back to the Implementer if critical vulnerabilities are found.
 */
@Component
public class SecurityAnalystRoleSkill extends AbstractPdlcRoleSkill {

	private static final Logger log = LoggerFactory.getLogger(SecurityAnalystRoleSkill.class);

	private static final String REJECTION_MARKER = "REJECTED";

	private static final String SYSTEM_PROMPT = """
			You are a senior Security Analyst working within an automated PDLC pipeline.
			Your job is to review all code changes for security vulnerabilities and ensure
			the implementation follows security best practices.

			## Your Responsibilities
			- Perform OWASP Top 10 analysis on all code changes
			- Check for injection vulnerabilities: SQL injection, NoSQL injection, XSS, SSRF
			- Verify authentication and authorization: proper @RequireAuth usage, scope checks
			- Audit credential handling: no hardcoded secrets, proper Vault integration
			- Review input validation: sanitization, length limits, type checking
			- Check for information leakage: error messages, stack traces, debug endpoints
			- Verify CORS configuration and CSP headers where applicable
			- Assess dependency security: known CVEs in new dependencies
			- Review rate limiting and abuse prevention on new endpoints

			## Decision: SECURE or REJECT
			After your security review, make a clear decision:
			- **SECURE** - No critical or high-severity vulnerabilities found
			- **REJECTED** - Critical or high vulnerabilities that must be fixed

			## Output Format
			1. **Security Summary** - Overall security posture assessment
			2. **Vulnerability Findings** - Numbered list with severity (critical/high/medium/low)
			3. **OWASP Checklist** - Status for each relevant OWASP Top 10 category
			4. **Credential Audit** - Assessment of secret handling
			5. **Decision** - Either "SECURE" or "REJECTED: <reason>"
			6. **Remediation Steps** - If rejected, specific fixes required

			## Quality Expectations
			- Every finding must include the specific file, method, and vulnerable pattern
			- Critical and high findings require immediate rejection
			- Medium findings should be documented but do not block
			- Suggest secure alternatives for every vulnerable pattern found
			""";

	public SecurityAnalystRoleSkill(AiEngineRouterService engineRouterService) {
		super(engineRouterService);
	}

	@Override
	public PdlcRole getRole() {
		return PdlcRole.SECURITY_ANALYST;
	}

	@Override
	public String getSystemPrompt() {
		return SYSTEM_PROMPT;
	}

	@Override
	public List<String> getAvailableTools() {
		return List.of("browse_web", "search_web", "github_read_file", "github_search_code");
	}

	@Override
	public String getAiSdlcStepName() {
		return AiSdlcStep.SECURITY_REVIEW.name();
	}

	@Override
	public SuccessCriteria getSuccessCriteria() {
		return new SuccessCriteria(
				"A security review with OWASP analysis and a clear SECURE or REJECTED decision",
				"SECURITY_REPORT");
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
				log.warn("[PDLC-ROLE] SECURITY_ANALYST failed for spark={}: {}",
						ctx.childSparkId(), result.getError());
				return RoleResult.failed(result.getError(), metrics);
			}

			String content = result.getContent();

			if (content != null && content.contains(REJECTION_MARKER)) {
				log.info("[PDLC-ROLE] SECURITY_ANALYST rejected work for spark={}, routing rework to IMPLEMENTER",
						ctx.childSparkId());
				return RoleResult.rejected(content, PdlcRole.IMPLEMENTER, metrics);
			}

			log.info("[PDLC-ROLE] SECURITY_ANALYST passed for spark={} in {}ms",
					ctx.childSparkId(), duration);
			return RoleResult.completed(
					List.of(Map.of("content", content)),
					content,
					metrics);
		}
		catch (Exception e) {
			long duration = System.currentTimeMillis() - start;
			log.error("[PDLC-ROLE] SECURITY_ANALYST threw exception for spark={}: {}",
					ctx.childSparkId(), e.getMessage(), e);
			RoleMetrics metrics = new RoleMetrics(0, BigDecimal.ZERO, duration, null, getAiSdlcStepName());
			return RoleResult.failed(e.getMessage(), metrics);
		}
	}

}
