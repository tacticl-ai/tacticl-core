package io.tacticl.business.telegram.command;

import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.business.telegram.permission.MemberPermissionService;
import io.tacticl.business.telegram.router.CommandContext;
import io.tacticl.business.telegram.router.CommandHandler;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import io.tacticl.data.telegram.entity.MemberRole;
import io.tacticl.data.telegram.entity.TelegramMemberGrant;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import io.tacticl.data.telegram.repository.TelegramProjectLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Handles {@code /members}: read-only listing of everyone with a role in the
 * current group's project. Anyone can invoke. Output is truncated at
 * {@value #MAX_ROWS} grant rows to stay well under Telegram's 4096-char limit.
 */
@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class MembersCommand implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(MembersCommand.class);

    private static final int MAX_ROWS = 50;

    private final MemberPermissionService permissions;
    private final TelegramProjectLinkRepository projectRepo;
    private final TelegramOutboundQueue outbound;

    public MembersCommand(MemberPermissionService permissions,
                          TelegramProjectLinkRepository projectRepo,
                          TelegramOutboundQueue outbound) {
        this.permissions = permissions;
        this.projectRepo = projectRepo;
        this.outbound = outbound;
    }

    @Override
    public String commandName() {
        return "/members";
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
        TelegramProjectLink link = linkOpt.get();

        List<TelegramMemberGrant> grants = permissions.listGrants(link.getProjectId());

        StringBuilder sb = new StringBuilder();
        sb.append("*Project members*\n");
        sb.append("• ").append(MemberRole.OWNER.name()).append(" — ").append(link.getOwnerUserId()).append('\n');

        int shown = Math.min(grants.size(), MAX_ROWS);
        for (int i = 0; i < shown; i++) {
            TelegramMemberGrant g = grants.get(i);
            sb.append("• ").append(g.getRole().name()).append(" — ").append(g.getTacticlUserId()).append('\n');
        }
        if (grants.size() > MAX_ROWS) {
            sb.append("…and ").append(grants.size() - MAX_ROWS).append(" more");
        }

        logger.debug("Listing {} grants + owner for project {}", grants.size(), link.getProjectId());
        reply(chatId, sb.toString());
    }

    private void reply(long chatId, String text) {
        outbound.enqueue(chatId, new OutboundMessage(SendMessageRequest.plain(chatId, text)));
    }
}
