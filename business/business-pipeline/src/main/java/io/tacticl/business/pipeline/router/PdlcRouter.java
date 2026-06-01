package io.tacticl.business.pipeline.router;

import io.tacticl.business.pipeline.service.PdlcV2Service;
import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.data.sparks.entity.SparkType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

/**
 * Routes PDLC-eligible sparks to v2 (arbiter) or v1 (in-JVM, when flag is off).
 * Only CODE and DEVOPS spark types are PDLC-eligible.
 */
@Service
public class PdlcRouter {

    private static final Logger log = LoggerFactory.getLogger(PdlcRouter.class);

    private final PdlcV2Service pdlcV2Service;
    private final boolean v2Enabled;

    public PdlcRouter(PdlcV2Service pdlcV2Service,
                      @Value("${pdlc.v2.enabled:false}") boolean v2Enabled) {
        this.pdlcV2Service = pdlcV2Service;
        this.v2Enabled = v2Enabled;
    }

    /**
     * Routes a spark to PDLC v2 if the flag is on and the spark type is eligible.
     * Returns empty if v2 is disabled or if the spark type is not PDLC-eligible.
     */
    public Optional<PipelineRun> route(String userId, String sparkId, String sparkRequest,
                                       String repoUrl, SparkType sparkType,
                                       List<String> skipRoles, String githubToken,
                                       double costCeilingUsd) {
        if (!v2Enabled) {
            log.debug("PDLC v2 disabled — skipping v2 route for spark {}", sparkId);
            return Optional.empty();
        }
        if (sparkType != SparkType.CODE && sparkType != SparkType.DEVOPS) {
            log.debug("Spark {} type {} is not PDLC-eligible", sparkId, sparkType);
            return Optional.empty();
        }
        String playbook = resolvePlaybook(sparkRequest);
        log.info("Routing spark {} ({}) to PDLC v2 with playbook {}", sparkId, sparkType, playbook);
        // This v1 route is tacticl-only, so the product is hardcoded here. The Phase-1 dispatch
        // front door (Discord→PDLC) will supply the product from the entry-point registry instead.
        PipelineRun run = pdlcV2Service.submitPipeline("tacticl", userId, sparkId, sparkRequest,
                                                        repoUrl, playbook, skipRoles,
                                                        githubToken, costCeilingUsd);
        return Optional.of(run);
    }

    private String resolvePlaybook(String sparkRequest) {
        // Default to FULL_PDLC — classifier on arbiter side will refine
        return "FULL_PDLC";
    }
}
