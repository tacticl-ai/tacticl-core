package io.tacticl.business.telegram.command;

import io.tacticl.business.telegram.identity.TelegramIdentityResolver;
import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.business.telegram.permission.MemberPermissionService;
import io.tacticl.business.telegram.permission.PermissionCheck;
import io.tacticl.business.telegram.router.CommandContext;
import io.tacticl.business.telegram.router.CommandHandler;
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
 * Handles {@code /archive}: owner-only soft-archive of the group's project
 * link. The bot stays in the group; a subsequent {@code /init} can reactivate.
 */
@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class ArchiveCommand implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveCommand.class);

    private final TelegramIdentityResolver identity;
    private final MemberPermissionService permissions;
    private final TelegramProjectLinkRepository projectRepo;
    private final TelegramOutboundQueue outbound;

    public ArchiveCommand(TelegramIdentityResolver identity,
                          MemberPermissionService permissions,
                          TelegramProjectLinkRepository projectRepo,
                          TelegramOutboundQueue outbound) {
        this.identity = identity;
        this.permissions = permissions;
        this.projectRepo = projectRepo;
        this.outbound = outbound;
    }

    @Override
    public String commandName() {
        return "/archive";
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

        PermissionCheck check = permissions.require(chatId, senderTacticlUserId.get(), MemberRole.OWNER);
        if (!check.allowed()) {
            reply(chatId, "Only the project owner can archive.");
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
        // TODO(Task 24): cancel active sparks by projectId once Spark.projectId lands.

        logger.info("Archived project {} in chat {} by {}",
                link.getProjectId(), chatId, senderTacticlUserId.get());

        reply(chatId,
                "\uD83D\uDCE6 Project archived. I'll stay in the group but won't run sparks until someone sends /init again.");
    }

    private void reply(long chatId, String text) {
        outbound.enqueue(chatId, new OutboundMessage(SendMessageRequest.plain(chatId, text)));
    }
}
