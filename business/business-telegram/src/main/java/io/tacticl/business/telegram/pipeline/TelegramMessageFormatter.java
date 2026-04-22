package io.tacticl.business.telegram.pipeline;

import io.tacticl.business.pipeline.dto.PipelineCallbackEvent;
import io.tacticl.client.telegram.dto.InlineKeyboardButton;
import io.tacticl.client.telegram.dto.InlineKeyboardMarkup;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Formats PDLC pipeline events emitted by {@code PipelineEventEmitter} into Telegram
 * {@link SendMessageRequest} payloads. Emits MarkdownV2 text and, for checkpoint events,
 * an inline keyboard with approve/changes/reject actions.
 *
 * <p>Reads role/phase/eventType as first-class fields from {@link PipelineCallbackEvent}.
 * The {@code payloadJson} string is only parsed for events that actually need a payload
 * field (checkpoint id, artifact path, failure reason) — the hot path of role events
 * avoids JSON parsing entirely.
 *
 * <p>Pure formatter: no I/O, no project lookup — {@code TelegramEventChannel} is
 * responsible for resolving chat/thread ids and enqueueing.
 */
@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class TelegramMessageFormatter {

    /** Telegram hard limit on text message length. */
    private static final int MAX_TEXT_LENGTH = 4096;

    /** Telegram hard limit on inline keyboard callback_data (UTF-8 bytes). */
    private static final int MAX_CALLBACK_BYTES = 64;

    private static final String PARSE_MODE = "MarkdownV2";

    private static final JsonMapper JSON = new JsonMapper();

    /**
     * @param chatId          target Telegram chat id.
     * @param messageThreadId forum-topic thread id, or {@code null} for the main chat.
     * @param event           callback event from the pipeline emitter. Carries eventType,
     *                        role, phase as first-class fields; {@code payloadJson} is a
     *                        raw JSON string (may be null/blank) parsed only when needed.
     * @return zero or more ready-to-enqueue {@link SendMessageRequest}s. Unknown event
     *         types return an empty list.
     */
    public List<SendMessageRequest> format(long chatId,
                                           Integer messageThreadId,
                                           PipelineCallbackEvent event) {
        if (event == null || event.eventType() == null) {
            return List.of();
        }
        return switch (event.eventType()) {
            case "PIPELINE_STARTED" ->
                single(chatId, messageThreadId, "🚀 *Pipeline started*", null);
            case "ROLE_STARTED" ->
                single(chatId, messageThreadId, formatRoleStarted(event), null);
            case "ROLE_COMPLETED" ->
                single(chatId, messageThreadId, formatRoleCompleted(event), null);
            case "ROLE_REWORK" ->
                single(chatId, messageThreadId, formatRoleRework(event), null);
            case "CHECKPOINT_REQUESTED", "CHECKPOINT_PENDING", "REVIEWER_NEEDS_APPROVAL" ->
                formatCheckpoint(chatId, messageThreadId, event);
            case "PIPELINE_COMPLETED" ->
                single(chatId, messageThreadId, "✅ *Pipeline completed*", null);
            case "PIPELINE_FAILED" ->
                single(chatId, messageThreadId, formatPipelineFailed(event), null);
            case "PIPELINE_CANCELLED" ->
                single(chatId, messageThreadId, "🛑 *Pipeline cancelled*", null);
            default -> List.of();
        };
    }

    // ---- Event builders ----------------------------------------------------

    private String formatRoleStarted(PipelineCallbackEvent event) {
        String role = firstNonBlank(event.role(), "ROLE");
        String phase = event.phase();
        StringBuilder sb = new StringBuilder();
        sb.append("▶️ *").append(escape(role)).append("* started");
        if (phase != null && !phase.isBlank()) {
            sb.append(" \\(").append(escape(phase)).append("\\)");
        }
        return sb.toString();
    }

    private String formatRoleCompleted(PipelineCallbackEvent event) {
        String role = firstNonBlank(event.role(), "ROLE");
        // WHY: role completion payload may carry a message + artifact reference.
        // Parse once lazily — unlike role-started/ended, the payload here matters.
        JsonNode payload = parsePayloadJson(event.payloadJson());
        String message = asString(payload, "message", null);
        String artifactPath = asString(payload, "artifactPath", null);
        String artifactUrl = asString(payload, "artifactUrl", null);

        StringBuilder sb = new StringBuilder();
        sb.append("✔️ *").append(escape(role)).append("* finished");
        if (artifactPath != null && !artifactPath.isEmpty()) {
            sb.append("\n📄 ").append(escape(artifactPath));
        }
        if (message != null && !message.isEmpty()) {
            sb.append("\n\n").append(escape(message));
        }
        String fallbackUrl = artifactUrl != null ? artifactUrl : artifactPath;
        return applyLengthBudget(sb.toString(), fallbackUrl);
    }

    private String formatRoleRework(PipelineCallbackEvent event) {
        String role = firstNonBlank(event.role(), "ROLE");
        return "🔁 *" + escape(role) + "* rework requested";
    }

    private String formatPipelineFailed(PipelineCallbackEvent event) {
        JsonNode payload = parsePayloadJson(event.payloadJson());
        String reason = asString(payload, "reason", null);
        if (reason == null || reason.isBlank()) {
            return "❌ *Pipeline failed*";
        }
        return applyLengthBudget(
            "❌ *Pipeline failed*\n\n" + escape(reason),
            null
        );
    }

    private List<SendMessageRequest> formatCheckpoint(long chatId,
                                                     Integer messageThreadId,
                                                     PipelineCallbackEvent event) {
        // WHY: production payloadJson for checkpoint events embeds "checkpointId".
        // Checkpoints are infrequent, so a full JSON parse here is fine.
        JsonNode payload = parsePayloadJson(event.payloadJson());
        String checkpointId = asString(payload, "checkpointId", null);
        String role = firstNonBlank(event.role(), "ROLE");
        String phase = event.phase();

        StringBuilder sb = new StringBuilder();
        sb.append("⏸️ *Checkpoint:* ").append(escape(role));
        if (phase != null && !phase.isBlank()) {
            sb.append(" \\(").append(escape(phase)).append("\\)");
        }
        sb.append("\n\nApprove, request changes, or reject:");
        String text = applyLengthBudget(sb.toString(), null);

        InlineKeyboardMarkup markup = buildCheckpointKeyboard(checkpointId);
        return List.of(new SendMessageRequest(chatId, text, PARSE_MODE, markup, messageThreadId));
    }

    /**
     * Builds the 3-button approve/changes/reject keyboard. Returns {@code null} when the
     * checkpoint id would make any callback_data exceed Telegram's 64-byte limit — the
     * caller still gets a text message; resolution can proceed via deep link / REST.
     */
    private InlineKeyboardMarkup buildCheckpointKeyboard(String checkpointId) {
        if (checkpointId == null || checkpointId.isBlank()) {
            return null;
        }
        String approve = "cp:approve:" + checkpointId;
        String changes = "cp:changes:" + checkpointId;
        String reject  = "cp:reject:"  + checkpointId;
        if (utf8Length(approve) > MAX_CALLBACK_BYTES
                || utf8Length(changes) > MAX_CALLBACK_BYTES
                || utf8Length(reject)  > MAX_CALLBACK_BYTES) {
            return null;
        }
        return new InlineKeyboardMarkup(List.of(List.of(
            new InlineKeyboardButton("✅ Approve", approve),
            new InlineKeyboardButton("✏️ Changes", changes),
            new InlineKeyboardButton("❌ Reject",  reject)
        )));
    }

    // ---- Helpers -----------------------------------------------------------

    private List<SendMessageRequest> single(long chatId,
                                            Integer messageThreadId,
                                            String text,
                                            InlineKeyboardMarkup markup) {
        return List.of(new SendMessageRequest(
            chatId, applyLengthBudget(text, null), PARSE_MODE, markup, messageThreadId
        ));
    }

    /**
     * Enforces the 4096-char cap. If text overflows, truncates and appends either the
     * artifact URL (if provided) or an ellipsis. Ensures the cut does not land mid-escape
     * (a trailing backslash would leave an unmatched MarkdownV2 escape and Telegram would
     * reject the message).
     */
    private String applyLengthBudget(String text, String artifactUrl) {
        if (text.length() <= MAX_TEXT_LENGTH) {
            return text;
        }
        String suffix;
        if (artifactUrl != null && !artifactUrl.isBlank()) {
            suffix = "\n\n" + escape(artifactUrl);
        } else {
            suffix = "…";
        }
        int keep = MAX_TEXT_LENGTH - suffix.length();
        if (keep < 0) {
            keep = 0;
        }
        // WHY: if truncation lands on the backslash of an escape pair, drop the lone
        // backslash so Telegram's MarkdownV2 parser doesn't reject "\\…" or "\\<eof>".
        while (keep > 0 && text.charAt(keep - 1) == '\\') {
            keep--;
        }
        return text.substring(0, keep) + suffix;
    }

    /**
     * Escapes MarkdownV2 special characters per Telegram Bot API:
     * {@code _ * [ ] ( ) ~ ` > # + - = | { } . !}
     */
    static String escape(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '_', '*', '[', ']', '(', ')', '~', '`', '>', '#',
                     '+', '-', '=', '|', '{', '}', '.', '!', '\\' ->
                    out.append('\\').append(c);
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static int utf8Length(String s) {
        return s.getBytes(StandardCharsets.UTF_8).length;
    }

    private static String firstNonBlank(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s;
    }

    private static String asString(JsonNode node, String field, String fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return fallback;
        }
        String s = v.asString("");
        return s.isEmpty() ? fallback : s;
    }

    /** Parses a raw JSON string into a JsonNode. Returns a null-node for blank or invalid input. */
    private static JsonNode parsePayloadJson(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return JSON.nullNode();
        }
        try {
            return JSON.readTree(payloadJson);
        } catch (JacksonException e) {
            return JSON.nullNode();
        }
    }
}
