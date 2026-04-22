package io.tacticl.business.telegram;

import io.tacticl.business.telegram.identity.TelegramIdentityResolver;
import io.tacticl.business.telegram.identity.TelegramUsernameCache;
import io.tacticl.business.telegram.router.CommandContext;
import io.tacticl.business.telegram.router.TelegramCommandRouter;
import io.tacticl.business.telegram.spark.TelegramSparkInitiator;
import io.tacticl.client.telegram.TelegramBotClient;
import io.tacticl.client.telegram.config.TelegramConfig;
import io.tacticl.client.telegram.dto.Message;
import io.tacticl.client.telegram.dto.MessageEntity;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import io.tacticl.client.telegram.dto.Update;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import io.tacticl.data.telegram.repository.TelegramProjectLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Entry point for every inbound Telegram {@link Update}. Responsibilities:
 *
 * <ul>
 *   <li>Seed {@link TelegramUsernameCache} from every message so later
 *       commands (e.g. {@code /grant @alice}) can resolve handles.</li>
 *   <li>Short-circuit the DM-only {@code /start} linking flow that predates
 *       the router (avoids coupling the pairing handshake to GROUP commands).</li>
 *   <li>Route every other slash command through {@link TelegramCommandRouter}.</li>
 *   <li>Detect plain-text bot mentions and reply-to-bot messages in groups and
 *       hand them to {@link TelegramSparkInitiator} as implicit {@code /spark}
 *       invocations.</li>
 * </ul>
 */
@Service
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class TelegramDispatchService {

    private static final Logger logger = LoggerFactory.getLogger(TelegramDispatchService.class);

    private final TelegramUserLinker linker;
    private final TelegramBotClient bot;
    private final TelegramCommandRouter commandRouter;
    private final TelegramUsernameCache usernameCache;
    private final TelegramSparkInitiator sparkInitiator;
    private final TelegramProjectLinkRepository projectRepo;
    private final TelegramIdentityResolver identity;
    private final TelegramConfig telegramConfig;

    public TelegramDispatchService(TelegramUserLinker linker,
                                   TelegramBotClient bot,
                                   TelegramCommandRouter commandRouter,
                                   TelegramUsernameCache usernameCache,
                                   TelegramSparkInitiator sparkInitiator,
                                   TelegramProjectLinkRepository projectRepo,
                                   TelegramIdentityResolver identity,
                                   TelegramConfig telegramConfig) {
        this.linker = linker;
        this.bot = bot;
        this.commandRouter = commandRouter;
        this.usernameCache = usernameCache;
        this.sparkInitiator = sparkInitiator;
        this.projectRepo = projectRepo;
        this.identity = identity;
        this.telegramConfig = telegramConfig;
    }

    public void handle(Update update) {
        if (update.message() != null) {
            handleMessage(update.message());
        } else if (update.callback_query() != null) {
            logger.debug("callback_query received, not handled in Phase 1");
        }
    }

    private void handleMessage(Message msg) {
        // Seed the username cache from every inbound message, regardless of
        // whether it carries text — /grant @alice relies on this observation.
        if (msg.from() != null && msg.chat() != null && msg.from().username() != null) {
            usernameCache.observe(msg.chat().id(), msg.from().id(), msg.from().username());
        }

        if (msg.text() == null) {
            return;
        }
        String text = msg.text().trim();
        if (text.isEmpty()) {
            return;
        }
        long chatId = msg.chat().id();

        // DM-only linking flow: intentionally bypasses the router so it works
        // even before any /init has created a project.
        if (text.startsWith("/start ")) {
            handleStartWithToken(msg, chatId, text);
            return;
        }
        if (text.equals("/start")) {
            bot.sendMessage(SendMessageRequest.plain(chatId,
                    "Welcome to Tacticl. To link your account, tap the link "
                            + "in your dashboard (Settings → Integrations → Telegram)."));
            return;
        }

        if (text.startsWith("/")) {
            long fromId = msg.from() != null ? msg.from().id() : 0L;
            String fromUsername = msg.from() != null ? msg.from().username() : null;
            CommandContext ctx = new CommandContext(chatId, fromId, text, fromUsername, msg);
            boolean handled = commandRouter.dispatch(ctx);
            if (!handled) {
                // Silently drop unknown commands so the bot doesn't spam groups
                // when users type slash-commands intended for other bots.
                String token = text.split("\\s+", 2)[0];
                logger.debug("No handler for command {} in chat {}", token, chatId);
            }
            return;
        }

        handlePlainText(msg, chatId, text);
    }

    private void handleStartWithToken(Message msg, long chatId, String text) {
        String token = text.substring("/start ".length()).trim();
        String username = msg.from() != null ? msg.from().username() : null;
        String firstName = msg.from() != null ? msg.from().first_name() : null;
        Optional<String> result = linker.redeemToken(token, chatId, username, firstName);
        if (result.isPresent()) {
            bot.sendMessage(SendMessageRequest.plain(chatId,
                    "✅ Linked as @" + username + ". Spark creation coming soon."));
        } else {
            bot.sendMessage(SendMessageRequest.plain(chatId,
                    "❌ Invalid or expired link token. Generate a new one from your Tacticl dashboard."));
        }
    }

    private void handlePlainText(Message msg, long chatId, String text) {
        boolean isGroup = msg.chat() != null
                && ("group".equals(msg.chat().type()) || "supergroup".equals(msg.chat().type()));
        if (!isGroup) {
            // DM fallback preserved to keep first-contact behaviour unchanged for users
            // who message the bot directly without using /start.
            bot.sendMessage(SendMessageRequest.plain(chatId,
                    "Commands not supported yet. Stay tuned — spark creation and "
                            + "checkpoint approvals are coming in the next release."));
            return;
        }

        String botUsername = telegramConfig.getBotUsername();
        if (botUsername == null || botUsername.isBlank()) {
            // Without a configured bot username we cannot safely detect mentions;
            // stay silent in groups rather than firing on every reply.
            return;
        }

        boolean mentioned = isMentioningBot(msg, botUsername);
        boolean replyToBot = isReplyToBot(msg, botUsername);
        if (!mentioned && !replyToBot) {
            return;
        }

        long telegramUserId = msg.from() != null ? msg.from().id() : 0L;
        Optional<String> tacticlUserId = identity.resolveByChatId(telegramUserId);
        if (tacticlUserId.isEmpty()) {
            bot.sendMessage(SendMessageRequest.plain(chatId,
                    "You must link your Tacticl account first."));
            return;
        }

        Optional<TelegramProjectLink> linkOpt = projectRepo.findByChatIdAndIsActiveTrue(chatId);
        if (linkOpt.isEmpty()) {
            bot.sendMessage(SendMessageRequest.plain(chatId,
                    "No active project in this group. Use /init first."));
            return;
        }

        String cleaned = mentioned ? stripBotMention(msg, botUsername) : text;
        sparkInitiator.initiate(chatId, tacticlUserId.get(), cleaned, linkOpt.get(), null);
    }

    private boolean isMentioningBot(Message msg, String botUsername) {
        List<MessageEntity> entities = msg.entities();
        if (entities == null || entities.isEmpty() || msg.text() == null) {
            return false;
        }
        String expected = "@" + botUsername;
        String fullText = msg.text();
        for (MessageEntity e : entities) {
            if (!"mention".equals(e.type())) continue;
            if (e.offset() < 0 || e.offset() + e.length() > fullText.length()) continue;
            String slice = fullText.substring(e.offset(), e.offset() + e.length());
            if (slice.equalsIgnoreCase(expected)) {
                return true;
            }
        }
        return false;
    }

    private boolean isReplyToBot(Message msg, String botUsername) {
        Message replyTo = msg.reply_to_message();
        if (replyTo == null || replyTo.from() == null) {
            return false;
        }
        return botUsername.equalsIgnoreCase(replyTo.from().username());
    }

    private String stripBotMention(Message msg, String botUsername) {
        String fullText = msg.text();
        List<MessageEntity> entities = msg.entities();
        if (entities == null) {
            return fullText.trim();
        }
        String expected = "@" + botUsername;
        StringBuilder sb = new StringBuilder(fullText);
        // Walk in reverse so offsets remain valid as we delete ranges.
        for (int i = entities.size() - 1; i >= 0; i--) {
            MessageEntity e = entities.get(i);
            if (!"mention".equals(e.type())) continue;
            if (e.offset() < 0 || e.offset() + e.length() > sb.length()) continue;
            String slice = sb.substring(e.offset(), e.offset() + e.length());
            if (slice.equalsIgnoreCase(expected)) {
                sb.delete(e.offset(), e.offset() + e.length());
            }
        }
        return sb.toString().trim().replaceAll("\\s+", " ");
    }
}
