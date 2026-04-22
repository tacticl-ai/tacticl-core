package io.tacticl.business.telegram.command;

import io.tacticl.business.telegram.identity.TelegramIdentityResolver;
import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.business.telegram.router.CommandContext;
import io.tacticl.business.telegram.router.CommandHandler;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import io.tacticl.data.telegram.repository.TelegramProjectLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Handles the {@code /init} slash-command: claims the current Telegram group
 * as a Tacticl project for the linked sender.
 *
 * <p>Behaviour:
 * <ol>
 *   <li>If the sender is not linked to a Tacticl user → DM them a link prompt
 *       and abort (no project is created).</li>
 *   <li>If the group already has an active {@link TelegramProjectLink} → reply
 *       in the group with the existing project id and abort.</li>
 *   <li>Otherwise persist a new link and post a welcome message citing the
 *       owner and the available command set.</li>
 * </ol>
 *
 * <p>V1 generates a random UUID as the project id. A dedicated Project entity
 * is tracked in a later chunk of the Telegram integration plan.
 */
@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class InitCommand implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(InitCommand.class);

    private static final String LINK_PROMPT =
            "Link your Tacticl account first: https://tacticl.ai/telegram/link";

    private final TelegramIdentityResolver identity;
    private final TelegramProjectLinkRepository projectRepo;
    private final TelegramOutboundQueue outbound;

    public InitCommand(TelegramIdentityResolver identity,
                       TelegramProjectLinkRepository projectRepo,
                       TelegramOutboundQueue outbound) {
        this.identity = identity;
        this.projectRepo = projectRepo;
        this.outbound = outbound;
    }

    @Override
    public String commandName() {
        return "/init";
    }

    @Override
    public Scope scope() {
        return Scope.GROUP;
    }

    @Override
    public void handle(CommandContext ctx) {
        Optional<String> tacticlUserId = identity.resolveByChatId(ctx.telegramUserId());
        if (tacticlUserId.isEmpty()) {
            // DM the user their link instructions — chat_id == telegram user id
            // for private chats with the bot.
            outbound.enqueue(ctx.telegramUserId(), new OutboundMessage(
                    SendMessageRequest.plain(ctx.telegramUserId(), LINK_PROMPT)));
            return;
        }

        Optional<TelegramProjectLink> existing =
                projectRepo.findByChatIdAndIsActiveTrue(ctx.chatId());
        if (existing.isPresent()) {
            outbound.enqueue(ctx.chatId(), new OutboundMessage(
                    SendMessageRequest.plain(ctx.chatId(),
                            "Already linked to project " + existing.get().getProjectId())));
            return;
        }

        String projectId = UUID.randomUUID().toString();
        String title = Optional.ofNullable(ctx.raw().chat().title()).orElse("Untitled group");
        TelegramProjectLink link = TelegramProjectLink.create(
                projectId, ctx.chatId(), tacticlUserId.get(), title);
        projectRepo.save(link);
        logger.info("Project {} claimed by {} for chat {}",
                projectId, tacticlUserId.get(), ctx.chatId());

        String welcome = String.format(
                "Project created by @%s.%n"
                        + "Owner: %s%n"
                        + "Commands: /grant /revoke /transfer /members /status /help",
                Optional.ofNullable(ctx.senderUsername()).orElse("unknown"),
                tacticlUserId.get());
        outbound.enqueue(ctx.chatId(), new OutboundMessage(
                SendMessageRequest.plain(ctx.chatId(), welcome)));
    }
}
