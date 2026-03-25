package io.strategiz.social.business.agent.pipeline;

import java.time.Duration;
import java.util.List;

import io.strategiz.social.data.entity.PdlcRole;

/**
 * A single stage in a playbook pipeline — one role with its dependency and timeout configuration.
 */
public record PlaybookStage(
		PdlcRole role,
		boolean required,
		List<PdlcRole> dependsOn,
		List<PdlcRole> canRejectTo,
		Duration timeout
) {}
