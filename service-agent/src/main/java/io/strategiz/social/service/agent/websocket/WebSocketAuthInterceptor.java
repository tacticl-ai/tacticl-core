package io.strategiz.social.service.agent.websocket;

import io.strategiz.framework.authorization.context.AuthenticatedUser;
import io.strategiz.framework.authorization.validator.PasetoTokenValidator;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Authenticates WebSocket upgrade requests. Extracts PASETO token and device ID from query
 * parameters, validates the token, and stores the authenticated principal in session attributes.
 */
@Component
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

	private static final Logger log = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

	private final PasetoTokenValidator tokenValidator;

	public WebSocketAuthInterceptor(PasetoTokenValidator tokenValidator) {
		this.tokenValidator = tokenValidator;
	}

	@Override
	public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
			WebSocketHandler wsHandler, Map<String, Object> attributes) {
		try {
			var params = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
			String token = params.getFirst("token");
			String deviceId = params.getFirst("deviceId");

			if (token == null || deviceId == null) {
				log.warn("[WS-AUTH] Missing query params: token={}, deviceId={}", token != null, deviceId != null);
				return false;
			}

			// Strip "Bearer " prefix if present
			if (token.startsWith("Bearer ")) {
				token = token.substring(7);
			}

			Optional<AuthenticatedUser> userOpt = tokenValidator.validateAndExtract(token);
			if (userOpt.isEmpty()) {
				log.warn("[WS-AUTH] Token validation failed for device {}", deviceId);
				return false;
			}

			String userId = userOpt.get().getUserId();
			attributes.put("principal", new DevicePrincipal(userId, deviceId));
			log.info("[WS-AUTH] Authenticated: user={}, device={}", userId, deviceId);
			return true;
		}
		catch (Exception ex) {
			log.error("[WS-AUTH] Handshake failed", ex);
			return false;
		}
	}

	@Override
	public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
			Exception exception) {
		// No-op
	}

}
