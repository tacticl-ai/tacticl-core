package io.tacticl.service.profile.controller;

import io.cidadel.framework.authorization.annotation.AuthUser;
import io.cidadel.framework.authorization.annotation.RequireAuth;
import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.cidadel.service.base.controller.BaseController;
import io.tacticl.business.profile.service.UserProfileService;
import io.tacticl.data.profile.entity.UserProfile;
import io.tacticl.service.profile.dto.UpdateProfileRequest;
import io.tacticl.service.profile.dto.UserProfileResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/users")
public class ProfileController extends BaseController {

    private final UserProfileService userProfileService;

    public ProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @Override
    protected String getModuleName() {
        return "profile";
    }

    @GetMapping("/me")
    @RequireAuth
    public ResponseEntity<UserProfileResponse> getProfile(@AuthUser AuthenticatedUser user) {
        var profile = userProfileService.getOrCreate(user);
        return ResponseEntity.ok(toResponse(profile));
    }

    @PutMapping("/me")
    @RequireAuth
    public ResponseEntity<UserProfileResponse> updateProfile(
            @AuthUser AuthenticatedUser user,
            @RequestBody UpdateProfileRequest body) {
        UserProfile updated = userProfileService.update(user, body.displayName(), body.avatarUrl());
        return ResponseEntity.ok(toResponse(updated));
    }

    private static UserProfileResponse toResponse(UserProfile profile) {
        return new UserProfileResponse(
            profile.getDisplayName(),
            profile.getEmail(),
            profile.getAvatarUrl()
        );
    }
}
