package io.strategiz.social.service.social.controller;

import io.cidadel.framework.authorization.annotation.AuthUser;
import io.cidadel.framework.authorization.annotation.RequireAuth;
import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.strategiz.social.data.entity.SocialIntegration;
import io.strategiz.social.data.repository.SocialIntegrationRepository;
import io.strategiz.social.service.social.dto.IntegrationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for managing social media integrations (connected accounts). */
@RestController
@RequestMapping("/api/social/integrations")
@Tag(name = "Social Integrations", description = "Manage connected social media accounts")
public class SocialIntegrationController {

	private static final Logger log = LoggerFactory.getLogger(SocialIntegrationController.class);

	private final SocialIntegrationRepository integrationRepository;

	public SocialIntegrationController(SocialIntegrationRepository integrationRepository) {
		this.integrationRepository = integrationRepository;
	}

	@GetMapping
	@RequireAuth
	@Operation(summary = "List connected accounts", description = "List all connected social media integrations")
	public ResponseEntity<List<IntegrationResponse>> listIntegrations(@AuthUser AuthenticatedUser user) {
		List<SocialIntegration> integrations = integrationRepository.findAllByUserId(user.getUserId());
		return ResponseEntity.ok(integrations.stream().map(this::toResponse).toList());
	}

	@GetMapping("/{integrationId}")
	@RequireAuth
	@Operation(summary = "Get integration", description = "Get details of a specific connected account")
	public ResponseEntity<IntegrationResponse> getIntegration(@PathVariable String integrationId,
			@AuthUser AuthenticatedUser user) {
		Optional<SocialIntegration> integration = integrationRepository.findById(user.getUserId(), integrationId);
		if (integration.isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(toResponse(integration.get()));
	}

	@DeleteMapping("/{integrationId}")
	@RequireAuth
	@Operation(summary = "Disconnect account",
			description = "Disconnect a social media account by marking it inactive")
	public ResponseEntity<Void> disconnectIntegration(@PathVariable String integrationId,
			@AuthUser AuthenticatedUser user) {
		Optional<SocialIntegration> integration = integrationRepository.findById(user.getUserId(), integrationId);
		if (integration.isEmpty()) {
			return ResponseEntity.notFound().build();
		}

		SocialIntegration integ = integration.get();
		integ.setDisabled(true);
		integ.setAccessToken(null);
		integ.setRefreshToken(null);
		integ.setUpdatedAt(Instant.now());
		integrationRepository.save(user.getUserId(), integ, integ.getId());

		log.info("Disconnected {} integration {} for user {}", integ.getPlatform(), integrationId, user.getUserId());
		return ResponseEntity.noContent().build();
	}

	private IntegrationResponse toResponse(SocialIntegration integration) {
		IntegrationResponse response = new IntegrationResponse();
		response.setId(integration.getId());
		response.setPlatform(integration.getPlatform().name());
		response.setPlatformUsername(integration.getPlatformUsername());
		response.setProfileImageUrl(integration.getProfileImageUrl());
		response.setDisabled(integration.isDisabled());
		response.setTokenRefreshNeeded(integration.isTokenRefreshNeeded());
		response.setTokenExpiresAt(integration.getTokenExpiresAt());
		response.setCreatedAt(integration.getCreatedAt());
		return response;
	}

}
