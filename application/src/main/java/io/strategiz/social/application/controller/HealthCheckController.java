package io.strategiz.social.application.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Health check endpoint for Cloud Run liveness/readiness probes. */
@RestController
public class HealthCheckController {

	@GetMapping("/health")
	public Map<String, Object> health() {
		return Map.of("status", "UP", "application", "tacticl-core", "timestamp", System.currentTimeMillis());
	}

}
