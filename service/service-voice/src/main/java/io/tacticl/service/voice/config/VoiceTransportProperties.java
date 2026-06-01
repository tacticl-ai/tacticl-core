package io.tacticl.service.voice.config;

/**
 * Transport-layer configuration for the voice WebSocket edge (prefix
 * {@code tacticl.voice}). Bound in {@link VoiceTransportConfig}.
 *
 * <p>This is the service-layer companion to {@code VoiceProperties} in
 * business-voice (which owns {@code enabled} + {@code voiceId}); both bind under
 * the same {@code tacticl.voice} prefix, so unknown-field tolerance keeps them
 * from colliding. Fields here are purely about the browser-facing transport:
 *
 * <ul>
 *   <li>{@code wsPath} — the WebSocket endpoint path the handler registers and
 *       the token endpoint advertises back to the browser as {@code wsUrl}.</li>
 *   <li>{@code tokenTtlSeconds} — lifetime of a minted voice session token
 *       (mirrors the {@code link-token-ttl} style of telegram/discord).</li>
 *   <li>{@code publicWsUrl} — optional absolute {@code wss://…} URL handed to the
 *       browser in the token response; blank means the client falls back to its
 *       own {@code VITE_VOICE_WS_URL}.</li>
 * </ul>
 */
public class VoiceTransportProperties {

    private String wsPath = "/v1/voice";

    private long tokenTtlSeconds = 120;

    private String publicWsUrl;

    public String getWsPath() {
        return wsPath;
    }

    public void setWsPath(String wsPath) {
        this.wsPath = wsPath;
    }

    public long getTokenTtlSeconds() {
        return tokenTtlSeconds;
    }

    public void setTokenTtlSeconds(long tokenTtlSeconds) {
        this.tokenTtlSeconds = tokenTtlSeconds;
    }

    /** Optional absolute {@code wss://…} URL; null/blank → client uses its own default. */
    public String getPublicWsUrl() {
        return (publicWsUrl == null || publicWsUrl.isBlank()) ? null : publicWsUrl;
    }

    public void setPublicWsUrl(String publicWsUrl) {
        this.publicWsUrl = publicWsUrl;
    }
}
