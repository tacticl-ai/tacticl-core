package io.tacticl.business.profile.service;

import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.tacticl.data.profile.entity.UserProfile;
import io.tacticl.data.profile.repository.UserProfileRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;

    public UserProfileService(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    public UserProfile getOrCreate(AuthenticatedUser user) {
        if (user.getUserId() == null) {
            throw new IllegalArgumentException("Token missing userId claim");
        }
        if (user.getName() == null) {
            throw new IllegalArgumentException("Token missing name claim");
        }
        if (user.getEmail() == null) {
            throw new IllegalArgumentException("Token missing email claim");
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
}
