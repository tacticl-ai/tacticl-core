package io.tacticl.business.telegram.spark;

import io.tacticl.business.pipeline.router.PdlcRouter;
import io.tacticl.business.sparks.service.SparkService;
import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.business.telegram.permission.MemberPermissionService;
import io.tacticl.business.telegram.permission.PermissionCheck;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.data.sparks.entity.Spark;
import io.tacticl.data.sparks.entity.SparkInitiatorSource;
import io.tacticl.data.sparks.entity.SparkType;
import io.tacticl.data.telegram.entity.MemberRole;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Initiates a Spark from a Telegram group message:
 * permission check → create spark → stamp Telegram provenance → route to PDLC v2 → reply.
 *
 * <p>Called by {@code SparkCommand} (Task 26) when a contributor addresses the bot in a group.
 */
@Service
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class TelegramSparkInitiator {

    private static final Logger log = LoggerFactory.getLogger(TelegramSparkInitiator.class);

    // TODO: read cost ceiling from UserConfig (plan 2026-04-19 — future Phase 3 work).
    // Matches the prod default documented in application-prod.properties for pdlc.v2.
    private static final double COST_CEILING_USD = 50.0;

    private final SparkService sparkService;
    private final PdlcRouter pdlcRouter;
    private final MemberPermissionService permissions;
    private final TelegramOutboundQueue outbound;

    public TelegramSparkInitiator(SparkService sparkService,
                                  PdlcRouter pdlcRouter,
                                  MemberPermissionService permissions,
                                  TelegramOutboundQueue outbound) {
        this.sparkService = sparkService;
        this.pdlcRouter = pdlcRouter;
        this.permissions = permissions;
        this.outbound = outbound;
    }

    /**
     * @param repoUrl nullable — no repo-mapping service exists yet (Phase 2 deferral);
     *                callers pass whatever they have, router treats null as "no repo".
     */
    public void initiate(long chatId, String tacticlUserId, String text,
                         TelegramProjectLink link, String repoUrl) {
        if (text == null || text.isBlank()) {
            reply(chatId, "Spark text is required.");
            return;
        }

        PermissionCheck check = permissions.require(chatId, tacticlUserId, MemberRole.CONTRIBUTOR);
        if (!check.allowed()) {
            reply(chatId, "You need contributor role to start a spark.");
            return;
        }

        Spark spark = sparkService.create(
                tacticlUserId,
                text,
                SparkInitiatorSource.TELEGRAM_GROUP,
                tacticlUserId,
                link.getProjectId());

        // SparkType=CODE is the Phase 2 default. Auto-classification will replace this
        // via SparkClassifierService in a later task; for now CODE is safe because /spark
        // is an explicit intent to run a pipeline.
        Optional<PipelineRun> run;
        try {
            run = pdlcRouter.route(
                    tacticlUserId,
                    spark.getId(),
                    text,
                    repoUrl,
                    SparkType.CODE,
                    List.of(),
                    null,
                    COST_CEILING_USD);
        } catch (RuntimeException e) {
            log.error("Pipeline routing failed for spark {} in chat {}", spark.getId(), chatId, e);
            reply(chatId, "⚠️ Couldn't start the pipeline. Try again or check with an admin.");
            return;
        }

        if (run.isEmpty()) {
            // TODO: when CloudOrchestratorService (legacy cloud path) lands, fall back here
            // instead of replying with a disabled message (plan 2026-04-19 Task 25).
            log.warn("PDLC v2 disabled — spark {} in chat {} not routed to pipeline",
                    spark.getId(), chatId);
            reply(chatId, "⚠️ Pipeline engine is disabled — ask an admin to enable pdlc.v2.");
            return;
        }

        reply(chatId, "▶️ Started — I'll post updates here.");
    }

    private void reply(long chatId, String text) {
        outbound.enqueue(chatId, new OutboundMessage(SendMessageRequest.plain(chatId, text)));
    }
}
