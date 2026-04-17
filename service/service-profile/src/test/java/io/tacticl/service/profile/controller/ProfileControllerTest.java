package io.tacticl.service.profile.controller;

import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.cidadel.framework.exception.CidadelException;
import io.tacticl.business.profile.ProfileErrorCode;
import io.tacticl.business.profile.service.UserProfileService;
import io.tacticl.data.profile.entity.UserProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    @Mock
    private UserProfileService userProfileService;

    @InjectMocks
    private ProfileController profileController;

    private AuthenticatedUser mockUser() {
        return mock(AuthenticatedUser.class);
    }

    @Test
    void getProfile_returns200_withDisplayNameAndEmail() {
        var profile = UserProfile.create("user-1", "Gabriel J.", "g@example.com");
        when(userProfileService.getOrCreate(any(AuthenticatedUser.class))).thenReturn(profile);

        var response = profileController.getProfile(mockUser());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().displayName()).isEqualTo("Gabriel J.");
        assertThat(response.getBody().email()).isEqualTo("g@example.com");
        assertThat(response.getBody().avatarUrl()).isNull();
    }

    @Test
    void getProfile_returns200_withAvatarUrl_whenProfileHasAvatar() {
        var profile = mock(UserProfile.class);
        when(profile.getDisplayName()).thenReturn("Gabriel J.");
        when(profile.getEmail()).thenReturn("g@example.com");
        when(profile.getAvatarUrl()).thenReturn("https://example.com/avatar.png");
        when(userProfileService.getOrCreate(any(AuthenticatedUser.class))).thenReturn(profile);

        var response = profileController.getProfile(mockUser());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().displayName()).isEqualTo("Gabriel J.");
        assertThat(response.getBody().email()).isEqualTo("g@example.com");
        assertThat(response.getBody().avatarUrl()).isEqualTo("https://example.com/avatar.png");
    }

    @Test
    void getProfile_propagatesException_whenServiceThrows() {
        when(userProfileService.getOrCreate(any(AuthenticatedUser.class)))
            .thenThrow(new CidadelException(ProfileErrorCode.INVALID_TOKEN_CLAIMS, "business-profile", "Token missing name claim"));

        assertThatThrownBy(() ->
            profileController.getProfile(mockUser())
        ).isInstanceOf(CidadelException.class);
    }
}
