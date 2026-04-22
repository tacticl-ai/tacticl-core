package io.tacticl.business.telegram.command;

import io.tacticl.business.telegram.command.ProjectPipelineSummaryProvider.ProjectPipelineSummary;
import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.business.telegram.router.CommandContext;
import io.tacticl.business.telegram.router.CommandHandler;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import io.tacticl.data.telegram.repository.TelegramProjectLinkRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Locale;
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

    private final TelegramProjectLinkRepository projectRepo;
    private final Optional<ProjectPipelineSummaryProvider> summaryProvider;
    private final TelegramOutboundQueue outbound;

    public StatusCommand(TelegramProjectLinkRepository projectRepo,
                         Optional<ProjectPipelineSummaryProvider> summaryProvider,
                         TelegramOutboundQueue outbound) {
        this.projectRepo = projectRepo;
        this.summaryProvider = summaryProvider;
        this.outbound = outbound;
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

        Optional<TelegramProjectLink> linkOpt = projectRepo.findByChatIdAndIsActiveTrue(chatId);
        if (linkOpt.isEmpty()) {
            reply(chatId, "No active project in this group.");
            return;
        }

        if (summaryProvider.isEmpty()) {
            reply(chatId, "Status reporting is not yet enabled.");
            return;
        }

        ProjectPipelineSummary summary = summaryProvider.get().summarize(linkOpt.get().getProjectId());
        if (summary == null) {
            reply(chatId, "Status reporting is not yet enabled.");
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
    }

    private void reply(long chatId, String text) {
        outbound.enqueue(chatId, new OutboundMessage(SendMessageRequest.plain(chatId, text)));
    }
}
