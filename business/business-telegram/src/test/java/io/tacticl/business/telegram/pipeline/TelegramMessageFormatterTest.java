package io.tacticl.business.telegram.pipeline;

import io.tacticl.business.pipeline.dto.PipelineCallbackEvent;
import io.tacticl.client.telegram.dto.InlineKeyboardButton;
import io.tacticl.client.telegram.dto.InlineKeyboardMarkup;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramMessageFormatterTest {

    private static final long CHAT_ID = 123_456_789L;
    private static final String RUN_ID = "run-1";

    private final TelegramMessageFormatter formatter = new TelegramMessageFormatter();

    @Test
    void roleStartedEventProducesSinglePlainMessageNoKeyboard() {
        PipelineCallbackEvent event = new PipelineCallbackEvent(
            RUN_ID, "ROLE_STARTED", "RESEARCHER", "DISCOVERY", null);

        List<SendMessageRequest> out = formatter.format(CHAT_ID, null, event);

        assertThat(out).hasSize(1);
        SendMessageRequest msg = out.get(0);
        assertThat(msg.chat_id()).isEqualTo(CHAT_ID);
        assertThat(msg.reply_markup()).isNull();
        assertThat(msg.parse_mode()).isEqualTo("MarkdownV2");
        assertThat(msg.text()).contains("RESEARCHER");
        assertThat(msg.text().length()).isLessThanOrEqualTo(4096);
    }

    @Test
    void roleCompletedWithArtifactPathEscapesSpecialChars() {
        // WHY: MarkdownV2 requires escaping of _ * [ ] ( ) ~ ` > # + - = | { } . !
        // A realistic artifact path contains many of these (dots, underscores, dashes).
        String json = "{\"artifactPath\":\"docs/specs/2026-04-19_system-design_v1.2.md\"}";
        PipelineCallbackEvent event = new PipelineCallbackEvent(
            RUN_ID, "ROLE_COMPLETED", "ARCHITECT", "DESIGN", json);

        List<SendMessageRequest> out = formatter.format(CHAT_ID, null, event);

        assertThat(out).hasSize(1);
        String text = out.get(0).text();
        assertThat(text).contains("ARCHITECT");
        assertThat(text).contains("2026\\-04\\-19");
        assertThat(text).contains("system\\-design");
        assertThat(text).contains("v1\\.2\\.md");
        assertThat(text).contains("\\_");
    }

    @Test
    void overflowTruncatesAndAppendsArtifactUrlWhenAvailable() {
        String longMessage = "x".repeat(10_000);
        String json = "{\"message\":\"" + longMessage + "\",\"artifactUrl\":\"https://example.com/a/b\"}";
        PipelineCallbackEvent event = new PipelineCallbackEvent(
            RUN_ID, "ROLE_COMPLETED", "TESTER", "TEST", json);

        List<SendMessageRequest> out = formatter.format(CHAT_ID, null, event);

        assertThat(out).hasSize(1);
        String text = out.get(0).text();
        assertThat(text.length()).isLessThanOrEqualTo(4096);
        assertThat(text).contains("https://example\\.com/a/b");
    }

    @Test
    void overflowWithoutArtifactUrlTruncatesWithEllipsis() {
        String longMessage = "y".repeat(10_000);
        String json = "{\"message\":\"" + longMessage + "\"}";
        PipelineCallbackEvent event = new PipelineCallbackEvent(
            RUN_ID, "ROLE_COMPLETED", "IMPLEMENTER", "IMPL", json);

        List<SendMessageRequest> out = formatter.format(CHAT_ID, null, event);

        assertThat(out).hasSize(1);
        String text = out.get(0).text();
        assertThat(text.length()).isLessThanOrEqualTo(4096);
        assertThat(text).endsWith("…");
    }

    @Test
    void overflowTruncationDoesNotLeaveDanglingEscapeBackslash() {
        // WHY: security review #6 (low) — if truncation lands on a backslash of a
        // MarkdownV2 escape pair, Telegram rejects the message. Build a message where
        // the natural cut point would be an escape-backslash and verify we back up.
        // "aaaa…" then many dots → each dot escapes to "\.", 2 chars. At length 4095 the
        // last char of the kept prefix may well be "\\" — the formatter must trim it.
        String longMessage = "a".repeat(3500) + ".".repeat(2000);
        String json = "{\"message\":\"" + longMessage + "\"}";
        PipelineCallbackEvent event = new PipelineCallbackEvent(
            RUN_ID, "ROLE_COMPLETED", "TESTER", "TEST", json);

        List<SendMessageRequest> out = formatter.format(CHAT_ID, null, event);

        assertThat(out).hasSize(1);
        String text = out.get(0).text();
        assertThat(text.length()).isLessThanOrEqualTo(4096);
        // The char immediately before the suffix "…" must not be a lone backslash.
        int ellipsisIdx = text.lastIndexOf('…');
        assertThat(ellipsisIdx).isGreaterThan(0);
        assertThat(text.charAt(ellipsisIdx - 1))
            .as("character before suffix must not be a dangling escape backslash")
            .isNotEqualTo('\\');
    }

    @Test
    void checkpointPendingEventIncludesThreeButtonKeyboardWithSafeCallbackData() {
        String checkpointId = "cp-abc123def456";
        String json = "{\"checkpointId\":\"" + checkpointId + "\"}";
        PipelineCallbackEvent event = new PipelineCallbackEvent(
            RUN_ID, "CHECKPOINT_REQUESTED", "REVIEWER", "REVIEW", json);

        List<SendMessageRequest> out = formatter.format(CHAT_ID, null, event);

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

        for (InlineKeyboardButton button : row) {
            byte[] bytes = button.callback_data().getBytes(StandardCharsets.UTF_8);
            assertThat(bytes.length)
                .as("callback_data must be <=64 bytes for button '%s'", button.text())
                .isLessThanOrEqualTo(64);
        }
    }

    @Test
    void checkpointWithOverlyLongIdDropsKeyboard() {
        // WHY: enforce the 64-byte rule at formatter boundary; callers must not be able
        // to send us an id that blows past the limit silently.
        String tooLongId = "x".repeat(80);
        String json = "{\"checkpointId\":\"" + tooLongId + "\"}";
        PipelineCallbackEvent event = new PipelineCallbackEvent(
            RUN_ID, "CHECKPOINT_REQUESTED", "REVIEWER", "REVIEW", json);

        List<SendMessageRequest> out = formatter.format(CHAT_ID, null, event);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).reply_markup()).isNull();
    }

    @Test
    void checkpointWithoutCheckpointIdDropsKeyboard() {
        // WHY: emitter must always supply checkpointId; but if it doesn't, we must not
        // produce a keyboard with a dangling "cp:approve:" prefix.
        PipelineCallbackEvent event = new PipelineCallbackEvent(
            RUN_ID, "CHECKPOINT_REQUESTED", "REVIEWER", "REVIEW", null);

        List<SendMessageRequest> out = formatter.format(CHAT_ID, null, event);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).reply_markup()).isNull();
    }

    @Test
    void unknownEventReturnsEmptyList() {
        PipelineCallbackEvent event = new PipelineCallbackEvent(
            RUN_ID, "SOME_FUTURE_EVENT", null, null, "{\"foo\":\"bar\"}");
        List<SendMessageRequest> out = formatter.format(CHAT_ID, null, event);
        assertThat(out).isEmpty();
    }

    @Test
    void nullEventReturnsEmptyList() {
        assertThat(formatter.format(CHAT_ID, null, null)).isEmpty();
    }

    @Test
    void pipelineStartedEventProducesSingleMessage() {
        PipelineCallbackEvent event = new PipelineCallbackEvent(
            RUN_ID, "PIPELINE_STARTED", null, null, null);
        List<SendMessageRequest> out = formatter.format(CHAT_ID, null, event);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).text()).contains("started");
    }

    @Test
    void pipelineCompletedEventProducesSingleMessage() {
        PipelineCallbackEvent event = new PipelineCallbackEvent(
            RUN_ID, "PIPELINE_COMPLETED", null, null, null);
        List<SendMessageRequest> out = formatter.format(CHAT_ID, null, event);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).text()).contains("completed");
    }

    @Test
    void pipelineCancelledEventProducesSingleMessage() {
        PipelineCallbackEvent event = new PipelineCallbackEvent(
            RUN_ID, "PIPELINE_CANCELLED", null, null, null);
        List<SendMessageRequest> out = formatter.format(CHAT_ID, null, event);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).text()).contains("cancelled");
    }

    @Test
    void pipelineFailedIncludesReason() {
        String json = "{\"reason\":\"arbiter timed out (waited 120s)\"}";
        PipelineCallbackEvent event = new PipelineCallbackEvent(
            RUN_ID, "PIPELINE_FAILED", null, null, json);

        List<SendMessageRequest> out = formatter.format(CHAT_ID, null, event);
        assertThat(out).hasSize(1);
        String text = out.get(0).text();
        assertThat(text.toLowerCase()).contains("failed");
        assertThat(text).contains("\\(waited 120s\\)");
    }

    @Test
    void roleReworkEventIncludesRoleName() {
        PipelineCallbackEvent event = new PipelineCallbackEvent(
            RUN_ID, "ROLE_REWORK", "IMPLEMENTER", "IMPL", null);

        List<SendMessageRequest> out = formatter.format(CHAT_ID, null, event);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).text()).contains("IMPLEMENTER");
        assertThat(out.get(0).text()).contains("rework");
    }

    @Test
    void threadIdIsPropagatedWhenProvided() {
        PipelineCallbackEvent event = new PipelineCallbackEvent(
            RUN_ID, "ROLE_STARTED", "PM", "PLAN", null);
        List<SendMessageRequest> out = formatter.format(CHAT_ID, 42, event);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).message_thread_id()).isEqualTo(42);
    }

    @Test
    void threadIdIsNullWhenNotProvided() {
        PipelineCallbackEvent event = new PipelineCallbackEvent(
            RUN_ID, "ROLE_STARTED", "PM", "PLAN", null);
        List<SendMessageRequest> out = formatter.format(CHAT_ID, null, event);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).message_thread_id()).isNull();
    }

    @Test
    void invalidPayloadJsonIsSurvivable() {
        // WHY: defensive — a malformed payloadJson must not crash the formatter on the
        // hot path. The role completion header still renders.
        PipelineCallbackEvent event = new PipelineCallbackEvent(
            RUN_ID, "ROLE_COMPLETED", "PM", "PLAN", "{this is not json");

        List<SendMessageRequest> out = formatter.format(CHAT_ID, null, event);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).text()).contains("PM");
    }
}
