package io.tacticl.service.profile.dto;

import java.util.List;

public record UserSettingsDto(
    int maxConcurrentSparks,
    double spendingLimit,
    List<String> domainAllowlist,
    List<String> domainBlocklist
) {
    public static UserSettingsDto from(io.tacticl.data.profile.entity.UserProfile profile) {
        return new UserSettingsDto(
            profile.getMaxConcurrentSparks(),
            profile.getSpendingLimit(),
            profile.getDomainAllowlist() != null ? profile.getDomainAllowlist() : List.of(),
            profile.getDomainBlocklist() != null ? profile.getDomainBlocklist() : List.of()
        );
    }
}
