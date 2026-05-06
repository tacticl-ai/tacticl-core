package io.tacticl.business.telegram.command;

import io.tacticl.business.telegram.TelegramUserLinker;
import io.tacticl.business.telegram.audit.TelegramAuditLogger;
import io.tacticl.business.telegram.identity.TelegramIdentityResolver;
import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.business.telegram.router.CommandContext;
import io.tacticl.business.telegram.router.CommandHandler;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import io.tacticl.data.telegram.entity.TelegramLink;
import io.tacticl.data.telegram.entity.TelegramMemberGrant;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import io.tacticl.data.telegram.repository.TelegramLinkRepository;
import io.tacticl.data.telegram.repository.TelegramMemberGrantRepository;
import io.tacticl.data.telegram.repository.TelegramProjectLinkRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Handles {@code /unlink} (DM-only): fully detaches a Tacticl account from
 * Telegram. Steps:
 * <ol>
 *   <li>If the sender is not currently linked, reply and exit.</li>
 *   <li>If the sender owns any active project, refuse and list the projects —
 *       ownership must be transferred ({@code /transfer}) or the project
 *       archived ({@code /archive}) before the user can unlink.</li>
 *   <li>Otherwise: soft-delete every {@link TelegramMemberGrant} for the user,
 *       call {@link TelegramUserLinker#unlink(String, long)} for every active
 *       {@link TelegramLink}, and confirm.</li>
 * </ol>
 *
 * <p>Owner-orphan guard rationale: ownership is modelled on
 * {@link TelegramProjectLink#getOwnerUserId()}, never duplicated on a grant
 * row, so any actively-owned project would be left orphaned by an unlink.
 */
@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class UnlinkCommand implements CommandHandler {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final TelegramIdentityResolver identity;
    private final TelegramProjectLinkRepository projectRepo;
    private final TelegramMemberGrantRepository grantRepo;
    private final TelegramLinkRepository linkRepo;
    private final TelegramUserLinker userLinker;
    private final TelegramOutboundQueue outbound;
    private final TelegramAuditLogger auditLogger;

    public UnlinkCommand(TelegramIdentityResolver identity,
                         TelegramProjectLinkRepository projectRepo,
                         TelegramMemberGrantRepository grantRepo,
                         TelegramLinkRepository linkRepo,
                         TelegramUserLinker userLinker,
                         TelegramOutboundQueue outbound,
                         TelegramAuditLogger auditLogger) {
        this.identity = identity;
        this.projectRepo = projectRepo;
        this.grantRepo = grantRepo;
        this.linkRepo = linkRepo;
        this.userLinker = userLinker;
        this.outbound = outbound;
        this.auditLogger = auditLogger;
    }

    @Override
    public String commandName() {
        return "/unlink";
    }

    @Override
    public Scope scope() {
        return Scope.DM;
    }

    @Override
    public String description() {
        return "Disconnect your Tacticl account from Telegram";
    }

    @Override
    public void handle(CommandContext ctx) {
        long chatId = ctx.chatId();

        Optional<String> tacticlUserIdOpt = identity.resolveByChatId(ctx.telegramUserId());
        if (tacticlUserIdOpt.isEmpty()) {
            reply(chatId, "You weren't linked.");
            audit(ctx, null, Map.of("rejected", "unlinked_sender"));
            return;
        }
        String tacticlUserId = tacticlUserIdOpt.get();

        // Owner-orphan guard: any active project owned solely by this user must
        // be transferred or archived first. OWNER is implicit on the project link
        // (no co-owner concept), so owning at all = sole owner = orphan-on-unlink.
        List<TelegramProjectLink> owned = projectRepo.findByOwnerUserIdAndIsActiveTrue(tacticlUserId);
        if (!owned.isEmpty()) {
            StringBuilder sb = new StringBuilder(
                    "Can't unlink — you're the sole owner of these projects. "
                            + "Run /transfer in the group or /archive first:\n\n");
            for (TelegramProjectLink p : owned) {
                sb.append("• ").append(p.getGroupTitle()).append('\n');
            }
            reply(chatId, sb.toString().stripTrailing());
            audit(ctx, tacticlUserId,
                    Map.of("rejected", "owns_active_projects", "count", owned.size()));
            return;
        }

        // Soft-delete every grant row for this user. Loop instead of bulk-update
        // because grants table is small per-user and this keeps the BaseMongoEntity
        // soft-delete invariant intact (delete() also touches updatedAt + isActive).
        List<TelegramMemberGrant> grants = grantRepo.findByTacticlUserIdAndIsActiveTrue(tacticlUserId);
        for (TelegramMemberGrant g : grants) {
            g.delete();
            grantRepo.save(g);
        }

        // Soft-delete every active link via the linker so any DM/forwarding
        // bookkeeping it does stays in one place.
        List<TelegramLink> links = linkRepo.findByUserIdAndIsActiveTrue(tacticlUserId);
        for (TelegramLink link : links) {
            userLinker.unlink(tacticlUserId, link.getChatId());
        }

        reply(chatId, "Unlinked. Re-link any time from Settings → Integrations → Telegram.");
        audit(ctx, tacticlUserId,
                Map.of("grants", grants.size(), "links", links.size()));
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
        auditLogger.record(ctx.chatId(), ctx.telegramUserId(), tacticlUserId, "UNLINK", json);
    }
}
