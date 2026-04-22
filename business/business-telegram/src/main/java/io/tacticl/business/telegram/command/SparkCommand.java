package io.tacticl.business.telegram.command;

import io.tacticl.business.telegram.identity.TelegramIdentityResolver;
import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.business.telegram.router.CommandContext;
import io.tacticl.business.telegram.router.CommandHandler;
import io.tacticl.business.telegram.spark.TelegramSparkInitiator;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import io.tacticl.data.telegram.repository.TelegramProjectLinkRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Handles the {@code /spark} slash-command: starts a pipeline from a group
 * message. Pre-flight validation (linked account, project exists, non-empty
 * text) happens here; once accepted, delegation to
 * {@link TelegramSparkInitiator} is total — the initiator owns all user-facing
 * replies (permission denial, started, pipeline disabled, etc.) so this handler
 * stays silent on the happy path.
 */
@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class SparkCommand implements CommandHandler {

    private final TelegramIdentityResolver identity;
    private final TelegramProjectLinkRepository projectRepo;
    private final TelegramSparkInitiator initiator;
    private final TelegramOutboundQueue outbound;

    public SparkCommand(TelegramIdentityResolver identity,
                        TelegramProjectLinkRepository projectRepo,
                        TelegramSparkInitiator initiator,
                        TelegramOutboundQueue outbound) {
        this.identity = identity;
        this.projectRepo = projectRepo;
        this.initiator = initiator;
        this.outbound = outbound;
    }

    @Override
    public String commandName() {
        return "/spark";
    }

    @Override
    public Scope scope() {
        return Scope.GROUP;
    }

    @Override
    public void handle(CommandContext ctx) {
        long chatId = ctx.chatId();

        Optional<String> tacticlUserId = identity.resolveByChatId(ctx.telegramUserId());
        if (tacticlUserId.isEmpty()) {
            reply(chatId, "You must link your Tacticl account first.");
            return;
        }

        Optional<TelegramProjectLink> linkOpt = projectRepo.findByChatIdAndIsActiveTrue(chatId);
        if (linkOpt.isEmpty()) {
            reply(chatId, "No active project in this group. Use /init first.");
            return;
        }

        String text = ctx.argsAfterCommand();
        if (text.isBlank()) {
            reply(chatId, "Usage: /spark <what to do>");
            return;
        }

        // repoUrl is null: no repo-mapping service exists yet (Phase 2 deferral).
        initiator.initiate(chatId, tacticlUserId.get(), text, linkOpt.get(), null);
    }

    private void reply(long chatId, String text) {
        outbound.enqueue(chatId, new OutboundMessage(SendMessageRequest.plain(chatId, text)));
    }
}
