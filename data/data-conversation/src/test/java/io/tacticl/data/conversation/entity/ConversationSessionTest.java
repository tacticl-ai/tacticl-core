package io.tacticl.data.conversation.entity;

import io.tacticl.data.cloudorchestrator.entity.SessionMode;
import io.tacticl.data.cloudorchestrator.entity.SessionStatus;
import io.tacticl.data.cloudorchestrator.entity.Turn;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class ConversationSessionTest {

    @Test
    void create_setsIdleStatusAndDefaults() {
        ConversationSession s = ConversationSession.create("user-1", "build me a login page");
        assertThat(s.getId()).isNotBlank();
        assertThat(s.getUserId()).isEqualTo("user-1");
        // ConversationSession.create() intentionally defaults to GATHERING (legacy bridge
        // for the pre-orchestrator ConversationService contract). The new
        // CloudAgentSessionWorkflow explicitly transitions IDLE → ENGAGED via changeStatus
        // on its own path. See entity Javadoc on the `status` field.
        assertThat(s.getStatus()).isEqualTo(SessionStatus.GATHERING);
        assertThat(s.getMode()).isEqualTo(SessionMode.TEXT_ONLY);
        assertThat(s.getTurns()).isEmpty();
        assertThat(s.getSessionStartedSparkIds()).isEmpty();
        assertThat(s.getCostAccumulator()).isNotNull();
        assertThat(s.getCostCeilingUsd()).isEqualTo(5.0);
        // Legacy fields default but stay empty
        assertThat(s.getMessages()).isEmpty();
    }

    @Test
    void appendTurn_addsToTurnsList() {
        ConversationSession s = ConversationSession.create("user-1", "hi");
        s.appendTurn(Turn.user("hello", "text"));
        s.appendTurn(Turn.assistant("product-manager", "hi there", "text"));
        assertThat(s.getTurns()).hasSize(2);
        assertThat(s.getTurns().get(1).getPersonaId()).isEqualTo("product-manager");
    }

    @Test
    void recordStartedSpark_appendsToList() {
        ConversationSession s = ConversationSession.create("user-1", "hi");
        s.recordStartedSpark("spark-a");
        s.recordStartedSpark("spark-b");
        assertThat(s.getSessionStartedSparkIds()).containsExactly("spark-a", "spark-b");
    }

    @Test
    void changeStatus_transitionsStatus() {
        ConversationSession s = ConversationSession.create("user-1", "test");
        s.changeStatus(SessionStatus.GATHERING);
        assertThat(s.getStatus()).isEqualTo(SessionStatus.GATHERING);
        s.changeStatus(SessionStatus.PROPOSING);
        assertThat(s.getStatus()).isEqualTo(SessionStatus.PROPOSING);
    }

    @Test
    void focusOn_setsFocusedPipelineId() {
        ConversationSession s = ConversationSession.create("user-1", "test");
        s.focusOn("pipeline-1");
        assertThat(s.getFocusedPipelineId()).isEqualTo("pipeline-1");
        s.clearFocus();
        assertThat(s.getFocusedPipelineId()).isNull();
    }

    @Test
    void changeMode_updatesMode() {
        ConversationSession s = ConversationSession.create("user-1", "test");
        s.changeMode(SessionMode.VOICE_ACTIVE);
        assertThat(s.getMode()).isEqualTo(SessionMode.VOICE_ACTIVE);
    }

    @Test
    void title_truncatesLongInput() {
        String longInput = "a".repeat(100);
        ConversationSession s = ConversationSession.create("user-1", longInput);
        assertThat(s.getTitle().length()).isLessThanOrEqualTo(60);
    }

    @Test
    void createForTelegramGroupSetsProjectIdAndSource() {
        ConversationSession s = ConversationSession.createForTelegramGroup(
            "user-1", "proj-1", "build me a daily summary bot");
        assertThat(s.getUserId()).isEqualTo("user-1");
        assertThat(s.getProjectId()).isEqualTo("proj-1");
        assertThat(s.getInitiatorSource()).isEqualTo("TELEGRAM_GROUP");
        // Inherits GATHERING default from create() — see legacy-bridge note above.
        assertThat(s.getStatus()).isEqualTo(SessionStatus.GATHERING);
        assertThat(s.getRepoUrl()).isNull();
    }

    @Test
    void setRepoUrlUpdatesUpdatedAt() {
        ConversationSession s = ConversationSession.create("user-1", "hello");
        Instant before = Instant.now();
        s.setRepoUrl("https://github.com/foo/bar");
        Instant after = Instant.now();
        assertThat(s.getRepoUrl()).isEqualTo("https://github.com/foo/bar");
        assertThat(s.getUpdatedAt()).isBetween(before, after);
    }
}
