package io.tacticl.service.voice.config;

import io.tacticl.business.voice.VoiceSessionService;
import io.tacticl.service.voice.token.VoiceSessionTokenService;
import io.tacticl.service.voice.ws.VoiceWebSocketHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket wiring for the voice command-center transport.
 *
 * <p>Registers {@link VoiceWebSocketHandler} at {@code tacticl.voice.ws-path}
 * (default {@code /v1/voice}) with the allowed origins from
 * {@code tacticl.websocket.allowed-origins} (the same CSV the rest of the app
 * uses — wired here for the first time; it was previously orphaned). The handshake
 * itself authenticates via the {@code ?token=} query param inside the handler, so
 * no Spring Security / handshake interceptor is involved (Spring Security is
 * disabled app-wide; auth is the cidadel {@code @RequireAuth} interceptor for MVC
 * plus this explicit token check for the WS upgrade).
 *
 * <p>Entirely gated by {@code tacticl.voice.enabled=true}: dormant by default, so
 * {@code @EnableWebSocket} and the {@code /v1/voice} registration only activate
 * once voice is provisioned. Imported hierarchically by the application entry
 * point — no broad component scan.
 */
@Configuration
@EnableWebSocket
@ConditionalOnProperty(name = "tacticl.voice.enabled", havingValue = "true")
public class VoiceTransportConfig implements WebSocketConfigurer {

    private final VoiceSessionService sessionService;

    private final VoiceSessionTokenService tokenService;

    private final String wsPath;

    private final String[] allowedOrigins;

    public VoiceTransportConfig(VoiceSessionService sessionService,
                                VoiceSessionTokenService tokenService,
                                VoiceTransportProperties properties,
                                @org.springframework.beans.factory.annotation.Value(
                                    "${tacticl.websocket.allowed-origins:}") String allowedOriginsCsv) {
        this.sessionService = sessionService;
        this.tokenService = tokenService;
        this.wsPath = properties.getWsPath();
        this.allowedOrigins = splitCsv(allowedOriginsCsv);
    }

    @Bean
    public VoiceWebSocketHandler voiceWebSocketHandler() {
        return new VoiceWebSocketHandler(sessionService, tokenService);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        var registration = registry.addHandler(voiceWebSocketHandler(), wsPath);
        if (allowedOrigins.length > 0) {
            registration.setAllowedOrigins(allowedOrigins);
        }
    }

    private static String[] splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return new String[0];
        }
        return java.util.Arrays.stream(csv.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toArray(String[]::new);
    }
}
