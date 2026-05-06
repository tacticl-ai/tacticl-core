package io.tacticl.business.telegram.command;

import io.tacticl.business.telegram.audit.TelegramAuditLogger;
import io.tacticl.business.telegram.identity.TelegramIdentityResolver;
import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.business.telegram.router.CommandContext;
import io.tacticl.business.telegram.router.CommandHandler;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import io.tacticl.data.profile.entity.UserProfile;
import io.tacticl.data.profile.repository.UserProfileRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Handles {@code /whoami} (DM-only): tells the sender which Tacticl account
 * their Telegram identity is linked to. Used as a self-service sanity check
 * before {@code /unlink} or while debugging onboarding.
 *
 * <p>Email is sourced from {@link UserProfile} when available; otherwise the
 * raw Tacticl user id is shown so support can still cross-reference the link.
 */
@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class WhoamiCommand implements CommandHandler {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final TelegramIdentityResolver identity;
    private final UserProfileRepository profileRepo;
    private final TelegramOutboundQueue outbound;
    private final TelegramAuditLogger auditLogger;

    public WhoamiCommand(TelegramIdentityResolver identity,
                         UserProfileRepository profileRepo,
                         TelegramOutboundQueue outbound,
                         TelegramAuditLogger auditLogger) {
        this.identity = identity;
        this.profileRepo = profileRepo;
        this.outbound = outbound;
        this.auditLogger = auditLogger;
    }

    @Override
    public String commandName() {
        return "/whoami";
    }

    @Override
    public Scope scope() {
        return Scope.DM;
    }

    @Override
    public String description() {
        return "Show the Tacticl account linked to your Telegram identity";
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
        String handle = handleFor(ctx);
        Optional<UserProfile> profile = profileRepo.findByCidadelUserIdAndIsActiveTrue(tacticlUserId);

        // WHY: prefer email when we have a profile row; fall back to user id so
        // operators always have something to cross-reference even if profile
        // bootstrapping hasn't run yet for this account.
        String detail = profile.map(UserProfile::getEmail).filter(s -> s != null && !s.isBlank())
                .orElse(tacticlUserId);

        reply(chatId, "Linked as " + handle + " (" + detail + ")");
        Map<String, Object> payload = new HashMap<>();
        payload.put("hasProfile", profile.isPresent());
        audit(ctx, tacticlUserId, payload);
    }

    private static String handleFor(CommandContext ctx) {
        String username = ctx.senderUsername();
        return (username == null || username.isBlank()) ? "your Telegram account" : "@" + username;
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
        auditLogger.record(ctx.chatId(), ctx.telegramUserId(), tacticlUserId, "WHOAMI", json);
    }
}
