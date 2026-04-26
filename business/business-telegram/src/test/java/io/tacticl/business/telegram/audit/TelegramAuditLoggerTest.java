package io.tacticl.business.telegram.audit;

import io.tacticl.data.telegram.entity.TelegramAuditLog;
import io.tacticl.data.telegram.repository.TelegramAuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TelegramAuditLoggerTest {

    private TelegramAuditLogRepository repo;
    private TelegramAuditLogger logger;

    @BeforeEach
    void setUp() {
        repo = mock(TelegramAuditLogRepository.class);
        logger = new TelegramAuditLogger(repo);
    }

    @Test
    void recordPersistsRowWithAllFields() {
        logger.record(-100L, 42L, "user-abc", "GRANT", "{\"target\":\"@bob\",\"role\":\"RUNNER\"}");

        ArgumentCaptor<TelegramAuditLog> captor = ArgumentCaptor.forClass(TelegramAuditLog.class);
        verify(repo).save(captor.capture());
        TelegramAuditLog saved = captor.getValue();
        assertThat(saved.getChatId()).isEqualTo(-100L);
        assertThat(saved.getTelegramUserId()).isEqualTo(42L);
        assertThat(saved.getTacticlUserId()).isEqualTo("user-abc");
        assertThat(saved.getAction()).isEqualTo("GRANT");
        assertThat(saved.getPayloadJson()).isEqualTo("{\"target\":\"@bob\",\"role\":\"RUNNER\"}");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getSource()).isEqualTo(TelegramAuditLog.DEFAULT_SOURCE);
    }

    @Test
    void recordAllowsNullTacticlUserIdForPreLinkActivity() {
        logger.record(-100L, 42L, null, "INIT", "{}");

        ArgumentCaptor<TelegramAuditLog> captor = ArgumentCaptor.forClass(TelegramAuditLog.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getTacticlUserId()).isNull();
    }

    @Test
    void recordSwallowsRepositoryFailures() {
        // WHY: forensic logging must never block a user-facing command. The repo
        // could be down, mongo could reject the doc — none of that should fail
        // the handler's primary action.
        doThrow(new RuntimeException("mongo down")).when(repo).save(any());

        logger.record(-100L, 42L, "user-abc", "GRANT", "{}");
        // No exception escapes; method returns normally.
    }
}
