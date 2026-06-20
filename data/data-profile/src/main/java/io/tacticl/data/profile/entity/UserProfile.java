package io.tacticl.data.profile.entity;

import io.tacticl.data.connections.base.BaseMongoEntity;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("user_profiles")
public class UserProfile extends BaseMongoEntity {

    @Indexed(unique = true)
    private String cidadelUserId;
    private String displayName;
    private String email;
    private String avatarUrl;

    private int maxConcurrentSparks = 3;
    private double spendingLimit = 0.0;
    private List<String> domainAllowlist = new ArrayList<>();
    private List<String> domainBlocklist = new ArrayList<>();

    public static UserProfile create(String cidadelUserId, String displayName, String email) {
        var profile = new UserProfile();
        profile.cidadelUserId = cidadelUserId;
        profile.displayName = displayName;
        profile.email = email;
        return profile;
    }

    public String getCidadelUserId() { return cidadelUserId; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getEmail() { return email; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public int getMaxConcurrentSparks() { return maxConcurrentSparks; }
    public void setMaxConcurrentSparks(int maxConcurrentSparks) { this.maxConcurrentSparks = maxConcurrentSparks; }

    public double getSpendingLimit() { return spendingLimit; }
    public void setSpendingLimit(double spendingLimit) { this.spendingLimit = spendingLimit; }

    public List<String> getDomainAllowlist() { return domainAllowlist; }
    public void setDomainAllowlist(List<String> domainAllowlist) { this.domainAllowlist = domainAllowlist; }

    public List<String> getDomainBlocklist() { return domainBlocklist; }
    public void setDomainBlocklist(List<String> domainBlocklist) { this.domainBlocklist = domainBlocklist; }
}
