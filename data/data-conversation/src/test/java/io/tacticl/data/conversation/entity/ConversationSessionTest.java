package io.tacticl.data.conversation.entity;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class ConversationSessionTest {

    @Test
    void create_setsGatheringStatus() {
        ConversationSession s = ConversationSession.create("user-1", "build me a login page");
        assertThat(s.getStatus()).isEqualTo(SessionStatus.GATHERING);
        assertThat(s.getUserId()).isEqualTo("user-1");
        assertThat(s.getMessages()).isEmpty();
        assertThat(s.getId()).isNotBlank();
    }

    @Test
    void addMessage_appendsToList() {
        ConversationSession s = ConversationSession.create("user-1", "test");
        s.addMessage(ConversationMessage.user("hello"));
        s.addMessage(ConversationMessage.assistant("hi"));
        assertThat(s.getMessages()).hasSize(2);
    }

    @Test
    void markProposing_transitionsStatus() {
        ConversationSession s = ConversationSession.create("user-1", "test");
        s.markProposing("CODE", "Build a React todo app with Node backend");
        assertThat(s.getStatus()).isEqualTo(SessionStatus.PROPOSING);
        assertThat(s.getDetectedSparkType()).isEqualTo("CODE");
        assertThat(s.getProposalText()).isEqualTo("Build a React todo app with Node backend");
    }

    @Test
    void markActive_setsSparkId() {
        ConversationSession s = ConversationSession.create("user-1", "test");
        s.markProposing("CODE", "my plan");
        s.markActive("spark-123");
        assertThat(s.getStatus()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(s.getSparkId()).isEqualTo("spark-123");
    }

    @Test
    void markActive_requiresProposingFirst() {
        ConversationSession s = ConversationSession.create("user-1", "test");
        assertThatThrownBy(() -> s.markActive("spark-123"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void revertToGathering_fromProposing() {
        ConversationSession s = ConversationSession.create("user-1", "test");
        s.markProposing("CODE", "my plan");
        s.revertToGathering();
        assertThat(s.getStatus()).isEqualTo(SessionStatus.GATHERING);
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
