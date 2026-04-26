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
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import io.tacticl.data.telegram.repository.TelegramProjectLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Optional;

/**
 * Handles {@code /transfer @user}: rotates project ownership. The current
 * owner becomes ADMIN; the target is promoted by rewriting
 * {@link TelegramProjectLink#setOwnerUserId(String)}. Only the current owner
 * can invoke this.
 */
@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class TransferCommand implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(TransferCommand.class);

    private static final String USAGE = "Usage: /transfer @user";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final TelegramIdentityResolver identity;
    private final TelegramUsernameCache usernameCache;
    private final MemberPermissionService permissions;
    private final TelegramProjectLinkRepository projectRepo;
    private final TelegramOutboundQueue outbound;
    private final TelegramAuditLogger auditLogger;

    public TransferCommand(TelegramIdentityResolver identity,
                           TelegramUsernameCache usernameCache,
                           MemberPermissionService permissions,
                           TelegramProjectLinkRepository projectRepo,
                           TelegramOutboundQueue outbound,
                           TelegramAuditLogger auditLogger) {
        this.identity = identity;
        this.usernameCache = usernameCache;
        this.permissions = permissions;
        this.projectRepo = projectRepo;
        this.outbound = outbound;
        this.auditLogger = auditLogger;
    }

    @Override
    public String commandName() {
        return "/transfer";
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

        PermissionCheck check = permissions.require(chatId, senderTacticlUserId.get(), MemberRole.OWNER);
        if (!check.allowed()) {
            reply(chatId, "Only the project owner can transfer ownership.");
            audit(ctx, senderTacticlUserId.get(), Map.of("rejected", "not_owner"));
            return;
        }

        String args = ctx.argsAfterCommand();
        String[] tokens = args.isEmpty() ? new String[0] : args.split("\\s+");
        if (tokens.length != 1) {
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

        if (targetTacticlUserId.equals(senderTacticlUserId.get())) {
            reply(chatId, "You already own this project.");
            audit(ctx, senderTacticlUserId.get(),
                    Map.of("rejected", "self_transfer", "target", "@" + usernameRaw));
            return;
        }

        // Defensive: could become empty if the project link is deleted concurrently
        // between the permission check above and this read.
        Optional<TelegramProjectLink> linkOpt = projectRepo.findByChatIdAndIsActiveTrue(chatId);
        if (linkOpt.isEmpty()) {
            reply(chatId, "No active project in this group.");
            audit(ctx, senderTacticlUserId.get(), Map.of("rejected", "no_project"));
            return;
        }
        TelegramProjectLink link = linkOpt.get();

        String formerOwnerTacticlUserId = link.getOwnerUserId();
        long formerOwnerTelegramUserId = ctx.telegramUserId();

        // Mongo has no cross-doc txn here. Order is chosen so a mid-sequence crash
        // leaves the safest partial state: save commits the ownership swap
        // (pivotal) so authority transfers atomically, revoke clears any stale
        // grant on the incoming owner (idempotent), grant then records the
        // former owner's ADMIN role.
        link.setOwnerUserId(targetTacticlUserId);
        projectRepo.save(link);

        permissions.revoke(chatId, targetTacticlUserId);

        permissions.grant(chatId, formerOwnerTacticlUserId, formerOwnerTelegramUserId,
                MemberRole.ADMIN, formerOwnerTacticlUserId);

        logger.info("Transferred ownership of chat {} from {} to {} (tg {})",
                chatId, formerOwnerTacticlUserId, targetTacticlUserId, targetTelegramUserId);

        reply(chatId, "✅ Ownership transferred to @" + usernameRaw + ".");
        audit(ctx, senderTacticlUserId.get(),
                Map.of("target", "@" + usernameRaw,
                        "targetTacticlUserId", targetTacticlUserId,
                        "formerOwnerTacticlUserId", formerOwnerTacticlUserId,
                        "projectId", link.getProjectId()));
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
        auditLogger.record(ctx.chatId(), ctx.telegramUserId(), tacticlUserId, "TRANSFER", json);
    }
}
