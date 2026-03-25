package io.strategiz.social.business.agent.pipeline;

import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineRun;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * Stub implementation of {@link PdlcRoleExecutor} used as a fallback when the real
 * {@link RealPdlcRoleExecutor} is not available (e.g., in integration tests or when
 * role skills are not wired). Returns mock metrics for every role execution without
 * performing actual work.
 *
 * @deprecated Superseded by {@link RealPdlcRoleExecutor}. Retained only as a fallback
 *             for environments where the full role skill stack is not available.
 */
@Deprecated
@Service
@ConditionalOnMissingBean(RealPdlcRoleExecutor.class)
public class StubPdlcRoleExecutor implements PdlcRoleExecutor {

	private static final Logger log = LoggerFactory.getLogger(StubPdlcRoleExecutor.class);

	private static final long STUB_TOKENS = 500L;

	private static final BigDecimal STUB_COST = new BigDecimal("0.01");

	private static final long STUB_DURATION_MS = 100L;

	private static final String STUB_MODEL = "stub-model";

	@Override
	public RoleExecutionResult execute(PipelineRun run, PdlcRole role, String childSparkId) {
		log.info("[PIPELINE-STUB] Executing role={} for run={} childSpark={} (no-op stub)",
				role, run.getId(), childSparkId);

		return RoleExecutionResult.success(STUB_TOKENS, STUB_COST, STUB_DURATION_MS, STUB_MODEL, null);
	}

}
