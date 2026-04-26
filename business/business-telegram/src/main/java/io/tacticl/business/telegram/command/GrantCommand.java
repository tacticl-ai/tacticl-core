package io.tacticl.business.telegram.command;

import io.tacticl.business.telegram.audit.TelegramAuditLogger;
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
import tools.jackson.databind.json.JsonMapper;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Handles the {@code /grant @user <role>} slash-command: assigns a
 * {@link MemberRole} (observer|contributor|runner|admin) to another member of
 * the current Telegram group.
 *
 * <p>Prerequisites enforced in order:
 * <ol>
 *   <li>Sender must be linked to a Tacticl account.</li>
 *   <li>Sender must have at least {@link MemberRole#ADMIN} in this group.</li>
 *   <li>Args must parse as {@code @username <role>}.</li>
 *   <li>Target username must have been seen speaking in this group
 *       (cached by {@link TelegramUsernameCache}).</li>
 *   <li>Target must also be linked to a Tacticl account.</li>
 * </ol>
 */
@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class GrantCommand implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(GrantCommand.class);

    private static final String USAGE =
            "Usage: /grant @user <observer|contributor|runner|admin>";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final TelegramIdentityResolver identity;
    private final TelegramUsernameCache usernameCache;
    private final MemberPermissionService permissions;
    private final TelegramOutboundQueue outbound;
    private final TelegramAuditLogger auditLogger;

    public GrantCommand(TelegramIdentityResolver identity,
                        TelegramUsernameCache usernameCache,
                        MemberPermissionService permissions,
                        TelegramOutboundQueue outbound,
                        TelegramAuditLogger auditLogger) {
        this.identity = identity;
        this.usernameCache = usernameCache;
        this.permissions = permissions;
        this.outbound = outbound;
        this.auditLogger = auditLogger;
    }

    @Override
    public String commandName() {
        return "/grant";
    }

    @Override
    public Scope scope() {
        return Scope.GROUP;
    }

    @Override
    public String description() {
        return "Grant a role to a member";
    }

    @Override
    public void handle(CommandContext ctx) {
        long chatId = ctx.chatId();

        Optional<String> senderTacticlUserId = identity.resolveByChatId(ctx.telegramUserId());
        if (senderTacticlUserId.isEmpty()) {
            reply(chatId, "You must link your Tacticl account first.");
            audit(ctx, null, Map.of("rejected", "unlinked_sender"));
            return;
        }

        PermissionCheck check = permissions.require(chatId, senderTacticlUserId.get(), MemberRole.ADMIN);
        if (!check.allowed()) {
            reply(chatId, "You need admin role to grant.");
            audit(ctx, senderTacticlUserId.get(), Map.of("rejected", "insufficient_role"));
            return;
        }

        String args = ctx.argsAfterCommand();
        String[] tokens = args.isEmpty() ? new String[0] : args.split("\\s+");
        if (tokens.length != 2) {
            reply(chatId, USAGE);
            audit(ctx, senderTacticlUserId.get(), Map.of("rejected", "usage", "args", args));
            return;
        }

        String usernameRaw = tokens[0];
        if (usernameRaw.startsWith("@")) {
            usernameRaw = usernameRaw.substring(1);
        }
        if (usernameRaw.isBlank()) {
            reply(chatId, USAGE);
            audit(ctx, senderTacticlUserId.get(), Map.of("rejected", "usage", "args", args));
            return;
        }

        Optional<MemberRole> roleOpt = parseRole(tokens[1]);
        if (roleOpt.isEmpty()) {
            reply(chatId, USAGE);
            audit(ctx, senderTacticlUserId.get(),
                    Map.of("rejected", "invalid_role", "target", "@" + usernameRaw, "raw", tokens[1]));
            return;
        }
        MemberRole role = roleOpt.get();

        Optional<Long> targetTelegramIdOpt = usernameCache.lookup(chatId, usernameRaw);
        if (targetTelegramIdOpt.isEmpty()) {
            reply(chatId,
                    "I haven't seen @" + usernameRaw + " speak in this group yet; ask them to say hi first.");
            audit(ctx, senderTacticlUserId.get(),
                    Map.of("rejected", "unknown_username", "target", "@" + usernameRaw));
            return;
        }
        long targetTelegramUserId = targetTelegramIdOpt.get();

        Optional<String> targetTacticlUserIdOpt = identity.resolveByChatId(targetTelegramUserId);
        if (targetTacticlUserIdOpt.isEmpty()) {
            reply(chatId, "@" + usernameRaw + " must link their Tacticl account first.");
            audit(ctx, senderTacticlUserId.get(),
                    Map.of("rejected", "target_unlinked", "target", "@" + usernameRaw));
            return;
        }
        String targetTacticlUserId = targetTacticlUserIdOpt.get();

        permissions.grant(chatId, targetTacticlUserId, targetTelegramUserId, role, senderTacticlUserId.get());
        logger.info("Granted role {} to tacticlUser {} (tg {}) in chat {} by {}",
                role, targetTacticlUserId, targetTelegramUserId, chatId, senderTacticlUserId.get());

        reply(chatId, "✅ @" + usernameRaw + " is now " + role.name() + ".");
        audit(ctx, senderTacticlUserId.get(),
                Map.of("target", "@" + usernameRaw, "role", role.name(),
                        "targetTacticlUserId", targetTacticlUserId));
    }

    private void audit(CommandContext ctx, String tacticlUserId, Map<String, ?> payload) {
        String json;
        try {
            json = MAPPER.writeValueAsString(payload);
        } catch (RuntimeException e) {
            json = null;
        }
        auditLogger.record(ctx.chatId(), ctx.telegramUserId(), tacticlUserId, "GRANT", json);
    }

    private void reply(long chatId, String text) {
        outbound.enqueue(chatId, new OutboundMessage(SendMessageRequest.plain(chatId, text)));
    }

    private Optional<MemberRole> parseRole(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        // Only allow the documented set — reject OWNER (not grantable via /grant).
        if (!(normalized.equals("OBSERVER")
                || normalized.equals("CONTRIBUTOR")
                || normalized.equals("RUNNER")
                || normalized.equals("ADMIN"))) {
            return Optional.empty();
        }
        try {
            return Optional.of(MemberRole.valueOf(normalized));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
