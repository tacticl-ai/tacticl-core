package io.tacticl.client.telegram;

import io.cidadel.framework.exception.CidadelException;
import io.github.bucket4j.Bucket;
import io.tacticl.client.telegram.config.TelegramConfig;
import io.tacticl.client.telegram.dto.ApiResponse;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import io.tacticl.client.telegram.dto.SendMessageResponse;
import io.tacticl.client.telegram.dto.WebhookInfo;
import io.tacticl.client.telegram.exception.TelegramErrorDetails;
import java.util.List;
import java.util.Map;
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
        this.config = config;
        this.rateLimiter = rateLimiter;
        this.objectMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
        this.restClient = RestClient.builder()
            .baseUrl(config.getBaseUrl())
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    public SendMessageResponse sendMessage(SendMessageRequest request) {
        checkRateLimit();
        try {
            String body = restClient.post()
                .uri("/bot{token}/sendMessage", config.getBotToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(String.class);
            ApiResponse<SendMessageResponse> response = objectMapper.readValue(
                body,
                objectMapper.getTypeFactory().constructParametricType(
                    ApiResponse.class, SendMessageResponse.class));
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
            logger.error("Telegram sendMessage failed", e);
            throw new CidadelException(TelegramErrorDetails.SEND_MESSAGE_FAILED, MODULE_NAME,
                e.getMessage());
        }
    }

    public boolean setWebhook(String url, String secretToken) {
        checkRateLimit();
        try {
            Map<String, Object> payload = Map.of(
                "url", url,
                "secret_token", secretToken,
                "allowed_updates", List.of("message", "callback_query"),
                "drop_pending_updates", true
            );
            String body = restClient.post()
                .uri("/bot{token}/setWebhook", config.getBotToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);
            ApiResponse<Boolean> response = objectMapper.readValue(
                body,
                objectMapper.getTypeFactory().constructParametricType(
                    ApiResponse.class, Boolean.class));
            return response.ok() && Boolean.TRUE.equals(response.result());
        }
        catch (Exception e) {
            logger.error("Telegram setWebhook failed", e);
            throw new CidadelException(TelegramErrorDetails.WEBHOOK_REGISTRATION_FAILED,
                MODULE_NAME, e.getMessage());
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

    private void checkRateLimit() {
        if (!rateLimiter.tryConsume(1)) {
            throw new CidadelException(TelegramErrorDetails.RATE_LIMIT_EXCEEDED, MODULE_NAME);
        }
    }
}
