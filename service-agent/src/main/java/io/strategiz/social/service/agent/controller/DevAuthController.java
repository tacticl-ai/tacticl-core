package io.strategiz.social.service.agent.controller;

import io.strategiz.framework.token.issuer.PasetoTokenIssuer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dev/QA controller for generating PASETO tokens for testing. */
@RestController
@RequestMapping("/api/auth")
@Profile({ "local", "qa", "prod" })
@Tag(name = "Dev Auth", description = "Development token generation (local/QA only)")
public class DevAuthController {

	private static final Logger log = LoggerFactory.getLogger(DevAuthController.class);

	private final PasetoTokenIssuer tokenIssuer;

	public DevAuthController(PasetoTokenIssuer tokenIssuer) {
		this.tokenIssuer = tokenIssuer;
	}

	@PostMapping("/dev-token")
	@Operation(summary = "Generate a dev PASETO token", description = "Creates a PASETO token for dev/QA testing")
	public ResponseEntity<Map<String, String>> generateDevToken(@RequestBody(required = false) Map<String, String> body) {
		String userId = (body != null && body.containsKey("userId")) ? body.get("userId") : UUID.randomUUID().toString();

		log.info("Generating dev token for userId: {}", userId);

		String token = tokenIssuer.createAuthenticationToken(userId, List.of("password"), "1", Duration.ofDays(30),
				false);

		return ResponseEntity.ok(Map.of("token", token, "userId", userId));
	}

}
