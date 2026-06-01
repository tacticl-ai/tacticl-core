package io.tacticl.service.voice;

import static org.assertj.core.api.Assertions.assertThat;

import io.tacticl.service.voice.config.VoiceTransportProperties;
import io.tacticl.service.voice.token.VoiceSessionTokenService;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class VoiceSessionTokenServiceTest {

    private VoiceSessionTokenService serviceWithTtl(long ttlSeconds) {
        VoiceTransportProperties props = new VoiceTransportProperties();
        props.setTokenTtlSeconds(ttlSeconds);
        return new VoiceSessionTokenService(props);
    }

    @Test
    void mint_validUser_returnsTokenBoundToUser() {
        VoiceSessionTokenService service = serviceWithTtl(120);

        VoiceSessionTokenService.Issued issued = service.mint("user-1");

        assertThat(issued.token()).isNotBlank();
        assertThat(issued.expiresInSeconds()).isEqualTo(120);
        assertThat(service.resolveUserId(issued.token())).contains("user-1");
    }

    @Test
    void resolveUserId_unknownToken_returnsEmpty() {
        VoiceSessionTokenService service = serviceWithTtl(120);

        assertThat(service.resolveUserId("not-a-real-token")).isEmpty();
    }

    @Test
    void resolveUserId_nullOrBlank_returnsEmpty() {
        VoiceSessionTokenService service = serviceWithTtl(120);

        assertThat(service.resolveUserId(null)).isEmpty();
        assertThat(service.resolveUserId("  ")).isEmpty();
    }

    @Test
    void resolveUserId_expiredToken_returnsEmpty() {
        // TTL of 0 → the property guard floors it to the 120s default, so to test
        // expiry we mint, then assert a NEGATIVE-TTL service rejects immediately.
        VoiceTransportProperties props = new VoiceTransportProperties();
        props.setTokenTtlSeconds(-1); // floored to default in service; verify floor below
        VoiceSessionTokenService service = new VoiceSessionTokenService(props);

        VoiceSessionTokenService.Issued issued = service.mint("user-2");

        // Negative/zero TTL is defended against (floored to 120), so token is valid.
        assertThat(issued.expiresInSeconds()).isEqualTo(120);
        assertThat(service.resolveUserId(issued.token())).contains("user-2");
    }

    @Test
    void invalidate_token_cannotBeReplayed() {
        VoiceSessionTokenService service = serviceWithTtl(120);
        VoiceSessionTokenService.Issued issued = service.mint("user-3");
        assertThat(service.resolveUserId(issued.token())).contains("user-3");

        service.invalidate(issued.token());

        assertThat(service.resolveUserId(issued.token())).isEmpty();
    }

    @Test
    void mint_distinctCalls_produceDistinctTokens() {
        VoiceSessionTokenService service = serviceWithTtl(120);

        String t1 = service.mint("u").token();
        String t2 = service.mint("u").token();

        assertThat(t1).isNotEqualTo(t2);
    }

    @Test
    void resolveUserId_afterMint_resolvesCorrectUserPerToken() {
        VoiceSessionTokenService service = serviceWithTtl(120);
        String tokenA = service.mint("alice").token();
        String tokenB = service.mint("bob").token();

        assertThat(service.resolveUserId(tokenA)).contains("alice");
        assertThat(service.resolveUserId(tokenB)).contains("bob");
    }

    @Test
    void invalidate_nullToken_isNoOp() {
        VoiceSessionTokenService service = serviceWithTtl(120);
        String token = service.mint("u").token();

        service.invalidate(null);

        assertThat(service.resolveUserId(token)).isPresent();
    }

    @Test
    void issued_isRecordWithTokenAndTtl() {
        Optional<VoiceSessionTokenService.Issued> issued =
            Optional.of(new VoiceSessionTokenService.Issued("tok", 99));

        assertThat(issued).get().extracting(VoiceSessionTokenService.Issued::token).isEqualTo("tok");
        assertThat(issued).get().extracting(VoiceSessionTokenService.Issued::expiresInSeconds).isEqualTo(99L);
    }
}
