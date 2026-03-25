package io.strategiz.social.business.agent.pipeline;

import io.strategiz.social.business.agent.pipeline.role.GitContext;
import io.strategiz.social.business.agent.pipeline.role.PdlcRoleRegistry;
import io.strategiz.social.business.agent.pipeline.role.PdlcRoleSkill;
import io.strategiz.social.business.agent.pipeline.role.RoleContext;
import io.strategiz.social.business.agent.pipeline.role.RoleOutcome;
import io.strategiz.social.business.agent.pipeline.role.RoleResult;
import io.strategiz.social.business.agent.service.SparkService;
import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineArtifact;
import io.strategiz.social.data.entity.PipelineRun;
import io.strategiz.social.data.entity.Spark;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Production implementation of {@link PdlcRoleExecutor} that bridges the orchestrator's interface
 * to the actual {@link PdlcRoleSkill} implementations. Builds a {@link RoleContext} with upstream
 * artifacts, knowledge context, and optional rework feedback, then delegates to the skill registered
 * for the target role.
 *
 * <p>Annotated with {@link Primary} so Spring injects this over {@link StubPdlcRoleExecutor}
 * when both are present in the application context.</p>
 */
@Service
@Primary
public class RealPdlcRoleExecutor implements PdlcRoleExecutor {

	private static final Logger log = LoggerFactory.getLogger(RealPdlcRoleExecutor.class);

	private final PdlcRoleRegistry roleRegistry;

	private final PipelineArtifactService artifactService;

	private final KnowledgeBaseService knowledgeBaseService;

	private final SparkService sparkService;

	private final PlaybookRegistry playbookRegistry;

	public RealPdlcRoleExecutor(PdlcRoleRegistry roleRegistry, PipelineArtifactService artifactService,
			KnowledgeBaseService knowledgeBaseService, SparkService sparkService,
			PlaybookRegistry playbookRegistry) {
		this.roleRegistry = roleRegistry;
		this.artifactService = artifactService;
		this.knowledgeBaseService = knowledgeBaseService;
		this.sparkService = sparkService;
		this.playbookRegistry = playbookRegistry;
	}

	@Override
	public RoleExecutionResult execute(PipelineRun run, PdlcRole role, String childSparkId) {
		return execute(run, role, childSparkId, null, 0);
	}

	/**
	 * Execute a role with optional rework feedback and iteration count.
	 *
	 * @param run             the pipeline run context
	 * @param role            the role to execute
	 * @param childSparkId    the child spark for this role execution
	 * @param reworkFeedback  feedback from a rejecting role (null on first execution)
	 * @param reworkIteration rework iteration (0 on first execution)
	 * @return the execution result
	 */
	public RoleExecutionResult execute(PipelineRun run, PdlcRole role, String childSparkId,
			String reworkFeedback, int reworkIteration) {
		log.info("[PIPELINE-REAL] Executing role={} for run={} childSpark={} rework={}",
				role, run.getId(), childSparkId, reworkIteration > 0);

		// 1. Get role skill from registry
		PdlcRoleSkill skill = roleRegistry.getRole(role)
				.orElseThrow(() -> new IllegalStateException("No skill registered for role: " + role));

		// 2. Build stage order from playbook and gather upstream artifacts
		List<PdlcRole> stageOrder = resolveStageOrder(run);
		Map<PdlcRole, PipelineArtifact> upstreamArtifacts =
				artifactService.getUpstreamArtifacts(run.getId(), role, stageOrder);

		// 3. Build knowledge context and inject into the original request augmentation
		String knowledgeContext = knowledgeBaseService.buildKnowledgeContext(role, resolveOriginalRequest(run));

		// 4. Resolve the original request text from the parent spark
		String originalRequest = resolveOriginalRequest(run);
		if (!knowledgeContext.isEmpty()) {
			originalRequest = originalRequest + "\n\n" + knowledgeContext;
		}

		// 5. Resolve playbook config
		PlaybookConfig playbook = playbookRegistry.getPlaybook(run.getPlaybook()).orElse(null);

		// 6. Resolve git context from PipelineRun metadata
		GitContext gitContext = resolveGitContext(run);

		// 7. Resolve classification from PipelineRun
		Map<String, Object> classification = run.getClassificationResult() != null
				? run.getClassificationResult()
				: Collections.emptyMap();

		// 8. Build RoleContext
		RoleContext ctx = new RoleContext(
				run.getId(),
				run.getSparkId(),
				childSparkId,
				run.getUserId(),
				originalRequest,
				classification,
				playbook,
				upstreamArtifacts,
				gitContext,
				reworkFeedback,
				reworkIteration
		);

		// 9. Execute role skill
		RoleResult result = skill.execute(ctx);

		// 10. Store artifact if completed with content
		String artifactId = null;
		if (result.outcome() == RoleOutcome.COMPLETED
				&& result.artifacts() != null
				&& !result.artifacts().isEmpty()) {
			String artifactType = skill.getSuccessCriteria().requiredArtifactType();
			artifactId = artifactService.store(
					run.getId(),
					role,
					childSparkId,
					artifactType,
					result.artifacts().get(0)
			);
			log.info("[PIPELINE-REAL] Stored artifact {} for role={} run={}", artifactId, role, run.getId());
		}

		// 11. Convert RoleResult to RoleExecutionResult
		if (result.outcome() == RoleOutcome.REJECTED) {
			log.info("[PIPELINE-REAL] Role {} rejected with reason='{}' target={} for run={}",
					role, result.rejectionReason(), result.reworkTarget(), run.getId());
			return RoleExecutionResult.rejection(
					result.metrics().tokens(),
					result.metrics().cost(),
					result.metrics().durationMs(),
					result.metrics().model(),
					result.reworkTarget(),
					result.rejectionReason()
			);
		}

		if (result.outcome() == RoleOutcome.FAILED) {
			log.warn("[PIPELINE-REAL] Role {} failed: {} for run={}", role, result.rejectionReason(), run.getId());
		}

		return RoleExecutionResult.success(
				result.metrics().tokens(),
				result.metrics().cost(),
				result.metrics().durationMs(),
				result.metrics().model(),
				artifactId
		);
	}

	/**
	 * Resolve the ordered list of roles from the pipeline run's playbook configuration.
	 */
	private List<PdlcRole> resolveStageOrder(PipelineRun run) {
		Optional<PlaybookConfig> playbookOpt = playbookRegistry.getPlaybook(run.getPlaybook());
		if (playbookOpt.isEmpty()) {
			return List.of();
		}
		return playbookOpt.get().stages().stream()
				.map(PlaybookStage::role)
				.toList();
	}

	/**
	 * Resolve the original user request text from the parent spark's description.
	 */
	private String resolveOriginalRequest(PipelineRun run) {
		Optional<Spark> sparkOpt = sparkService.getSparkInternal(run.getSparkId());
		if (sparkOpt.isPresent()) {
			Spark spark = sparkOpt.get();
			// Prefer description (the user's raw input), fall back to title
			if (spark.getDescription() != null && !spark.getDescription().isBlank()) {
				return spark.getDescription();
			}
			if (spark.getTitle() != null && !spark.getTitle().isBlank()) {
				return spark.getTitle();
			}
		}
		return "No original request available for spark: " + run.getSparkId();
	}

	/**
	 * Resolve git context from the PipelineRun's gitContext metadata map, if present.
	 */
	private GitContext resolveGitContext(PipelineRun run) {
		Map<String, Object> gitMap = run.getGitContext();
		if (gitMap == null || gitMap.isEmpty()) {
			return null;
		}
		return new GitContext(
				(String) gitMap.get("repoFullName"),
				(String) gitMap.get("baseBranch"),
				(String) gitMap.get("workingBranch"),
				(String) gitMap.get("latestCommitSha")
		);
	}

}
