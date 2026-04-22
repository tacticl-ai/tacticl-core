package io.tacticl.business.telegram.permission;

import io.tacticl.data.telegram.entity.MemberRole;

public record PermissionCheck(boolean allowed, MemberRole actual, MemberRole required, String reason) {
    public static PermissionCheck allow(MemberRole actual) {
        return new PermissionCheck(true, actual, null, null);
    }
    public static PermissionCheck deny(MemberRole actual, MemberRole required, String reason) {
        return new PermissionCheck(false, actual, required, reason);
    }
}
