package io.tacticl.business.telegram.command;

import io.tacticl.business.telegram.audit.TelegramAuditLogger;
import io.tacticl.business.telegram.identity.TelegramIdentityResolver;
import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.business.telegram.permission.MemberPermissionService;
import io.tacticl.business.telegram.router.CommandContext;
import io.tacticl.business.telegram.router.CommandHandler;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import io.tacticl.data.telegram.entity.MemberRole;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Optional;

/**
 * Handles {@code /help}: renders the slash-commands visible to the sender's
 * current {@link MemberRole}. Senders without a linked Tacticl account are
 * shown the link prompt instead of a command list.
 */
@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class HelpCommand implements CommandHandler {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final TelegramIdentityResolver identity;
    private final MemberPermissionService permissions;
    private final TelegramOutboundQueue outbound;
    private final TelegramAuditLogger auditLogger;

    public HelpCommand(TelegramIdentityResolver identity,
                       MemberPermissionService permissions,
                       TelegramOutboundQueue outbound,
                       TelegramAuditLogger auditLogger) {
        this.identity = identity;
        this.permissions = permissions;
        this.outbound = outbound;
        this.auditLogger = auditLogger;
    }

    @Override
    public String commandName() {
        return "/help";
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
            audit(ctx, null, Map.of("rejected", "unlinked_sender"));
            return;
        }

        MemberRole role = permissions.findRole(chatId, senderTacticlUserId.get()).orElse(MemberRole.OBSERVER);

        StringBuilder sb = new StringBuilder("Available commands:\n");
        sb.append("/help — show this message\n");
        sb.append("/members — list project members\n");
        sb.append("/status — project status\n");

        if (role.atLeast(MemberRole.CONTRIBUTOR)) {
            // /spark lands in Chunk 7; include here for discoverability.
            sb.append("/spark <text> — start a project spark\n");
        }
        if (role.atLeast(MemberRole.ADMIN)) {
            sb.append("/grant @user <role> — assign a role\n");
            sb.append("/revoke @user — clear a role grant\n");
        }
        if (role.atLeast(MemberRole.OWNER)) {
            sb.append("/transfer @user — transfer ownership\n");
            sb.append("/archive — archive this project\n");
            sb.append("/leave — archive and make me leave\n");
        }

        reply(chatId, sb.toString().stripTrailing());
        audit(ctx, senderTacticlUserId.get(), Map.of("role", role.name()));
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
        auditLogger.record(ctx.chatId(), ctx.telegramUserId(), tacticlUserId, "HELP", json);
    }
}
