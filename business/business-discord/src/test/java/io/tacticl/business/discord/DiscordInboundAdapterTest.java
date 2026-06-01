package io.tacticl.business.discord;

import io.tacticl.business.pipeline.ingress.ChannelType;
import io.tacticl.business.pipeline.ingress.IngressKind;
import io.tacticl.business.pipeline.ingress.IngressRequest;
import io.tacticl.data.pipeline.entity.CheckpointDecision;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscordInboundAdapterTest {

    private final DiscordInboundAdapter adapter = new DiscordInboundAdapter();

    @Test
    void normalize_slashCommand_returnsExplicitTriggerWithPrompt() {
        Map<String, Object> interaction = Map.of(
            "id", "int-1",
            "type", 2,
            "guild_id", "guild-9",
            "channel", Map.of("id", "chan-7"),
            "data", Map.of(
                "type", 1,
                "name", "pdlc",
                "options", List.of(Map.of("name", "prompt", "value", "build a login page"))
            )
        );

        IngressRequest req = adapter.normalize(interaction, "user-42");

        assertThat(req.kind()).isEqualTo(IngressKind.EXPLICIT_TRIGGER);
        assertThat(req.text()).isEqualTo("build a login page");
        assertThat(req.tacticlUserId()).isEqualTo("user-42");
        assertThat(req.correlationId()).isEqualTo("int-1");
        assertThat(req.origin().channel()).isEqualTo(ChannelType.DISCORD);
        assertThat(req.origin().externalKey()).isEqualTo("guild-9:chan-7");
        assertThat(req.origin().destinationHandle()).isEqualTo("chan-7");
    }

    @Test
    void normalize_contextMenu_pullsTargetedMessageContent() {
        Map<String, Object> interaction = Map.of(
            "id", "int-2",
            "type", 2,
            "guild_id", "guild-9",
            "channel_id", "chan-7",
            "data", Map.of(
                "type", 3,
                "name", "Send to PDLC",
                "target_id", "msg-100",
                "resolved", Map.of(
                    "messages", Map.of("msg-100", Map.of("content", "fix the flaky test"))
                )
            )
        );

        IngressRequest req = adapter.normalize(interaction, "user-42");

        assertThat(req.kind()).isEqualTo(IngressKind.EXPLICIT_TRIGGER);
        assertThat(req.text()).isEqualTo("fix the flaky test");
        // channel_id fallback (no channel object) still resolves the destination.
        assertThat(req.origin().externalKey()).isEqualTo("guild-9:chan-7");
        assertThat(req.origin().destinationHandle()).isEqualTo("chan-7");
    }

    @Test
    void normalize_approveButton_returnsCheckpointDecisionApproved() {
        IngressRequest req = adapter.normalize(buttonInteraction("pdlc:approve:spark-5:cp-3"), "user-42");

        assertThat(req.kind()).isEqualTo(IngressKind.CHECKPOINT_DECISION);
        assertThat(req.decision()).isNotNull();
        assertThat(req.decision().sparkId()).isEqualTo("spark-5");
        assertThat(req.decision().checkpointId()).isEqualTo("cp-3");
        assertThat(req.decision().decision()).isEqualTo(CheckpointDecision.APPROVED);
        // The host message id anchors run-update grouping.
        assertThat(req.origin().threadHandle()).isEqualTo("host-msg-1");
    }

    @Test
    void normalize_changesButton_mapsToRework() {
        IngressRequest req = adapter.normalize(buttonInteraction("pdlc:changes:spark-5:cp-3"), "user-42");

        assertThat(req.kind()).isEqualTo(IngressKind.CHECKPOINT_DECISION);
        assertThat(req.decision().decision()).isEqualTo(CheckpointDecision.REWORK);
    }

    @Test
    void normalize_rejectButton_mapsToCancelRunKind() {
        IngressRequest req = adapter.normalize(buttonInteraction("pdlc:reject:spark-5:cp-3"), "user-42");

        // A reject decision is a cancel — routed as CANCEL_RUN, carrying the spark id.
        assertThat(req.kind()).isEqualTo(IngressKind.CANCEL_RUN);
        assertThat(req.decision().decision()).isEqualTo(CheckpointDecision.CANCEL);
        assertThat(req.decision().sparkId()).isEqualTo("spark-5");
    }

    @Test
    void normalize_unlinkedUser_stillNormalizesWithNullUserId() {
        // Normalization is pure and does not enforce the link precondition — the dispatcher does.
        IngressRequest req = adapter.normalize(buttonInteraction("pdlc:approve:s:c"), null);
        assertThat(req.tacticlUserId()).isNull();
    }

    @Test
    void normalize_slashCommandMissingPrompt_throws() {
        Map<String, Object> interaction = Map.of(
            "id", "int-3", "type", 2, "guild_id", "g", "channel_id", "c",
            "data", Map.of("type", 1, "name", "pdlc", "options", List.of())
        );
        assertThatThrownBy(() -> adapter.normalize(interaction, "user-42"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalize_unknownButtonVerb_throws() {
        assertThatThrownBy(() -> adapter.normalize(buttonInteraction("pdlc:explode:s:c"), "user-42"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalize_malformedCustomId_throws() {
        assertThatThrownBy(() -> adapter.normalize(buttonInteraction("garbage"), "user-42"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalize_unsupportedInteractionType_throws() {
        assertThatThrownBy(() -> adapter.normalize(Map.of("id", "x", "type", 99), "user-42"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalize_nullInteraction_throws() {
        assertThatThrownBy(() -> adapter.normalize(null, "user-42"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static Map<String, Object> buttonInteraction(String customId) {
        return Map.of(
            "id", "int-btn",
            "type", 3,
            "guild_id", "guild-9",
            "channel", Map.of("id", "chan-7"),
            "message", Map.of("id", "host-msg-1"),
            "data", Map.of("custom_id", customId, "component_type", 2)
        );
    }
}
