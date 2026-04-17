package io.tacticl.data.profile.repository;

import io.tacticl.data.profile.entity.UserProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileRepositoryTest {

    @Mock
    private UserProfileRepository repository;

    @Test
    void findByCidadelUserIdAndIsActiveTrue_returnsProfile_whenStubbed() {
        var profile = UserProfile.create("user-1", "Gabriel J.", "g@example.com");
        when(repository.findByCidadelUserIdAndIsActiveTrue("user-1")).thenReturn(Optional.of(profile));

        var result = repository.findByCidadelUserIdAndIsActiveTrue("user-1");

        assertThat(result).isPresent();
        assertThat(result.get().getDisplayName()).isEqualTo("Gabriel J.");
    }

    @Test
    void findByCidadelUserIdAndIsActiveTrue_returnsEmpty_whenNotFound() {
        when(repository.findByCidadelUserIdAndIsActiveTrue("nonexistent")).thenReturn(Optional.empty());

        var result = repository.findByCidadelUserIdAndIsActiveTrue("nonexistent");

        assertThat(result).isEmpty();
    }
}
