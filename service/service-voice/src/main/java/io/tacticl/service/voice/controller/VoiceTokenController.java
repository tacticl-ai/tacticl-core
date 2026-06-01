package io.tacticl.service.voice.controller;

import io.cidadel.framework.authorization.annotation.AuthUser;
import io.cidadel.framework.authorization.annotation.RequireAuth;
import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.cidadel.service.base.controller.BaseController;
import io.tacticl.service.voice.config.VoiceTransportProperties;
import io.tacticl.service.voice.dto.VoiceTokenResponse;
import io.tacticl.service.voice.token.VoiceSessionTokenService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Issues short-lived voice session tokens for the WebSocket handshake.
 *
 * <p>The caller authenticates the normal way — {@code @RequireAuth} +
 * {@code @AuthUser} resolve the validated access PASETO into an
 * {@link AuthenticatedUser}. We mint an opaque, short-TTL token bound to that
 * userId (via {@link VoiceSessionTokenService}) and hand it back; the browser
 * then opens {@code wss://…/v1/voice?token=<token>} and the WS handler validates
 * that token at the handshake. The access PASETO never travels in the WS query
 * string. Third-party (Deepgram / ElevenLabs) keys never reach the browser at
 * all — they live in Vault and are used only inside the server-side client beans.
 *
 * <p>Gated by {@code tacticl.voice.enabled=true}: dormant by default, so no token
 * endpoint exists until voice is provisioned.
 */
@RestController
@RequestMapping("/v1/voice")
@ConditionalOnProperty(name = "tacticl.voice.enabled", havingValue = "true")
public class VoiceTokenController extends BaseController {

    private final VoiceSessionTokenService tokenService;

    private final String publicWsUrl;

    public VoiceTokenController(VoiceSessionTokenService tokenService,
                               VoiceTransportProperties properties) {
        this.tokenService = tokenService;
        this.publicWsUrl = properties.getPublicWsUrl();
    }

    @Override
    protected String getModuleName() {
        return "voice-token";
    }

    @PostMapping("/token")
    @RequireAuth
    public ResponseEntity<VoiceTokenResponse> issueToken(@AuthUser AuthenticatedUser user) {
        VoiceSessionTokenService.Issued issued = tokenService.mint(user.getUserId());
        return ResponseEntity.ok(
            new VoiceTokenResponse(issued.token(), publicWsUrl, issued.expiresInSeconds()));
    }
}
