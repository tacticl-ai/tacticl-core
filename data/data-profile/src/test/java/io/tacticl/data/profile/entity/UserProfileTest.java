package io.tacticl.data.profile.entity;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class UserProfileTest {

    @Test
    void create_setsFieldsCorrectly() {
        var profile = UserProfile.create("user-1", "Gabriel J.", "g@example.com");
        assertThat(profile.getCidadelUserId()).isEqualTo("user-1");
        assertThat(profile.getDisplayName()).isEqualTo("Gabriel J.");
        assertThat(profile.getEmail()).isEqualTo("g@example.com");
        assertThat(profile.getAvatarUrl()).isNull();
    }

    @Test
    void create_isActiveByDefault() {
        var profile = UserProfile.create("user-1", "Gabriel J.", "g@example.com");
        assertThat(profile.isActive()).isTrue();
    }
}
