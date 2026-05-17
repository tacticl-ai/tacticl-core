package io.tacticl.business.telegram.command;

import io.tacticl.business.telegram.conversation.TelegramConversationAdapter;
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

import java.util.Optional;

/**
 * Handles the {@code /spark} slash-command: starts a conversational turn from a
 * group message. Pre-flight validation (linked account, project exists,
 * non-empty text) happens here; once accepted, delegation to
 * {@link TelegramConversationAdapter} is total — the adapter owns all
 * user-facing replies (permission denial, gather/propose/align prompts,
 * pipeline-started notice, etc.) so this handler stays silent on the happy
 * path.
 */
@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class SparkCommand implements CommandHandler {

    private final TelegramIdentityResolver identity;
    private final TelegramProjectLinkRepository projectRepo;
    private final TelegramConversationAdapter adapter;
    private final TelegramOutboundQueue outbound;

    public SparkCommand(TelegramIdentityResolver identity,
                        TelegramProjectLinkRepository projectRepo,
                        TelegramConversationAdapter adapter,
                        TelegramOutboundQueue outbound) {
        this.identity = identity;
        this.projectRepo = projectRepo;
        this.adapter = adapter;
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
    public String description() {
        return "Start a project spark";
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

        adapter.handle(chatId, tacticlUserId.get(), text, linkOpt.get());
    }

    private void reply(long chatId, String text) {
        outbound.enqueue(chatId, new OutboundMessage(SendMessageRequest.plain(chatId, text)));
    }
}
