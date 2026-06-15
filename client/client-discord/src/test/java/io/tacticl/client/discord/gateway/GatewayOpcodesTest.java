package io.tacticl.client.discord.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GatewayOpcodesTest {

    @Test
    void fatalCloses_stopTheClient() {
        // Reconnecting on these is pointless/harmful.
        for (int code : new int[] {4004, 4010, 4011, 4012, 4013, 4014}) {
            assertThat(GatewayOpcodes.isFatalClose(code)).as("code %d fatal", code).isTrue();
            assertThat(GatewayOpcodes.isResumableClose(code)).as("fatal ⇒ not resumable").isFalse();
        }
    }

    @Test
    void disallowedAndAuth_areClassified() {
        assertThat(GatewayOpcodes.isDisallowedIntents(4014)).isTrue();
        assertThat(GatewayOpcodes.isDisallowedIntents(4004)).isFalse();
        assertThat(GatewayOpcodes.isAuthFailed(4004)).isTrue();
        assertThat(GatewayOpcodes.isAuthFailed(4014)).isFalse();
    }

    @Test
    void sessionInvalidatingCloses_forceFreshIdentify_notResume() {
        // 4007 invalid seq, 4009 session timed out, and our own 1000 normal close all drop the session.
        assertThat(GatewayOpcodes.isResumableClose(4007)).isFalse();
        assertThat(GatewayOpcodes.isResumableClose(4009)).isFalse();
        assertThat(GatewayOpcodes.isResumableClose(GatewayOpcodes.CLOSE_NORMAL)).isFalse();
    }

    @Test
    void transientCloses_areResumable() {
        // Unknown error / rate limited / our zombie code → resume the existing session.
        assertThat(GatewayOpcodes.isResumableClose(4000)).isTrue();
        assertThat(GatewayOpcodes.isResumableClose(4008)).isTrue();
        assertThat(GatewayOpcodes.isResumableClose(GatewayOpcodes.CLOSE_ZOMBIE)).isTrue();
        assertThat(GatewayOpcodes.isFatalClose(GatewayOpcodes.CLOSE_ZOMBIE)).isFalse();
    }
}
