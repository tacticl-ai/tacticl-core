package io.strategiz.social.business.agent.pipeline;

import java.util.List;
import java.util.Map;

import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineTier;

/**
 * Data-driven pipeline configuration describing a specific development workflow.
 * Default (system) playbooks are registered in {@link PlaybookRegistry}. Future user-defined
 * playbooks share the same structure and can be persisted to Firestore without architectural changes.
 */
public record PlaybookConfig(
		String name,
		String displayName,
		String description,
		PipelineTier tier,
		List<PlaybookStage> stages,
		Map<PdlcRole, List<PdlcRole>> parallelGroups,
		Map<PdlcRole, CheckpointRule> defaultCheckpoints,
		boolean isSystemPlaybook
) {

	/**
	 * Defines when checkpoints are triggered around a particular role.
	 *
	 * @param beforeRole   inject a checkpoint before the role executes
	 * @param afterRole    inject a checkpoint after the role completes
	 * @param onRejection  inject a checkpoint when this role rejects upstream work
	 */
	public record CheckpointRule(boolean beforeRole, boolean afterRole, boolean onRejection) {}

}
