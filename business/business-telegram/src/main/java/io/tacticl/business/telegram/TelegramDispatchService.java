package io.tacticl.business.telegram;

import io.tacticl.client.telegram.TelegramBotClient;
import io.tacticl.client.telegram.dto.Message;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import io.tacticl.client.telegram.dto.Update;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TelegramDispatchService {

    private static final Logger logger = LoggerFactory.getLogger(TelegramDispatchService.class);

    private final TelegramUserLinker linker;
    private final TelegramBotClient bot;

    public TelegramDispatchService(TelegramUserLinker linker, TelegramBotClient bot) {
        this.linker = linker;
        this.bot = bot;
    }

    public void handle(Update update) {
        if (update.message() != null) {
            handleMessage(update.message());
        } else if (update.callback_query() != null) {
            logger.debug("callback_query received, not handled in Phase 1");
        }
    }

    private void handleMessage(Message msg) {
        if (msg.text() == null) return;
        long chatId = msg.chat().id();
        String text = msg.text().trim();

        if (text.startsWith("/start ")) {
            String token = text.substring("/start ".length()).trim();
            var result = linker.redeemToken(token, chatId,
                    msg.from().username(), msg.from().first_name());
            if (result.isPresent()) {
                bot.sendMessage(SendMessageRequest.plain(chatId,
                        "✅ Linked as @" + msg.from().username()
                                + ". Spark creation coming soon."));
            } else {
                bot.sendMessage(SendMessageRequest.plain(chatId,
                        "❌ Invalid or expired link token. Generate a new one from your Tacticl dashboard."));
            }
        } else if (text.equals("/start")) {
            bot.sendMessage(SendMessageRequest.plain(chatId,
                    "Welcome to Tacticl. To link your account, tap the link "
                            + "in your dashboard (Settings → Integrations → Telegram)."));
        } else {
            bot.sendMessage(SendMessageRequest.plain(chatId,
                    "Commands not supported yet. Stay tuned — spark creation and "
                            + "checkpoint approvals are coming in the next release."));
        }
    }
}
