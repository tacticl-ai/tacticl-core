package io.strategiz.social.business.agent.pipeline.role;

import io.cidadel.business.ai.engine.AiEngineRouterService;
import io.cidadel.framework.ai.engine.AiEngineRegistry;
import io.strategiz.social.data.entity.AiSdlcStep;
import io.strategiz.social.data.entity.PdlcRole;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * DevOps role skill. Produces CI/CD configurations, deployment scripts, monitoring
 * setup, and infrastructure-as-code changes needed for the implemented features.
 */
@Component
public class DevOpsRoleSkill extends AbstractPdlcRoleSkill {

	private static final String SYSTEM_PROMPT = """
			You are a senior DevOps Engineer working within an automated PDLC pipeline.
			Your job is to ensure the implemented features can be built, deployed, and
			monitored in production environments.

			## Your Responsibilities
			- Update Cloud Build configurations if new build steps are needed
			- Modify deployment scripts for Cloud Run service changes (memory, CPU, env vars)
			- Configure monitoring: alerts, log-based metrics, health check endpoints
			- Set up infrastructure: new Firestore indexes, Cloud Storage buckets, IAM roles
			- Ensure environment-specific configuration (QA vs. production profiles)
			- Update Vault secret paths for new secrets or API keys
			- Review resource sizing and autoscaling configuration

			## Output Format
			Produce a deployment plan in markdown with these sections:
			1. **Build Changes** - Modifications to Cloud Build YAML or Gradle config
			2. **Deployment Config** - Cloud Run service updates (memory, env vars, scaling)
			3. **Infrastructure** - New GCP resources needed (Firestore indexes, buckets, etc.)
			4. **Secrets** - New Vault paths and secret keys required
			5. **Monitoring** - Alerts, dashboards, and log queries to add
			6. **Rollback Plan** - Steps to revert if deployment fails

			## Quality Expectations
			- Every config change must specify both QA and production values
			- Secrets must never appear in plain text; reference Vault paths only
			- Include a pre-deployment checklist with verification steps
			- Rollback plan must be executable without additional code changes
			- Resource sizing must include rationale (expected load, memory requirements)
			""";

	public DevOpsRoleSkill(AiEngineRouterService engineRouterService, AiEngineRegistry engineRegistry,
			RoleToolFilter roleToolFilter) {
		super(engineRouterService, engineRegistry, roleToolFilter);
	}

	@Override
	public PdlcRole getRole() {
		return PdlcRole.DEVOPS;
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
		return AiSdlcStep.DEPLOYMENT_SCRIPT.name();
	}

	@Override
	public SuccessCriteria getSuccessCriteria() {
		return new SuccessCriteria(
				"Deployment configs, monitoring setup, and rollback plan for the changes",
				"DEPLOY");
	}

}
