package io.tacticl.business.profile.service;

import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.cidadel.framework.exception.CidadelException;
import io.tacticl.data.profile.entity.UserProfile;
import io.tacticl.data.profile.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    private UserProfileService userProfileService;

    @BeforeEach
    void setUp() {
        userProfileService = new UserProfileService(userProfileRepository);
    }

    private AuthenticatedUser user(String userId, String name, String email) {
        return AuthenticatedUser.builder()
            .userId(userId).name(name).email(email).build();
    }

    @Test
    void getOrCreate_returnsExisting_whenProfileFound() {
        var existing = UserProfile.create("u1", "Gabriel", "g@example.com");
        when(userProfileRepository.findByCidadelUserIdAndIsActiveTrue("u1"))
            .thenReturn(Optional.of(existing));

        var result = userProfileService.getOrCreate(user("u1", "Gabriel", "g@example.com"));

        assertThat(result.getDisplayName()).isEqualTo("Gabriel");
        verify(userProfileRepository, never()).save(any());
    }

    @Test
    void getOrCreate_createsAndReturnsProfile_whenNotFound() {
        var created = UserProfile.create("u2", "New User", "new@example.com");
        when(userProfileRepository.findByCidadelUserIdAndIsActiveTrue("u2"))
            .thenReturn(Optional.empty());
        when(userProfileRepository.save(any())).thenReturn(created);

        var result = userProfileService.getOrCreate(user("u2", "New User", "new@example.com"));

        assertThat(result.getDisplayName()).isEqualTo("New User");
        verify(userProfileRepository).save(any(UserProfile.class));
    }

    @Test
    void getOrCreate_reReadsOnDuplicateKey_andReturnsExisting() {
        var existing = UserProfile.create("u3", "Race User", "race@example.com");
        when(userProfileRepository.findByCidadelUserIdAndIsActiveTrue("u3"))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(existing));
        when(userProfileRepository.save(any())).thenThrow(new DuplicateKeyException("dup"));

        var result = userProfileService.getOrCreate(user("u3", "Race User", "race@example.com"));

        assertThat(result.getDisplayName()).isEqualTo("Race User");
    }

    @Test
    void getOrCreate_throws_whenUserIdIsNull() {
        var nullIdUser = mock(AuthenticatedUser.class);
        when(nullIdUser.getUserId()).thenReturn(null);
        assertThatThrownBy(() -> userProfileService.getOrCreate(nullIdUser))
            .isInstanceOf(CidadelException.class);
    }

    @Test
    void getOrCreate_throws_whenNameIsNull() {
        assertThatThrownBy(() -> userProfileService.getOrCreate(user("u4", null, "e@example.com")))
            .isInstanceOf(CidadelException.class);
    }

    @Test
    void getOrCreate_throws_whenEmailIsNull() {
        assertThatThrownBy(() -> userProfileService.getOrCreate(user("u5", "Name", null)))
            .isInstanceOf(CidadelException.class);
    }
}
