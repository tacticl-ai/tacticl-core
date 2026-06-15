package io.tacticl.client.discord;

import io.cidadel.framework.exception.CidadelException;
import io.github.bucket4j.Bucket;
import io.tacticl.client.discord.config.DiscordConfig;
import io.tacticl.client.discord.exception.DiscordErrorDetails;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * REST client for the Discord HTTP API (v10). All write calls authenticate with the bot token
 * ({@code Authorization: Bot <token>}); the interaction-callback endpoint is the one exception —
 * Discord authenticates it by the interaction token embedded in the URL, not the bot token.
 *
 * <p>Run updates are rendered as real channel messages via {@link #createChannelMessage} / the
 * bot token — NOT via the interaction token (which expires ~15 minutes after the interaction).
 * {@link #editChannelMessage} is used to strip the Approve/Changes/Reject buttons (empty
 * {@code components} array) once a checkpoint decision is recorded.
 *
 * <p>Mirrors the plain-{@link RestClient} + bucket4j + {@link JsonMapper} convention of
 * {@code client-telegram}'s {@code TelegramBotClient} (the repo's reference HTTP client).
 */
public class DiscordRestClient {

    private static final Logger logger = LoggerFactory.getLogger(DiscordRestClient.class);
    private static final String MODULE_NAME = "client-discord";

    private final DiscordConfig config;
    private final Bucket rateLimiter;
    private final RestClient restClient;
    private final JsonMapper objectMapper;

    public DiscordRestClient(DiscordConfig config, Bucket rateLimiter) {
        this(config, rateLimiter, RestClient.builder().baseUrl(config.getBaseUrl()));
    }

    /**
     * Test-friendly constructor: accepts a pre-configured {@link RestClient.Builder} so tests
     * can bind a {@code MockRestServiceServer} to it before the client is built.
     */
    public DiscordRestClient(DiscordConfig config, Bucket rateLimiter, RestClient.Builder builder) {
        this.config = config;
        this.rateLimiter = rateLimiter;
        this.objectMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
        this.restClient = builder.build();
    }

    /**
     * Bulk-overwrites the guild-scoped application commands (slash + context-menu). Guild-scoped
     * commands register instantly, unlike global commands which take up to an hour to propagate.
     *
     * @param commands list of command definitions (already in Discord's command JSON shape)
     */
    public void registerGuildCommands(String guildId, List<Map<String, Object>> commands) {
        checkRateLimit();
        String path = "/applications/" + config.getApplicationId() + "/guilds/" + guildId + "/commands";
        executeBot("PUT", path, commands, DiscordErrorDetails.COMMAND_REGISTRATION_FAILED);
    }

    /**
     * Sends the immediate response to an interaction. Discord authenticates this endpoint via the
     * interaction id + token in the URL (no bot token). Used for PING→PONG (type 1) and the
     * deferred acknowledgement (type 5) the controller returns synchronously.
     *
     * @param interactionId    the interaction's snowflake id
     * @param interactionToken the interaction token (valid ~15 minutes)
     * @param callback         the interaction-response payload ({@code {"type": ...}})
     */
    public void createInteractionResponse(String interactionId, String interactionToken,
                                          Map<String, Object> callback) {
        checkRateLimit();
        String path = "/interactions/" + interactionId + "/" + interactionToken + "/callback";
        executeNoAuth("POST", path, callback, DiscordErrorDetails.INTERACTION_RESPONSE_FAILED);
    }

    /**
     * Posts a follow-up message to a deferred interaction (within the interaction token's window).
     * Used only for the immediate acknowledgement back to the invoking user; durable run updates
     * go to the channel via {@link #createChannelMessage}.
     */
    public void createFollowupMessage(String interactionToken, Map<String, Object> message) {
        checkRateLimit();
        String path = "/webhooks/" + config.getApplicationId() + "/" + interactionToken;
        executeNoAuth("POST", path, message, DiscordErrorDetails.INTERACTION_RESPONSE_FAILED);
    }

    /**
     * Posts a message to a channel using the bot token. This is the durable surface for run
     * updates: it does not depend on the interaction token and so survives past the 15-minute
     * interaction window.
     *
     * @return the created message's snowflake id, or {@code null} if absent from the response
     */
    public String createChannelMessage(String channelId, Map<String, Object> message) {
        checkRateLimit();
        String path = "/channels/" + channelId + "/messages";
        Map<String, Object> response = executeBot("POST", path, message, DiscordErrorDetails.SEND_MESSAGE_FAILED);
        return response == null ? null : (String) response.get("id");
    }

    /**
     * Edits a previously posted channel message via the bot token — used to strip the
     * Approve/Changes/Reject buttons by sending an empty {@code components} array once a decision
     * is recorded.
     */
    public void editChannelMessage(String channelId, String messageId, Map<String, Object> message) {
        checkRateLimit();
        String path = "/channels/" + channelId + "/messages/" + messageId;
        executeBot("PATCH", path, message, DiscordErrorDetails.SEND_MESSAGE_FAILED);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> executeBot(String method, String path, Object payload,
                                           DiscordErrorDetails failureDetails) {
        try {
            String body = restClient.method(org.springframework.http.HttpMethod.valueOf(method))
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bot " + config.getBotToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);
            return parse(body);
        }
        catch (Exception e) {
            logger.error("Discord {} {} failed", method, path, e);
            throw new CidadelException(failureDetails, MODULE_NAME, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> executeNoAuth(String method, String path, Object payload,
                                              DiscordErrorDetails failureDetails) {
        try {
            String body = restClient.method(org.springframework.http.HttpMethod.valueOf(method))
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);
            return parse(body);
        }
        catch (Exception e) {
            logger.error("Discord {} {} failed", method, path, e);
            throw new CidadelException(failureDetails, MODULE_NAME, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String body) {
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        // Some endpoints return a JSON ARRAY, not an object — notably the bulk command-overwrite
        // PUT /applications/{app}/guilds/{guild}/commands, whose caller discards the response.
        // Deserializing an array into Map throws (MismatchedInputException), so branch on shape:
        // only object responses carry fields callers read (e.g. a message's "id").
        JsonNode node = objectMapper.readTree(body);
        if (node.isObject()) {
            return objectMapper.convertValue(node, Map.class);
        }
        return Map.of();
    }

    private void checkRateLimit() {
        if (!rateLimiter.tryConsume(1)) {
            throw new CidadelException(DiscordErrorDetails.RATE_LIMIT_EXCEEDED, MODULE_NAME);
        }
    }
}
