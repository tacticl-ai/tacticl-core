package io.tacticl.data.telegram.entity;

/**
 * Group-membership permission tier for Telegram projects.
 * Distinct from CLAUDE.md's action-confirmation tiers — {@link #canRunTier(int)}
 * maps 0→read-only (CONTRIBUTOR+), 1→mutation (RUNNER+), 2→financial (OWNER only).
 */
public enum MemberRole {
    OBSERVER(0),
    CONTRIBUTOR(1),
    RUNNER(2),
    ADMIN(3),
    OWNER(4);

    private final int rank;

    MemberRole(int rank) { this.rank = rank; }

    public boolean atLeast(MemberRole other) {
        return other != null && this.rank >= other.rank;
    }

    public boolean canRunTier(int tier) {
        return switch (tier) {
            case 0 -> atLeast(CONTRIBUTOR);
            case 1 -> atLeast(RUNNER);
            case 2 -> atLeast(OWNER);
            default -> false;
        };
    }
}
