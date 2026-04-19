package io.tacticl.service.profile.controller;

import io.cidadel.framework.authorization.annotation.AuthUser;
import io.cidadel.framework.authorization.annotation.RequireAuth;
import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.cidadel.service.base.controller.BaseController;
import io.tacticl.business.profile.service.UserProfileService;
import io.tacticl.data.profile.entity.UserProfile;
import io.tacticl.service.profile.dto.UserSettingsDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/settings")
public class SettingsController extends BaseController {

    private final UserProfileService userProfileService;

    public SettingsController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @Override
    protected String getModuleName() {
        return "settings";
    }

    @GetMapping
    @RequireAuth
    public ResponseEntity<UserSettingsDto> getSettings(@AuthUser AuthenticatedUser user) {
        UserProfile profile = userProfileService.getOrCreate(user);
        return ResponseEntity.ok(UserSettingsDto.from(profile));
    }

    @PutMapping
    @RequireAuth
    public ResponseEntity<UserSettingsDto> updateSettings(
            @AuthUser AuthenticatedUser user,
            @RequestBody UserSettingsDto body) {
        // Ensure profile exists before updating settings
        userProfileService.getOrCreate(user);
        UserProfile updated = userProfileService.updateSettings(
            user.getUserId(),
            body.maxConcurrentSparks(),
            body.spendingLimit(),
            body.domainAllowlist(),
            body.domainBlocklist()
        );
        return ResponseEntity.ok(UserSettingsDto.from(updated));
    }
}
