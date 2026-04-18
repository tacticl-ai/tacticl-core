package io.tacticl.data.profile.repository;

import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.reverse.TransitionWalker;
import io.tacticl.data.profile.entity.UserProfile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
class UserProfileRepositoryTest {

    /** Minimal Spring Boot config so @DataMongoTest can find a configuration root. */
    @SpringBootApplication
    static class TestConfig { }

    private static final TransitionWalker.ReachedState<RunningMongodProcess> MONGOD =
            Mongod.instance().start(Version.Main.V7_0);

    @Autowired
    private UserProfileRepository repository;

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
        repository.deleteAll();
    }

    @Test
    void findByCidadelUserIdAndIsActiveTrue_returnsProfile_whenActive() {
        var profile = UserProfile.create("user-1", "Gabriel J.", "g@example.com");
        repository.save(profile);

        var result = repository.findByCidadelUserIdAndIsActiveTrue("user-1");

        assertThat(result).isPresent();
        assertThat(result.get().getCidadelUserId()).isEqualTo("user-1");
        assertThat(result.get().getDisplayName()).isEqualTo("Gabriel J.");
        assertThat(result.get().getEmail()).isEqualTo("g@example.com");
    }

    @Test
    void findByCidadelUserIdAndIsActiveTrue_returnsEmpty_whenInactive() {
        var profile = UserProfile.create("user-2", "Soft Deleted", "deleted@example.com");
        repository.save(profile);

        profile.delete(); // sets isActive = false
        repository.save(profile);

        var result = repository.findByCidadelUserIdAndIsActiveTrue("user-2");

        assertThat(result).isEmpty();
    }

    @Test
    void findByCidadelUserIdAndIsActiveTrue_returnsEmpty_whenNotFound() {
        var result = repository.findByCidadelUserIdAndIsActiveTrue("nonexistent");

        assertThat(result).isEmpty();
    }
}
