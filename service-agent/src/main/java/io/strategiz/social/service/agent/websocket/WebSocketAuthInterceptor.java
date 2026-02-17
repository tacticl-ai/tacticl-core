package io.strategiz.social.service.agent.websocket;

import io.strategiz.framework.authorization.context.AuthenticatedUser;
import io.strategiz.framework.authorization.validator.PasetoTokenValidator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Intercepts STOMP CONNECT frames to authenticate devices. Extracts PASETO token and device ID from
 * headers, validates them, and sets the authenticated principal.
 */
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

	private static final Logger log = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

	private final PasetoTokenValidator tokenValidator;

	public WebSocketAuthInterceptor(PasetoTokenValidator tokenValidator) {
		this.tokenValidator = tokenValidator;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

		if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
			String token = getHeader(accessor, "Authorization");
			String deviceId = getHeader(accessor, "X-Device-Id");

			if (token == null || deviceId == null) {
				log.warn("WebSocket CONNECT missing Authorization or X-Device-Id header");
				throw new SecurityException("Missing authentication headers");
			}

			// Strip "Bearer " prefix if present
			if (token.startsWith("Bearer ")) {
				token = token.substring(7);
			}

			// Validate PASETO token using the same validator as @RequireAuth
			Optional<AuthenticatedUser> userOpt = tokenValidator.validateAndExtract(token);
			if (userOpt.isEmpty()) {
				log.warn("WebSocket CONNECT with invalid token for device {}", deviceId);
				throw new SecurityException("Invalid authentication token");
			}

			String userId = userOpt.get().getUserId();

			// Set the authenticated principal for user-destination routing
			accessor.setUser(new DevicePrincipal(userId, deviceId));
			log.info("WebSocket CONNECT authenticated: user={}, device={}", userId, deviceId);
		}

		return message;
	}

	private String getHeader(StompHeaderAccessor accessor, String headerName) {
		List<String> values = accessor.getNativeHeader(headerName);
		return (values != null && !values.isEmpty()) ? values.get(0) : null;
	}

}
