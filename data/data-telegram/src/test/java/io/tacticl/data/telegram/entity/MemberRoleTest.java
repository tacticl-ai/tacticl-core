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

    @Test
    void atLeastIsReflexive() {
        for (MemberRole r : MemberRole.values()) {
            assertTrue(r.atLeast(r), r + " should be atLeast itself");
        }
    }

    @Test
    void atLeastNullReturnsFalse() {
        assertFalse(MemberRole.OWNER.atLeast(null));
        assertFalse(MemberRole.OBSERVER.atLeast(null));
    }

    @Test
    void observerCannotRunAnyTier() {
        assertFalse(MemberRole.OBSERVER.canRunTier(0));
        assertFalse(MemberRole.OBSERVER.canRunTier(1));
        assertFalse(MemberRole.OBSERVER.canRunTier(2));
    }

    @Test
    void contributorRunsTier0Only() {
        assertTrue(MemberRole.CONTRIBUTOR.canRunTier(0));
        assertFalse(MemberRole.CONTRIBUTOR.canRunTier(1));
        assertFalse(MemberRole.CONTRIBUTOR.canRunTier(2));
    }

    @Test
    void adminRunsTier0And1ButNotTier2() {
        assertTrue(MemberRole.ADMIN.canRunTier(0));
        assertTrue(MemberRole.ADMIN.canRunTier(1));
        assertFalse(MemberRole.ADMIN.canRunTier(2));
    }

    @Test
    void canRunTierOutOfRangeReturnsFalse() {
        assertFalse(MemberRole.OWNER.canRunTier(-1));
        assertFalse(MemberRole.OWNER.canRunTier(3));
        assertFalse(MemberRole.OWNER.canRunTier(99));
    }

    @Test
    void ranksAreStrictlyMonotonic() {
        MemberRole[] values = MemberRole.values();
        for (int i = 1; i < values.length; i++) {
            assertTrue(values[i].atLeast(values[i - 1]),
                values[i] + " must be >= " + values[i - 1]);
            assertFalse(values[i - 1].atLeast(values[i]) && values[i].atLeast(values[i - 1])
                    && values[i - 1] != values[i],
                "distinct roles must not be mutually atLeast");
        }
    }
}
