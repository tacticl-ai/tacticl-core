package io.tacticl.data.telegram.repository;

import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.reverse.TransitionWalker;
import io.tacticl.data.telegram.entity.MemberRole;
import io.tacticl.data.telegram.entity.TelegramMemberGrant;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataMongoTest
class TelegramProjectRepositoriesIntegrationTest {

    /** Minimal Spring Boot config so @DataMongoTest can find a configuration root. */
    @SpringBootApplication
    static class TestConfig { }

    private static final TransitionWalker.ReachedState<RunningMongodProcess> MONGOD =
            Mongod.instance().start(Version.Main.V7_0);

    @Autowired
    TelegramProjectLinkRepository projectRepo;

    @Autowired
    TelegramMemberGrantRepository grantRepo;

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
        projectRepo.deleteAll();
        grantRepo.deleteAll();
    }

    @Test
    void chatIdIsUnique() {
        projectRepo.save(TelegramProjectLink.create("p-1", 100L, "u-a", "Group A"));
        var dup = TelegramProjectLink.create("p-2", 100L, "u-b", "Group B");
        assertThatThrownBy(() -> projectRepo.save(dup))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void concurrentActiveLinkInsertion_violatesUniqueIndex() {
        // Models the race: two concurrent /init handlers each see no existing link
        // and proceed to insert. The partial unique index must reject the second write.
        projectRepo.save(TelegramProjectLink.create("p-1", 500L, "u-a", "Group A"));
        var racingInsert = TelegramProjectLink.create("p-2", 500L, "u-b", "Group B");
        assertThatThrownBy(() -> projectRepo.save(racingInsert))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void archivedThenReactivated_doesNotViolateIndex() {
        // /archive then re-/init must succeed: the inactive row is excluded from
        // the partial filter, so a new active row for the same chatId is permitted.
        var first = projectRepo.save(TelegramProjectLink.create("p-1", 600L, "u-a", "Group A"));
        first.delete();
        projectRepo.save(first);
        var reinit = projectRepo.save(TelegramProjectLink.create("p-2", 600L, "u-b", "Group B"));
        assertThat(reinit.getId()).isNotEqualTo(first.getId());
        assertThat(reinit.isActive()).isTrue();
    }

    @Test
    void findByChatIdReturnsMatch() {
        projectRepo.save(TelegramProjectLink.create("p-1", 200L, "u-a", "G"));
        var found = projectRepo.findByChatIdAndIsActiveTrue(200L);
        assertThat(found).isPresent();
        assertThat(found.get().getProjectId()).isEqualTo("p-1");
    }

    @Test
    void softDeletedChatIdDoesNotBlockReuse() {
        var first = projectRepo.save(TelegramProjectLink.create("p-1", 300L, "u-a", "Group A"));
        first.delete(); // BaseMongoEntity soft-delete -> isActive=false
        projectRepo.save(first);
        var replacement = projectRepo.save(TelegramProjectLink.create("p-2", 300L, "u-b", "Group B"));
        assertThat(replacement.getId()).isNotEqualTo(first.getId());
    }

    @Test
    void grantProjectUserUnique() {
        grantRepo.save(TelegramMemberGrant.create("p-1", 1L, "u-1", 10L, MemberRole.OBSERVER, "owner"));
        var dup = TelegramMemberGrant.create("p-1", 1L, "u-1", 10L, MemberRole.RUNNER, "owner");
        assertThatThrownBy(() -> grantRepo.save(dup))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void findByProjectReturnsMembers() {
        grantRepo.save(TelegramMemberGrant.create("p-1", 1L, "u-1", 10L, MemberRole.RUNNER, "o"));
        grantRepo.save(TelegramMemberGrant.create("p-1", 1L, "u-2", 20L, MemberRole.OBSERVER, "o"));
        grantRepo.save(TelegramMemberGrant.create("p-2", 2L, "u-1", 10L, MemberRole.OWNER, "o"));
        assertThat(grantRepo.findByProjectIdAndIsActiveTrue("p-1")).hasSize(2);
    }
}
