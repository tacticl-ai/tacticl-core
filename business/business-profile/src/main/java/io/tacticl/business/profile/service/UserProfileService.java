package io.tacticl.business.profile.service;

import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.cidadel.framework.exception.CidadelException;
import io.tacticl.business.profile.ProfileErrorCode;
import io.tacticl.data.profile.entity.UserProfile;
import io.tacticl.data.profile.repository.UserProfileRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class UserProfileService {

    private static final String MODULE_NAME = "business-profile";

    private final UserProfileRepository userProfileRepository;

    public UserProfileService(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    public UserProfile getOrCreate(AuthenticatedUser user) {
        if (user.getUserId() == null) {
            throw new CidadelException(ProfileErrorCode.INVALID_TOKEN_CLAIMS, MODULE_NAME, "Token missing userId claim");
        }
        if (user.getName() == null) {
            throw new CidadelException(ProfileErrorCode.INVALID_TOKEN_CLAIMS, MODULE_NAME, "Token missing name claim");
        }
        if (user.getEmail() == null) {
            throw new CidadelException(ProfileErrorCode.INVALID_TOKEN_CLAIMS, MODULE_NAME, "Token missing email claim");
        }
        return userProfileRepository.findByCidadelUserIdAndIsActiveTrue(user.getUserId())
            .orElseGet(() -> insertProfile(user));
    }

    private UserProfile insertProfile(AuthenticatedUser user) {
        try {
            return userProfileRepository.save(
                UserProfile.create(user.getUserId(), user.getName(), user.getEmail())
            );
        } catch (DuplicateKeyException e) {
            return userProfileRepository.findByCidadelUserIdAndIsActiveTrue(user.getUserId())
                .orElseThrow(() -> new IllegalStateException(
                    "UserProfile not found after DuplicateKeyException for userId: " + user.getUserId()
                ));
        }
    }

    /**
     * Update mutable profile fields ({@code displayName}, {@code avatarUrl}). Ensures the
     * profile exists first (lazy-creates from the token), then applies only the non-null
     * fields supplied by the caller (null = leave unchanged). Email is immutable here — it
     * is sourced from the identity token, not user-editable.
     */
    public UserProfile update(AuthenticatedUser user, String displayName, String avatarUrl) {
        UserProfile profile = getOrCreate(user);
        if (displayName != null) {
            profile.setDisplayName(displayName);
        }
        if (avatarUrl != null) {
            profile.setAvatarUrl(avatarUrl);
        }
        return userProfileRepository.save(profile);
    }

    public UserProfile updateSettings(String userId, int maxConcurrentSparks, double spendingLimit,
                                       java.util.List<String> domainAllowlist, java.util.List<String> domainBlocklist) {
        UserProfile profile = userProfileRepository.findByCidadelUserIdAndIsActiveTrue(userId)
            .orElseThrow(() -> new CidadelException(ProfileErrorCode.INVALID_TOKEN_CLAIMS, MODULE_NAME,
                "UserProfile not found for userId: " + userId));
        profile.setMaxConcurrentSparks(maxConcurrentSparks);
        profile.setSpendingLimit(spendingLimit);
        profile.setDomainAllowlist(domainAllowlist != null ? domainAllowlist : new java.util.ArrayList<>());
        profile.setDomainBlocklist(domainBlocklist != null ? domainBlocklist : new java.util.ArrayList<>());
        return userProfileRepository.save(profile);
    }
}
