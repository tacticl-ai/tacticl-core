package io.tacticl.data.telegram.repository;

import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.reverse.TransitionWalker;
import io.tacticl.data.telegram.entity.TelegramAuditLog;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
class TelegramAuditLogRepositoryIT {

    /** Minimal Spring Boot config so @DataMongoTest can find a configuration root. */
    @SpringBootApplication
    static class TestConfig { }

    private static final TransitionWalker.ReachedState<RunningMongodProcess> MONGOD =
            Mongod.instance().start(Version.Main.V7_0);

    @Autowired
    TelegramAuditLogRepository auditRepo;

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> {
            var addr = MONGOD.current().getServerAddress();
            return "mongodb://%s:%d/test".formatted(addr.getHost(), addr.getPort());
        });
    }

    @AfterAll
    static void stopMongo() {
        MONGOD.close();
    }

    @BeforeEach
    void setUp() {
        auditRepo.deleteAll();
    }

    @Test
    void insertAndFindByChatId_returnsRow() {
        var log = TelegramAuditLog.create(1000L, 42L, "u-1", "INIT", "{\"k\":\"v\"}");
        auditRepo.save(log);

        List<TelegramAuditLog> rows = auditRepo.findByChatIdOrderByCreatedAtDesc(1000L);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getAction()).isEqualTo("INIT");
        assertThat(rows.get(0).getSource()).isEqualTo("TELEGRAM_GROUP");
        assertThat(rows.get(0).getCreatedAt()).isNotNull();
    }

    @Test
    void insertAndFindByTelegramUserId_returnsRow() {
        auditRepo.save(TelegramAuditLog.create(1000L, 42L, "u-1", "GRANT", "{}"));
        auditRepo.save(TelegramAuditLog.create(2000L, 42L, null, "REVOKE", "{}"));
        auditRepo.save(TelegramAuditLog.create(3000L, 99L, "u-2", "SPARK", "{}"));

        List<TelegramAuditLog> rows = auditRepo.findByTelegramUserIdOrderByCreatedAtDesc(42L);

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(TelegramAuditLog::getAction).containsExactlyInAnyOrder("GRANT", "REVOKE");
    }

    @Test
    void findByChatIdOrderByCreatedAtDesc_ordersDescending() {
        // Three rows on same chat with controlled createdAt values; finder must
        // return newest first so the audit timeline reads top-down.
        var oldest = TelegramAuditLog.create(7777L, 1L, "u", "INIT", "{}");
        oldest.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        var middle = TelegramAuditLog.create(7777L, 1L, "u", "GRANT", "{}");
        middle.setCreatedAt(Instant.parse("2026-02-01T00:00:00Z"));
        var newest = TelegramAuditLog.create(7777L, 1L, "u", "REVOKE", "{}");
        newest.setCreatedAt(Instant.parse("2026-03-01T00:00:00Z"));
        auditRepo.save(oldest);
        auditRepo.save(middle);
        auditRepo.save(newest);

        List<TelegramAuditLog> rows = auditRepo.findByChatIdOrderByCreatedAtDesc(7777L);

        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(TelegramAuditLog::getAction)
                .containsExactly("REVOKE", "GRANT", "INIT");
    }

    @Test
    void nullableTacticlUserId_persistsAsNull() {
        // Audit rows can be written before a Telegram user is linked to a Tacticl
        // account, so tacticlUserId must accept null.
        var log = TelegramAuditLog.create(5000L, 10L, null, "INIT", "{}");
        auditRepo.save(log);

        var rows = auditRepo.findByChatIdOrderByCreatedAtDesc(5000L);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getTacticlUserId()).isNull();
    }

    @Test
    void createdAt_defaultsToNowWhenNotSet() {
        var before = Instant.now().minus(1, ChronoUnit.SECONDS);
        var log = TelegramAuditLog.create(6000L, 11L, "u", "SPARK", "{}");
        auditRepo.save(log);

        var rows = auditRepo.findByChatIdOrderByCreatedAtDesc(6000L);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getCreatedAt()).isAfterOrEqualTo(before);
    }
}
