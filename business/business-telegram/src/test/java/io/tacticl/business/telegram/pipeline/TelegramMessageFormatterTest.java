package io.tacticl.business.telegram.pipeline;

import io.tacticl.client.telegram.dto.InlineKeyboardButton;
import io.tacticl.client.telegram.dto.InlineKeyboardMarkup;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramMessageFormatterTest {

    private static final long CHAT_ID = 123_456_789L;

    private final TelegramMessageFormatter formatter = new TelegramMessageFormatter();

    @Test
    void roleStartedEventProducesSinglePlainMessageNoKeyboard() {
        List<SendMessageRequest> out = formatter.format(
            CHAT_ID,
            null,
            "ROLE_STARTED",
            Map.of("role", "RESEARCHER", "phase", "DISCOVERY")
        );

        assertThat(out).hasSize(1);
        SendMessageRequest msg = out.get(0);
        assertThat(msg.chat_id()).isEqualTo(CHAT_ID);
        assertThat(msg.reply_markup()).isNull();
        assertThat(msg.parse_mode()).isEqualTo("MarkdownV2");
        assertThat(msg.text()).contains("RESEARCHER");
        assertThat(msg.text().length()).isLessThanOrEqualTo(4096);
    }

    @Test
    void artifactReadyEventIncludesArtifactReferenceWithEscapedSpecialChars() {
        // WHY: MarkdownV2 requires escaping of _ * [ ] ( ) ~ ` > # + - = | { } . !
        // A realistic artifact path contains many of these (dots, underscores, dashes).
        Map<String, Object> payload = Map.of(
            "role", "ARCHITECT",
            "artifactPath", "docs/specs/2026-04-19_system-design_v1.2.md"
        );

        List<SendMessageRequest> out = formatter.format(
            CHAT_ID, null, "ROLE_COMPLETED", payload
        );

        assertThat(out).hasSize(1);
        String text = out.get(0).text();
        assertThat(text).contains("ARCHITECT");
        // escaped form — dots, underscores, and dashes must be backslash-prefixed in MarkdownV2
        assertThat(text).contains("2026\\-04\\-19");
        assertThat(text).contains("system\\-design");
        assertThat(text).contains("v1\\.2\\.md");
        assertThat(text).contains("\\_"); // underscore(s) in filename were escaped
    }

    @Test
    void overflowTruncatesAndAppendsArtifactUrlWhenAvailable() {
        String longMessage = "x".repeat(10_000);
        Map<String, Object> payload = Map.of(
            "role", "TESTER",
            "message", longMessage,
            "artifactUrl", "https://example.com/a/b"
        );

        List<SendMessageRequest> out = formatter.format(
            CHAT_ID, null, "ROLE_COMPLETED", payload
        );

        assertThat(out).hasSize(1);
        String text = out.get(0).text();
        assertThat(text.length()).isLessThanOrEqualTo(4096);
        assertThat(text).contains("https://example\\.com/a/b");
    }

    @Test
    void overflowWithoutArtifactUrlTruncatesWithEllipsis() {
        String longMessage = "y".repeat(10_000);
        Map<String, Object> payload = Map.of(
            "role", "IMPLEMENTER",
            "message", longMessage
        );

        List<SendMessageRequest> out = formatter.format(
            CHAT_ID, null, "ROLE_COMPLETED", payload
        );

        assertThat(out).hasSize(1);
        String text = out.get(0).text();
        assertThat(text.length()).isLessThanOrEqualTo(4096);
        assertThat(text).endsWith("…");
    }

    @Test
    void checkpointPendingEventIncludesThreeButtonKeyboardWithSafeCallbackData() {
        String checkpointId = "cp-abc123def456";
        Map<String, Object> payload = Map.of(
            "checkpointId", checkpointId,
            "role", "REVIEWER",
            "phase", "REVIEW"
        );

        List<SendMessageRequest> out = formatter.format(
            CHAT_ID, null, "CHECKPOINT_REQUESTED", payload
        );

        assertThat(out).hasSize(1);
        SendMessageRequest msg = out.get(0);
        InlineKeyboardMarkup markup = msg.reply_markup();
        assertThat(markup).isNotNull();
        assertThat(markup.inline_keyboard()).hasSize(1);
        List<InlineKeyboardButton> row = markup.inline_keyboard().get(0);
        assertThat(row).hasSize(3);

        assertThat(row.get(0).callback_data()).isEqualTo("cp:approve:" + checkpointId);
        assertThat(row.get(1).callback_data()).isEqualTo("cp:changes:" + checkpointId);
        assertThat(row.get(2).callback_data()).isEqualTo("cp:reject:" + checkpointId);

        // Telegram hard limit: 64 bytes per callback_data
        for (InlineKeyboardButton button : row) {
            byte[] bytes = button.callback_data().getBytes(StandardCharsets.UTF_8);
            assertThat(bytes.length)
                .as("callback_data must be <=64 bytes for button '%s'", button.text())
                .isLessThanOrEqualTo(64);
        }
    }

    @Test
    void checkpointWithOverlyLongIdStillProducesValidCallbackData() {
        // WHY: enforce the 64-byte rule at formatter boundary; callers must not be able
        // to send us an id that blows past the limit silently.
        String tooLongId = "x".repeat(80);
        Map<String, Object> payload = Map.of(
            "checkpointId", tooLongId,
            "role", "REVIEWER"
        );

        List<SendMessageRequest> out = formatter.format(
            CHAT_ID, null, "CHECKPOINT_REQUESTED", payload
        );

        // We choose: still emit a message BUT drop the keyboard (approve/reject via link).
        // Alternative would be throwing — keyboard drop is safer for a formatter.
        assertThat(out).hasSize(1);
        assertThat(out.get(0).reply_markup()).isNull();
    }

    @Test
    void unknownEventReturnsEmptyList() {
        // WHY: formatter must be forward-compatible. An unknown event name should be
        // a silent no-op, not a crash or a spammy generic message — callers
        // (TelegramEventChannel, Wave 2) already decide whether to route.
        List<SendMessageRequest> out = formatter.format(
            CHAT_ID, null, "SOME_FUTURE_EVENT", Map.of("foo", "bar")
        );
        assertThat(out).isEmpty();
    }

    @Test
    void pipelineCompletedEventProducesSingleMessage() {
        List<SendMessageRequest> out = formatter.format(
            CHAT_ID, null, "PIPELINE_COMPLETED", Map.of()
        );
        assertThat(out).hasSize(1);
        assertThat(out.get(0).text()).contains("completed");
    }

    @Test
    void pipelineFailedIncludesReason() {
        List<SendMessageRequest> out = formatter.format(
            CHAT_ID, null, "PIPELINE_FAILED",
            Map.of("reason", "arbiter timed out (waited 120s)")
        );
        assertThat(out).hasSize(1);
        String text = out.get(0).text();
        assertThat(text.toLowerCase()).contains("failed");
        // Escaped parens + dashes
        assertThat(text).contains("\\(waited 120s\\)");
    }

    @Test
    void threadIdIsPropagatedWhenProvided() {
        List<SendMessageRequest> out = formatter.format(
            CHAT_ID, 42, "ROLE_STARTED", Map.of("role", "PM")
        );
        assertThat(out).hasSize(1);
        assertThat(out.get(0).message_thread_id()).isEqualTo(42);
    }

    @Test
    void threadIdIsNullWhenNotProvided() {
        List<SendMessageRequest> out = formatter.format(
            CHAT_ID, null, "ROLE_STARTED", Map.of("role", "PM")
        );
        assertThat(out).hasSize(1);
        assertThat(out.get(0).message_thread_id()).isNull();
    }
}
