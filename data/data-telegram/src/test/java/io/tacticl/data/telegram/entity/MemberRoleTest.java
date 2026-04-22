package io.tacticl.data.telegram.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MemberRoleTest {

    @Test
    void rolesAreOrderedByPower() {
        assertTrue(MemberRole.OWNER.atLeast(MemberRole.ADMIN));
        assertTrue(MemberRole.ADMIN.atLeast(MemberRole.RUNNER));
        assertTrue(MemberRole.RUNNER.atLeast(MemberRole.CONTRIBUTOR));
        assertTrue(MemberRole.CONTRIBUTOR.atLeast(MemberRole.OBSERVER));
        assertFalse(MemberRole.OBSERVER.atLeast(MemberRole.CONTRIBUTOR));
    }

    @Test
    void runnerCanRunTier1ButNotTier2() {
        assertTrue(MemberRole.RUNNER.canRunTier(1));
        assertFalse(MemberRole.RUNNER.canRunTier(2));
        assertTrue(MemberRole.OWNER.canRunTier(2));
    }
}
