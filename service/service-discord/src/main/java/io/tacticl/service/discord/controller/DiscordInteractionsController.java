package io.tacticl.service.discord.controller;

import io.cidadel.service.base.controller.BaseController;
import io.tacticl.business.discord.DiscordInteractionDedupService;
import io.tacticl.business.discord.DiscordInteractionDispatcher;
import io.tacticl.client.discord.DiscordEd25519Verifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/**
 * The single Discord Interactions webhook. Every Discord trigger — the {@code /pdlc} slash command,
 * the "Send to PDLC" message context-menu, and the Approve/Changes/Reject buttons — arrives here as
 * an Ed25519-signed HTTP POST.
 *
 * <p>Request handling, in strict order:
 * <ol>
 *   <li><b>Verify</b> the {@code X-Signature-Ed25519} / {@code X-Signature-Timestamp} headers
 *       against the raw body. Discord requires a failed verification to return <b>401</b>.</li>
 *   <li><b>PING → PONG</b>: a type-1 interaction is Discord's endpoint health check; answer with a
 *       type-1 response synchronously.</li>
 *   <li><b>Dedup BEFORE dispatch</b>: record the interaction id atomically; a re-delivery is
 *       ACK-and-dropped (Discord re-sends when the first ACK is slow).</li>
 *   <li><b>Deferred ACK</b>: return a type-5 (DEFERRED_CHANNEL_MESSAGE_WITH_SOURCE) within the
 *       3-second window so Discord shows "thinking…", then do the real work off-thread.</li>
 *   <li><b>Async dispatch</b>: hand the interaction to {@link DiscordInteractionDispatcher} on the
 *       shared pipeline executor — identity resolve → normalize → ingress dispatch → run binding.</li>
 * </ol>
 *
 * <p>Dormant unless {@code tacticl.discord.enabled=true}.
 */
@RestController
@RequestMapping("/v1/discord/interactions")
@ConditionalOnProperty(name = "tacticl.discord.enabled", havingValue = "true")
public class DiscordInteractionsController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(DiscordInteractionsController.class);

    /** Discord interaction types. */
    private static final int TYPE_PING = 1;

    /** Discord interaction-response types. */
    private static final int RESPONSE_PONG = 1;
    private static final int RESPONSE_DEFERRED_CHANNEL_MESSAGE = 5;

    private final DiscordEd25519Verifier verifier;
    private final DiscordInteractionDedupService dedupService;
    private final DiscordInteractionDispatcher dispatcher;
    private final JsonMapper objectMapper;

    public DiscordInteractionsController(DiscordEd25519Verifier verifier,
                                         DiscordInteractionDedupService dedupService,
                                         DiscordInteractionDispatcher dispatcher) {
        this.verifier = verifier;
        this.dedupService = dedupService;
        this.dispatcher = dispatcher;
        this.objectMapper = JsonMapper.builder().build();
    }

    @Override
    protected String getModuleName() {
        return "discord-interactions";
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> interactions(
            @RequestHeader(value = "X-Signature-Ed25519", required = false) String signature,
            @RequestHeader(value = "X-Signature-Timestamp", required = false) String timestamp,
            @RequestBody(required = false) byte[] body) {

        // 1) Ed25519 verification against the RAW body — Discord requires 401 on failure.
        if (!verifier.verify(signature, timestamp, body)) {
            log.warn("Rejected Discord interaction with invalid signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (body == null || body.length == 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        Map<String, Object> interaction;
        try {
            interaction = parse(body);
        } catch (Exception e) {
            log.warn("Failed to parse Discord interaction body", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        int type = asInt(interaction.get("type"));

        // 2) PING → PONG endpoint health check.
        if (type == TYPE_PING) {
            return ResponseEntity.ok(Map.of("type", RESPONSE_PONG));
        }

        // 3) Dedup BEFORE dispatch — ACK-and-drop a re-delivery (still a valid 200 to Discord).
        String interactionId = asString(interaction.get("id"));
        if (!dedupService.markIfFirstSeen(interactionId, String.valueOf(type))) {
            return ResponseEntity.ok(Map.of("type", RESPONSE_DEFERRED_CHANNEL_MESSAGE));
        }

        // 5) Hand off async (interaction token reserved for the dispatcher's single followup).
        String interactionToken = asString(interaction.get("token"));
        dispatcher.dispatchAsync(interaction, interactionToken);

        // 4) Deferred ACK within the 3-second window.
        return ResponseEntity.ok(Map.of("type", RESPONSE_DEFERRED_CHANNEL_MESSAGE));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(byte[] body) {
        return objectMapper.readValue(body, Map.class);
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static int asInt(Object o) {
        return o instanceof Number n ? n.intValue() : -1;
    }
}
