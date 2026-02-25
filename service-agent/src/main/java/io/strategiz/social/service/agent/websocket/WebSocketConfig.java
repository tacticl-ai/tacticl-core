package io.strategiz.social.service.agent.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Raw WebSocket configuration for device and user communication. */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

	private final DeviceWebSocketHandler deviceWebSocketHandler;

	private final UserWebSocketHandler userWebSocketHandler;

	private final WebSocketAuthInterceptor authInterceptor;

	private final String[] allowedOrigins;

	public WebSocketConfig(DeviceWebSocketHandler deviceWebSocketHandler, UserWebSocketHandler userWebSocketHandler,
			WebSocketAuthInterceptor authInterceptor,
			@Value("${tacticl.websocket.allowed-origins:http://localhost:5173,http://localhost:3000}") String allowedOriginsConfig) {
		this.deviceWebSocketHandler = deviceWebSocketHandler;
		this.userWebSocketHandler = userWebSocketHandler;
		this.authInterceptor = authInterceptor;
		this.allowedOrigins = allowedOriginsConfig.split(",");
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(deviceWebSocketHandler, "/ws/device")
			.setAllowedOrigins(allowedOrigins)
			.addInterceptors(authInterceptor);

		registry.addHandler(userWebSocketHandler, "/ws/user")
			.setAllowedOrigins(allowedOrigins)
			.addInterceptors(authInterceptor);
	}

}
