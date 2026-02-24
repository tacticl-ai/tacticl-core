package io.strategiz.social.service.agent.websocket;

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

	public WebSocketConfig(DeviceWebSocketHandler deviceWebSocketHandler, UserWebSocketHandler userWebSocketHandler,
			WebSocketAuthInterceptor authInterceptor) {
		this.deviceWebSocketHandler = deviceWebSocketHandler;
		this.userWebSocketHandler = userWebSocketHandler;
		this.authInterceptor = authInterceptor;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(deviceWebSocketHandler, "/ws/device")
			.setAllowedOriginPatterns("*")
			.addInterceptors(authInterceptor);

		registry.addHandler(userWebSocketHandler, "/ws/user")
			.setAllowedOriginPatterns("*")
			.addInterceptors(authInterceptor);
	}

}
