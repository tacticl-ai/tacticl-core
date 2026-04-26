package io.tacticl.business.telegram.command;

import io.tacticl.business.telegram.audit.TelegramAuditLogger;
import io.tacticl.business.telegram.command.ProjectPipelineSummaryProvider.ProjectPipelineSummary;
import io.tacticl.business.telegram.identity.TelegramIdentityResolver;
import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.business.telegram.router.CommandContext;
import io.tacticl.business.telegram.router.CommandHandler;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import io.tacticl.data.telegram.repository.TelegramProjectLinkRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Handles {@code /status}: reports active spark count, last activity, and
 * cost-to-date for the project linked to the current group.
 *
 * <p>The {@link ProjectPipelineSummaryProvider} implementation lands in Chunk 8;
 * until then it is injected as {@link Optional#empty()} and this command
 * degrades to a "not yet enabled" message.
 */
@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class StatusCommand implements CommandHandler {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final TelegramProjectLinkRepository projectRepo;
    private final Optional<ProjectPipelineSummaryProvider> summaryProvider;
    private final TelegramOutboundQueue outbound;
    private final TelegramIdentityResolver identity;
    private final TelegramAuditLogger auditLogger;

    public StatusCommand(TelegramProjectLinkRepository projectRepo,
                         Optional<ProjectPipelineSummaryProvider> summaryProvider,
                         TelegramOutboundQueue outbound,
                         TelegramIdentityResolver identity,
                         TelegramAuditLogger auditLogger) {
        this.projectRepo = projectRepo;
        this.summaryProvider = summaryProvider;
        this.outbound = outbound;
        this.identity = identity;
        this.auditLogger = auditLogger;
    }

    @Override
    public String commandName() {
        return "/status";
    }

    @Override
    public Scope scope() {
        return Scope.GROUP;
    }

    @Override
    public void handle(CommandContext ctx) {
        long chatId = ctx.chatId();

        // WHY: identity is resolved purely for the audit row — /status is open to anyone in the group.
        String tacticlUserId = identity.resolveByChatId(ctx.telegramUserId()).orElse(null);

        Optional<TelegramProjectLink> linkOpt = projectRepo.findByChatIdAndIsActiveTrue(chatId);
        if (linkOpt.isEmpty()) {
            reply(chatId, "No active project in this group.");
            audit(ctx, tacticlUserId, Map.of("rejected", "no_project"));
            return;
        }

        String projectId = linkOpt.get().getProjectId();
        if (summaryProvider.isEmpty()) {
            reply(chatId, "Status reporting is not yet enabled.");
            audit(ctx, tacticlUserId, Map.of("rejected", "summary_disabled", "projectId", projectId));
            return;
        }

        ProjectPipelineSummary summary = summaryProvider.get().summarize(projectId);
        if (summary == null) {
            reply(chatId, "Status reporting is not yet enabled.");
            audit(ctx, tacticlUserId, Map.of("rejected", "summary_unavailable", "projectId", projectId));
            return;
        }

        String lastActivity = summary.lastActivity() != null ? summary.lastActivity().toString() : "—";
        String cost = String.format(Locale.ROOT, "$%.2f",
                summary.costToDate() != null ? summary.costToDate() : java.math.BigDecimal.ZERO);

        reply(chatId,
                "Project status\n"
                        + "Active sparks: " + summary.activeSparks() + "\n"
                        + "Last activity: " + lastActivity + "\n"
                        + "Cost-to-date: " + cost);
        audit(ctx, tacticlUserId,
                Map.of("projectId", projectId, "activeSparks", summary.activeSparks()));
    }

    private void reply(long chatId, String text) {
        outbound.enqueue(chatId, new OutboundMessage(SendMessageRequest.plain(chatId, text)));
    }

    private void audit(CommandContext ctx, String tacticlUserId, Map<String, ?> payload) {
        String json;
        try {
            json = MAPPER.writeValueAsString(payload);
        } catch (RuntimeException e) {
            json = null;
        }
        auditLogger.record(ctx.chatId(), ctx.telegramUserId(), tacticlUserId, "STATUS", json);
    }
}
