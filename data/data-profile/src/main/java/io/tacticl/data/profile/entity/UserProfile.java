package io.tacticl.data.profile.entity;

import io.tacticl.data.profile.base.BaseMongoEntity;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("user_profiles")
public class UserProfile extends BaseMongoEntity {

    @Indexed(unique = true)
    private String cidadelUserId;
    private String displayName;
    private String email;
    private String avatarUrl;

    public static UserProfile create(String cidadelUserId, String displayName, String email) {
        var profile = new UserProfile();
        profile.cidadelUserId = cidadelUserId;
        profile.displayName = displayName;
        profile.email = email;
        return profile;
    }

    public String getCidadelUserId() { return cidadelUserId; }
    public String getDisplayName() { return displayName; }
    public String getEmail() { return email; }
    public String getAvatarUrl() { return avatarUrl; }
}
