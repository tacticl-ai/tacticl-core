package io.tacticl.business.telegram.command;

import io.tacticl.business.telegram.identity.TelegramIdentityResolver;
import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.business.telegram.permission.MemberPermissionService;
import io.tacticl.business.telegram.permission.PermissionCheck;
import io.tacticl.business.telegram.router.CommandContext;
import io.tacticl.business.telegram.router.CommandHandler;
import io.tacticl.client.telegram.TelegramBotClient;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import io.tacticl.data.telegram.entity.MemberRole;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import io.tacticl.data.telegram.repository.TelegramProjectLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Handles {@code /leave}: owner/admin-initiated archive + {@code leaveChat}.
 * The farewell message is enqueued before the bot leaves so the outbound
 * worker still has a chance to flush it.
 */
@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class LeaveCommand implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(LeaveCommand.class);

    private final TelegramIdentityResolver identity;
    private final MemberPermissionService permissions;
    private final TelegramProjectLinkRepository projectRepo;
    private final TelegramOutboundQueue outbound;
    private final TelegramBotClient bot;

    public LeaveCommand(TelegramIdentityResolver identity,
                        MemberPermissionService permissions,
                        TelegramProjectLinkRepository projectRepo,
                        TelegramOutboundQueue outbound,
                        TelegramBotClient bot) {
        this.identity = identity;
        this.permissions = permissions;
        this.projectRepo = projectRepo;
        this.outbound = outbound;
        this.bot = bot;
    }

    @Override
    public String commandName() {
        return "/leave";
    }

    @Override
    public Scope scope() {
        return Scope.GROUP;
    }

    @Override
    public void handle(CommandContext ctx) {
        long chatId = ctx.chatId();

        Optional<String> senderTacticlUserId = identity.resolveByChatId(ctx.telegramUserId());
        if (senderTacticlUserId.isEmpty()) {
            reply(chatId, "You must link your Tacticl account first.");
            return;
        }

        PermissionCheck check = permissions.require(chatId, senderTacticlUserId.get(), MemberRole.ADMIN);
        if (!check.allowed()) {
            reply(chatId, "Only owners or admins can make me leave.");
            return;
        }

        Optional<TelegramProjectLink> linkOpt = projectRepo.findByChatIdAndIsActiveTrue(chatId);
        if (linkOpt.isEmpty()) {
            reply(chatId, "No active project in this group.");
            return;
        }
        TelegramProjectLink link = linkOpt.get();

        link.archive();
        projectRepo.save(link);

        reply(chatId, "\uD83D\uDC4B Leaving the group. Bye!");

        try {
            bot.leaveChat(chatId);
        } catch (RuntimeException e) {
            logger.warn("leaveChat failed for chat {}: {}", chatId, e.toString());
        }

        logger.info("Archived and left chat {} (project {}) by {}",
                chatId, link.getProjectId(), senderTacticlUserId.get());
    }

    private void reply(long chatId, String text) {
        outbound.enqueue(chatId, new OutboundMessage(SendMessageRequest.plain(chatId, text)));
    }
}
