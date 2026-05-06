package io.tacticl.business.telegram.spark;

import io.tacticl.business.agent.command.AgentCommand;
import io.tacticl.business.agent.command.AgentCommandResult;
import io.tacticl.business.agent.command.AgentCommandService;
import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.business.telegram.permission.MemberPermissionService;
import io.tacticl.business.telegram.permission.PermissionCheck;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import io.tacticl.data.telegram.entity.MemberRole;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Initiates a Spark from a Telegram group message:
 * permission check → delegate to {@link AgentCommandService} → reply.
 *
 * <p>Called by {@code SparkCommand} when a contributor addresses the bot in a group.
 * Spark creation, classification, and pipeline routing all live in
 * {@link AgentCommandService}; this adapter only carries Telegram-specific
 * concerns (permission, text validation, outbound reply formatting).
 */
@Service
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class TelegramSparkInitiator {

    private static final Logger log = LoggerFactory.getLogger(TelegramSparkInitiator.class);

    // Telegram group messages allow up to 4096 chars but Sparks above ~2k chars are almost
    // always paste-spam or accidental dumps — routing them wastes tokens and can pin
    // the LLM into oversized prompts. Reject (don't truncate) so user intent is preserved.
    private static final int MAX_SPARK_TEXT_CHARS = 2000;

    private final AgentCommandService agentCommandService;
    private final MemberPermissionService permissions;
    private final TelegramOutboundQueue outbound;

    public TelegramSparkInitiator(AgentCommandService agentCommandService,
                                  MemberPermissionService permissions,
                                  TelegramOutboundQueue outbound) {
        this.agentCommandService = agentCommandService;
        this.permissions = permissions;
        this.outbound = outbound;
    }

    /**
     * @param repoUrl nullable — no repo-mapping service exists yet; callers pass
     *                whatever they have, the router treats null as "no repo".
     */
    public void initiate(long chatId, String tacticlUserId, String text,
                         TelegramProjectLink link, String repoUrl) {
        if (text == null || text.isBlank()) {
            reply(chatId, "Spark text is required.");
            return;
        }

        if (text.length() > MAX_SPARK_TEXT_CHARS) {
            reply(chatId, "Your spark text is too long (max " + MAX_SPARK_TEXT_CHARS + " chars).");
            return;
        }

        PermissionCheck check = permissions.require(chatId, tacticlUserId, MemberRole.CONTRIBUTOR);
        if (!check.allowed()) {
            reply(chatId, "You need contributor role to start a spark.");
            return;
        }

        AgentCommand cmd = AgentCommand.fromTelegramGroup(tacticlUserId, text, link.getProjectId(), repoUrl);
        AgentCommandResult result;
        try {
            result = agentCommandService.execute(cmd);
        } catch (RuntimeException e) {
            log.error("Agent command failed for spark in chat {}", chatId, e);
            reply(chatId, "⚠️ Couldn't start the spark. Try again or check with an admin.");
            return;
        }

        if (!result.succeeded()) {
            reply(chatId, "⚠️ " + result.responseText());
            return;
        }
        if (result.pipelineRunId() != null) {
            reply(chatId, "▶️ Started — I'll post updates here.");
        } else {
            reply(chatId, result.responseText());
        }
    }

    private void reply(long chatId, String text) {
        outbound.enqueue(chatId, new OutboundMessage(SendMessageRequest.plain(chatId, text)));
    }
}
