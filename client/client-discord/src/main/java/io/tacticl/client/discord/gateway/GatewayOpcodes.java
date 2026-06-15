package io.tacticl.client.discord.gateway;

/**
 * Discord Gateway opcodes and close-code classification (Gateway v10).
 *
 * <p>Pure constants + static classifiers — no state. The close-code table decides whether an
 * unexpected disconnect should resume, re-identify, back off, or stop for good.
 */
final class GatewayOpcodes {

    private GatewayOpcodes() {
    }

    // ── Opcodes ──────────────────────────────────────────────────────────────
    static final int DISPATCH = 0;        // receive: an event; carries `t` (type) and `s` (sequence)
    static final int HEARTBEAT = 1;       // send (and receive: server may request an immediate beat)
    static final int IDENTIFY = 2;        // send: first handshake after HELLO
    static final int RESUME = 6;          // send: replay a dropped session
    static final int RECONNECT = 7;       // receive: server asks us to reconnect (resumable)
    static final int INVALID_SESSION = 9; // receive: `d` boolean = whether the session is resumable
    static final int HELLO = 10;          // receive: `d.heartbeat_interval`
    static final int HEARTBEAT_ACK = 11;  // receive: ack of our HEARTBEAT

    // ── Our own client close codes ───────────────────────────────────────────
    /** Non-1000 close we send to drop a zombie connection so Discord still lets us RESUME. */
    static final int CLOSE_ZOMBIE = 4900;
    /** Normal closure (intentional shutdown) — Discord drops the session; we do NOT reconnect. */
    static final int CLOSE_NORMAL = 1000;

    /**
     * Fatal close codes — reconnecting is pointless or harmful; stop the client.
     *
     * <ul>
     *   <li>4004 authentication failed (bad bot token)</li>
     *   <li>4010 invalid shard / 4011 sharding required / 4012 invalid API version</li>
     *   <li>4013 invalid intent(s) / 4014 disallowed (privileged) intent(s)</li>
     * </ul>
     */
    static boolean isFatalClose(int code) {
        return code == 4004 || code == 4010 || code == 4011 || code == 4012 || code == 4013 || code == 4014;
    }

    /** 4014: a privileged intent (here, MESSAGE CONTENT) was requested but not enabled in the dev portal. */
    static boolean isDisallowedIntents(int code) {
        return code == 4014;
    }

    /** 4004: the bot token was rejected. */
    static boolean isAuthFailed(int code) {
        return code == 4004;
    }

    /**
     * Whether a non-fatal close should attempt to RESUME the existing session (vs. a fresh IDENTIFY).
     * Codes that invalidate the session itself force a fresh identify: 4007 (invalid seq),
     * 4009 (session timed out), and our normal closure. Everything else non-fatal can resume.
     */
    static boolean isResumableClose(int code) {
        if (isFatalClose(code) || code == CLOSE_NORMAL) {
            return false;
        }
        return code != 4007 && code != 4009;
    }
}
