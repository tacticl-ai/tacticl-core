package io.tacticl.business.telegram.command;

import io.tacticl.business.telegram.identity.TelegramIdentityResolver;
import io.tacticl.business.telegram.identity.TelegramUsernameCache;
import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.business.telegram.permission.MemberPermissionService;
import io.tacticl.business.telegram.permission.PermissionCheck;
import io.tacticl.business.telegram.router.CommandContext;
import io.tacticl.business.telegram.router.CommandHandler;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import io.tacticl.data.telegram.entity.MemberRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Handles {@code /revoke @user}: clears a member's role grant (downgrading them
 * back to implicit {@link MemberRole#OBSERVER}). Refuses to touch the project
 * owner — ownership is rotated via {@code /transfer}, not revoked.
 */
@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class RevokeCommand implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(RevokeCommand.class);

    private static final String USAGE = "Usage: /revoke @user";

    private final TelegramIdentityResolver identity;
    private final TelegramUsernameCache usernameCache;
    private final MemberPermissionService permissions;
    private final TelegramOutboundQueue outbound;

    public RevokeCommand(TelegramIdentityResolver identity,
                         TelegramUsernameCache usernameCache,
                         MemberPermissionService permissions,
                         TelegramOutboundQueue outbound) {
        this.identity = identity;
        this.usernameCache = usernameCache;
        this.permissions = permissions;
        this.outbound = outbound;
    }

    @Override
    public String commandName() {
        return "/revoke";
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
            reply(chatId, "You need admin role to revoke.");
            return;
        }

        String args = ctx.argsAfterCommand();
        String[] tokens = args.isEmpty() ? new String[0] : args.split("\\s+");
        if (tokens.length != 1) {
            reply(chatId, USAGE);
            return;
        }

        String usernameRaw = tokens[0];
        if (usernameRaw.startsWith("@")) {
            usernameRaw = usernameRaw.substring(1);
        }
        if (usernameRaw.isBlank()) {
            reply(chatId, USAGE);
            return;
        }

        Optional<Long> targetTelegramIdOpt = usernameCache.lookup(chatId, usernameRaw);
        if (targetTelegramIdOpt.isEmpty()) {
            reply(chatId,
                    "I haven't seen @" + usernameRaw + " speak in this group yet; ask them to say hi first.");
            return;
        }
        long targetTelegramUserId = targetTelegramIdOpt.get();

        Optional<String> targetTacticlUserIdOpt = identity.resolveByChatId(targetTelegramUserId);
        if (targetTacticlUserIdOpt.isEmpty()) {
            reply(chatId, "@" + usernameRaw + " must link their Tacticl account first.");
            return;
        }
        String targetTacticlUserId = targetTacticlUserIdOpt.get();

        // Ownership is modelled on TelegramProjectLink, not the grants table;
        // /revoke would silently no-op on an owner, so surface the error explicitly.
        if (permissions.findRole(chatId, targetTacticlUserId).orElse(MemberRole.OBSERVER) == MemberRole.OWNER) {
            reply(chatId, "Cannot revoke the project owner.");
            return;
        }

        permissions.revoke(chatId, targetTacticlUserId);
        logger.info("Revoked grant for tacticlUser {} (tg {}) in chat {} by {}",
                targetTacticlUserId, targetTelegramUserId, chatId, senderTacticlUserId.get());

        reply(chatId, "✅ @" + usernameRaw + "'s grant revoked.");
    }

    private void reply(long chatId, String text) {
        outbound.enqueue(chatId, new OutboundMessage(SendMessageRequest.plain(chatId, text)));
    }
}
