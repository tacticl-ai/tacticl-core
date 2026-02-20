package io.strategiz.social.service.agent.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Raw WebSocket configuration for device communication. */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

	private final DeviceWebSocketHandler webSocketHandler;

	private final WebSocketAuthInterceptor authInterceptor;

	public WebSocketConfig(DeviceWebSocketHandler webSocketHandler, WebSocketAuthInterceptor authInterceptor) {
		this.webSocketHandler = webSocketHandler;
		this.authInterceptor = authInterceptor;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(webSocketHandler, "/ws/device")
			.setAllowedOriginPatterns("*")
			.addInterceptors(authInterceptor);
	}

}
