package io.tacticl.client.telegram;

import io.cidadel.framework.exception.CidadelException;
import io.github.bucket4j.Bucket;
import io.tacticl.client.telegram.config.TelegramConfig;
import io.tacticl.client.telegram.dto.ApiResponse;
import io.tacticl.client.telegram.dto.BotCommand;
import io.tacticl.client.telegram.dto.ForumTopic;
import io.tacticl.client.telegram.dto.InlineKeyboardMarkup;
import io.tacticl.client.telegram.dto.Message;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import io.tacticl.client.telegram.dto.SendMessageResponse;
import io.tacticl.client.telegram.dto.TelegramFile;
import io.tacticl.client.telegram.dto.WebhookInfo;
import io.tacticl.client.telegram.exception.TelegramErrorDetails;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

public class TelegramBotClient {

    private static final Logger logger = LoggerFactory.getLogger(TelegramBotClient.class);
    private static final String MODULE_NAME = "client-telegram";

    private final TelegramConfig config;
    private final Bucket rateLimiter;
    private final RestClient restClient;
    private final JsonMapper objectMapper;

    public TelegramBotClient(TelegramConfig config, Bucket rateLimiter) {
        this(config, rateLimiter, RestClient.builder().baseUrl(config.getBaseUrl()));
    }

    /**
     * Test-friendly constructor: accepts a pre-configured {@link RestClient.Builder} so tests
     * can bind a {@code MockRestServiceServer} to it before the client is built.
     */
    public TelegramBotClient(TelegramConfig config, Bucket rateLimiter, RestClient.Builder builder) {
        this.config = config;
        this.rateLimiter = rateLimiter;
        this.objectMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
        this.restClient = builder
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    public SendMessageResponse sendMessage(SendMessageRequest request) {
        checkRateLimit();
        return executePost("/sendMessage", request, SendMessageResponse.class,
            TelegramErrorDetails.SEND_MESSAGE_FAILED);
    }

    public boolean setWebhook(String url, String secretToken) {
        checkRateLimit();
        Map<String, Object> payload = Map.of(
            "url", url,
            "secret_token", secretToken,
            "allowed_updates", List.of("message", "edited_message", "callback_query",
                                       "my_chat_member", "chat_member"),
            "drop_pending_updates", true
        );
        try {
            Boolean ok = executePost("/setWebhook", payload, Boolean.class,
                TelegramErrorDetails.WEBHOOK_REGISTRATION_FAILED);
            return Boolean.TRUE.equals(ok);
        }
        catch (CidadelException e) {
            if (e.getErrorDetails() == TelegramErrorDetails.BOT_API_ERROR) {
                logger.warn("Telegram setWebhook returned ok=false");
                return false;
            }
            throw e;
        }
    }

    public WebhookInfo getWebhookInfo() {
        checkRateLimit();
        try {
            String body = restClient.get()
                .uri("/bot{token}/getWebhookInfo", config.getBotToken())
                .retrieve()
                .body(String.class);
            ApiResponse<WebhookInfo> response = objectMapper.readValue(
                body,
                objectMapper.getTypeFactory().constructParametricType(
                    ApiResponse.class, WebhookInfo.class));
            return response.ok() ? response.result() : null;
        }
        catch (Exception e) {
            logger.error("Telegram getWebhookInfo failed", e);
            throw new CidadelException(TelegramErrorDetails.WEBHOOK_REGISTRATION_FAILED,
                MODULE_NAME, e.getMessage());
        }
    }

    public ForumTopic createForumTopic(long chatId, String name) {
        checkRateLimit();
        Map<String, Object> payload = Map.of("chat_id", chatId, "name", name);
        return executePost("/createForumTopic", payload, ForumTopic.class,
            TelegramErrorDetails.BOT_API_ERROR);
    }

    public Message editMessageText(long chatId, long messageId, String text, InlineKeyboardMarkup markup) {
        checkRateLimit();
        Map<String, Object> payload = new HashMap<>();
        payload.put("chat_id", chatId);
        payload.put("message_id", messageId);
        payload.put("text", text);
        if (markup != null) {
            payload.put("reply_markup", markup);
        }
        return executePost("/editMessageText", payload, Message.class,
            TelegramErrorDetails.BOT_API_ERROR);
    }

    public boolean pinChatMessage(long chatId, long messageId) {
        checkRateLimit();
        Map<String, Object> payload = Map.of(
            "chat_id", chatId,
            "message_id", messageId,
            "disable_notification", true
        );
        Boolean ok = executePost("/pinChatMessage", payload, Boolean.class,
            TelegramErrorDetails.BOT_API_ERROR);
        return Boolean.TRUE.equals(ok);
    }

    public boolean answerCallbackQuery(String callbackQueryId, String text) {
        checkRateLimit();
        Map<String, Object> payload = new HashMap<>();
        payload.put("callback_query_id", callbackQueryId);
        if (text != null) {
            payload.put("text", text);
        }
        Boolean ok = executePost("/answerCallbackQuery", payload, Boolean.class,
            TelegramErrorDetails.BOT_API_ERROR);
        return Boolean.TRUE.equals(ok);
    }

    public boolean leaveChat(long chatId) {
        checkRateLimit();
        Boolean ok = executePost("/leaveChat", Map.of("chat_id", chatId), Boolean.class,
            TelegramErrorDetails.BOT_API_ERROR);
        return Boolean.TRUE.equals(ok);
    }

    /**
     * Retrieves metadata for a file (voice note, document, photo, etc.) by Telegram file id.
     * The returned {@link TelegramFile#file_path()} is valid for at least 1 hour and can be
     * passed to {@link #downloadFile(String)} to fetch the binary content.
     *
     * @return populated {@link TelegramFile} on success, empty when the API responds {@code ok=false}
     */
    public Optional<TelegramFile> getFile(String fileId) {
        checkRateLimit();
        try {
            String body = restClient.get()
                .uri("/bot{token}/getFile?file_id={fileId}", config.getBotToken(), fileId)
                .retrieve()
                .body(String.class);
            ApiResponse<TelegramFile> response = objectMapper.readValue(
                body,
                objectMapper.getTypeFactory().constructParametricType(
                    ApiResponse.class, TelegramFile.class));
            if (response == null || !response.ok()) {
                return Optional.empty();
            }
            return Optional.ofNullable(response.result());
        }
        catch (Exception e) {
            logger.error("Telegram getFile failed for fileId={}", fileId, e);
            throw new CidadelException(TelegramErrorDetails.BOT_API_ERROR, MODULE_NAME, e.getMessage());
        }
    }

    /**
     * Downloads the binary content of a Telegram file using the {@code file_path} obtained from
     * {@link #getFile(String)}. Telegram serves files from a different host pattern than the
     * Bot API: {@code https://api.telegram.org/file/bot<token>/<file_path>} (note the
     * {@code /file} prefix). The file_path is valid for at least 1 hour.
     *
     * @param filePath value of {@link TelegramFile#file_path()}
     * @return file bytes (never {@code null}); zero-length array if the body was empty
     */
    public byte[] downloadFile(String filePath) {
        checkRateLimit();
        // Build URI manually so embedded slashes in file_path (e.g. "voice/file_5.ogg")
        // are preserved as path separators rather than percent-encoded.
        String uri = "/file/bot" + config.getBotToken() + "/" + filePath;
        try {
            byte[] body = restClient.get()
                .uri(uri)
                .retrieve()
                .body(byte[].class);
            return body != null ? body : new byte[0];
        }
        catch (Exception e) {
            logger.error("Telegram downloadFile failed for filePath={}", filePath, e);
            throw new CidadelException(TelegramErrorDetails.BOT_API_ERROR, MODULE_NAME, e.getMessage());
        }
    }

    public boolean setMyCommands(List<BotCommand> commands, String scopeType) {
        checkRateLimit();
        Map<String, Object> payload = Map.of(
            "commands", commands,
            "scope", Map.of("type", scopeType)
        );
        Boolean ok = executePost("/setMyCommands", payload, Boolean.class,
            TelegramErrorDetails.BOT_API_ERROR);
        return Boolean.TRUE.equals(ok);
    }

    private <T> T executePost(String path, Object payload, Class<T> responseType,
                              TelegramErrorDetails failureDetails) {
        try {
            String body = restClient.post()
                .uri("/bot{token}" + path, config.getBotToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);
            ApiResponse<T> response = objectMapper.readValue(
                body,
                objectMapper.getTypeFactory().constructParametricType(
                    ApiResponse.class, responseType));
            if (!response.ok()) {
                throw new CidadelException(TelegramErrorDetails.BOT_API_ERROR, MODULE_NAME,
                    response.description());
            }
            return response.result();
        }
        catch (CidadelException e) {
            throw e;
        }
        catch (Exception e) {
            logger.error("Telegram {} failed", path, e);
            throw new CidadelException(failureDetails, MODULE_NAME, e.getMessage());
        }
    }

    private void checkRateLimit() {
        if (!rateLimiter.tryConsume(1)) {
            throw new CidadelException(TelegramErrorDetails.RATE_LIMIT_EXCEEDED, MODULE_NAME);
        }
    }
}
