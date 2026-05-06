package io.tacticl.business.telegram.command;

import io.tacticl.business.telegram.audit.TelegramAuditLogger;
import io.tacticl.business.telegram.identity.TelegramIdentityResolver;
import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.business.telegram.router.CommandContext;
import io.tacticl.business.telegram.router.CommandHandler;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import io.tacticl.data.telegram.entity.MemberRole;
import io.tacticl.data.telegram.entity.TelegramMemberGrant;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import io.tacticl.data.telegram.repository.TelegramMemberGrantRepository;
import io.tacticl.data.telegram.repository.TelegramProjectLinkRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Handles {@code /projects} (DM-only): lists every active Tacticl project the
 * sender belongs to, alongside their effective {@link MemberRole} for each.
 *
 * <p>Combines:
 * <ul>
 *   <li>{@link TelegramProjectLink}s where the sender is the {@code ownerUserId}
 *       (implicit OWNER role)</li>
 *   <li>{@link TelegramMemberGrant}s for the sender (explicit role rows)</li>
 * </ul>
 *
 * <p>Stale grant rows whose project link is archived are silently dropped to
 * avoid surfacing internally-inconsistent state to end users.
 */
@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class ProjectsCommand implements CommandHandler {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final TelegramIdentityResolver identity;
    private final TelegramProjectLinkRepository projectRepo;
    private final TelegramMemberGrantRepository grantRepo;
    private final TelegramOutboundQueue outbound;
    private final TelegramAuditLogger auditLogger;

    public ProjectsCommand(TelegramIdentityResolver identity,
                           TelegramProjectLinkRepository projectRepo,
                           TelegramMemberGrantRepository grantRepo,
                           TelegramOutboundQueue outbound,
                           TelegramAuditLogger auditLogger) {
        this.identity = identity;
        this.projectRepo = projectRepo;
        this.grantRepo = grantRepo;
        this.outbound = outbound;
        this.auditLogger = auditLogger;
    }

    @Override
    public String commandName() {
        return "/projects";
    }

    @Override
    public Scope scope() {
        return Scope.DM;
    }

    @Override
    public String description() {
        return "List Tacticl projects you belong to";
    }

    @Override
    public void handle(CommandContext ctx) {
        long chatId = ctx.chatId();

        Optional<String> tacticlUserIdOpt = identity.resolveByChatId(ctx.telegramUserId());
        if (tacticlUserIdOpt.isEmpty()) {
            reply(chatId,
                    "You're not linked. Open your dashboard → Settings → Integrations → Telegram.");
            audit(ctx, null, Map.of("rejected", "unlinked_sender"));
            return;
        }
        String tacticlUserId = tacticlUserIdOpt.get();

        // LinkedHashMap preserves insertion order (owned first, then granted) so the
        // output is stable across runs; key on projectId for de-duping.
        LinkedHashMap<String, ProjectRow> rows = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();

        for (TelegramProjectLink owned : projectRepo.findByOwnerUserIdAndIsActiveTrue(tacticlUserId)) {
            rows.put(owned.getProjectId(), new ProjectRow(owned.getGroupTitle(), MemberRole.OWNER));
            seen.add(owned.getProjectId());
        }

        for (TelegramMemberGrant grant : grantRepo.findByTacticlUserIdAndIsActiveTrue(tacticlUserId)) {
            String projectId = grant.getProjectId();
            if (seen.contains(projectId)) {
                continue;  // OWNER outranks any grant; already listed.
            }
            // WHY: a grant whose project link no longer exists (or is archived) is
            // an internal-state oddity, not user-facing — drop silently.
            Optional<TelegramProjectLink> link = projectRepo.findByProjectIdAndIsActiveTrue(projectId);
            if (link.isEmpty()) {
                continue;
            }
            rows.put(projectId, new ProjectRow(link.get().getGroupTitle(), grant.getRole()));
        }

        if (rows.isEmpty()) {
            reply(chatId, "You're not in any Tacticl projects yet.");
            audit(ctx, tacticlUserId, Map.of("count", 0));
            return;
        }

        StringBuilder sb = new StringBuilder("Your projects\n\n");
        for (ProjectRow row : rows.values()) {
            sb.append("• ").append(row.title()).append(" — ").append(row.role().name()).append('\n');
        }
        reply(chatId, sb.toString().stripTrailing());
        audit(ctx, tacticlUserId, Map.of("count", rows.size()));
    }

    private record ProjectRow(String title, MemberRole role) {}

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
        auditLogger.record(ctx.chatId(), ctx.telegramUserId(), tacticlUserId, "PROJECTS", json);
    }
}
