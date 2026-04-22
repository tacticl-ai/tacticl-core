package io.tacticl.business.telegram.event;

import io.tacticl.business.telegram.identity.TelegramIdentityResolver;
import io.tacticl.business.telegram.permission.MemberPermissionService;
import io.tacticl.business.telegram.permission.PermissionCheck;
import io.tacticl.business.telegram.pipeline.TelegramCheckpointResolver;
import io.tacticl.client.telegram.TelegramBotClient;
import io.tacticl.client.telegram.dto.CallbackQuery;
import io.tacticl.data.pipeline.entity.CheckpointDecision;
import io.tacticl.data.telegram.entity.MemberRole;
import io.tacticl.data.telegram.entity.ProjectStatus;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import io.tacticl.data.telegram.repository.TelegramProjectLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Handles Telegram {@code callback_query} updates triggered by inline-keyboard taps
 * on checkpoint messages. Owns parsing + authn/authz short-circuits; delegates the
 * actual checkpoint resolution to {@link TelegramCheckpointResolver}.
 */
@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class CallbackQueryHandler {

    private static final Logger logger = LoggerFactory.getLogger(CallbackQueryHandler.class);

    private static final String PREFIX = "cp:";

    private final TelegramIdentityResolver identity;
    private final MemberPermissionService permissions;
    private final TelegramCheckpointResolver checkpointResolver;
    private final TelegramProjectLinkRepository projectLinks;
    private final TelegramBotClient bot;

    public CallbackQueryHandler(TelegramIdentityResolver identity,
                                MemberPermissionService permissions,
                                TelegramCheckpointResolver checkpointResolver,
                                TelegramProjectLinkRepository projectLinks,
                                TelegramBotClient bot) {
        this.identity = identity;
        this.permissions = permissions;
        this.checkpointResolver = checkpointResolver;
        this.projectLinks = projectLinks;
        this.bot = bot;
    }

    public void handle(CallbackQuery callback) {
        if (callback == null || callback.id() == null) return;
        String callbackId = callback.id();

        Parsed parsed = parse(callback.data());
        if (parsed == null) {
            // Telegram requires exactly one answerCallbackQuery per query; nothing else to do for malformed input.
            answer(callbackId, "Invalid action");
            return;
        }

        if (callback.from() == null || callback.message() == null || callback.message().chat() == null) {
            answer(callbackId, "Invalid action");
            return;
        }

        // Answer early with a neutral toast so the button stops spinning and Telegram's 15s/30s windows don't lapse.
        answer(callbackId, "Working…");

        long telegramUserId = callback.from().id();
        long chatId = callback.message().chat().id();
        long messageId = callback.message().message_id();

        // resolveByChatId works with telegramUserId because DM chat_id == user_id (Telegram invariant).
        Optional<String> tacticlUserIdOpt = identity.resolveByChatId(telegramUserId);
        if (tacticlUserIdOpt.isEmpty()) {
            editWithError(chatId, messageId, "⚠️ Link your Tacticl account first.");
            return;
        }
        String tacticlUserId = tacticlUserIdOpt.get();

        // Archived-project gate: buttons survive /archive, bot kicks, and re-adds — must re-check link status each tap.
        Optional<TelegramProjectLink> linkOpt = projectLinks.findByChatIdAndIsActiveTrue(chatId);
        if (linkOpt.isEmpty() || linkOpt.get().getStatus() != ProjectStatus.ACTIVE) {
            editWithError(chatId, messageId, "🗄️ Project is archived or unavailable.");
            return;
        }

        PermissionCheck check = permissions.require(chatId, tacticlUserId, MemberRole.RUNNER);
        if (!check.allowed()) {
            editWithError(chatId, messageId, "🚫 Need runner permission.");
            return;
        }

        TelegramCheckpointResolver.Result result;
        try {
            result = checkpointResolver.resolve(tacticlUserId, parsed.checkpointId, parsed.action);
        } catch (RuntimeException e) {
            logger.warn("Checkpoint resolve threw for chat={} checkpoint={} action={}: {}",
                        chatId, parsed.checkpointId, parsed.action, e.getMessage());
            editWithError(chatId, messageId, "⚠️ Could not resolve checkpoint. Try again.");
            return;
        }

        if (!result.isSuccess()) {
            editWithError(chatId, messageId, "⚠️ " + result.message());
            return;
        }

        // Null markup strips the inline keyboard so the same checkpoint cannot be double-resolved from Telegram.
        try {
            bot.editMessageText(chatId, messageId, successBody(result.decision()), null);
        } catch (RuntimeException e) {
            logger.warn("Failed to edit checkpoint message chat={} message={}: {}",
                        chatId, messageId, e.getMessage());
        }
    }

    private Parsed parse(String data) {
        if (data == null || !data.startsWith(PREFIX)) return null;
        String[] parts = data.split(":", 3);
        if (parts.length != 3) return null;
        String action = parts[1];
        String checkpointId = parts[2];
        if (action.isBlank() || checkpointId.isBlank()) return null;
        if (!(action.equals("approve") || action.equals("changes") || action.equals("reject"))) {
            return null;
        }
        return new Parsed(action, checkpointId);
    }

    private void answer(String callbackId, String text) {
        try {
            bot.answerCallbackQuery(callbackId, text);
        } catch (RuntimeException e) {
            logger.warn("answerCallbackQuery failed for {}: {}", callbackId, e.getMessage());
        }
    }

    private void editWithError(long chatId, long messageId, String body) {
        try {
            bot.editMessageText(chatId, messageId, body, null);
        } catch (RuntimeException e) {
            logger.warn("Failed to edit checkpoint error message chat={} message={}: {}",
                        chatId, messageId, e.getMessage());
        }
    }

    private String successBody(CheckpointDecision decision) {
        return switch (decision) {
            case APPROVED -> "✅ Checkpoint approved.";
            case REWORK   -> "🔁 Changes requested on checkpoint.";
            case CANCEL   -> "🛑 Checkpoint rejected.";
        };
    }

    private record Parsed(String action, String checkpointId) {}
}
