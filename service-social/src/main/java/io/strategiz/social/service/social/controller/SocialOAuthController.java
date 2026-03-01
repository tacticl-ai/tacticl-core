package io.strategiz.social.service.social.controller;

import io.cidadel.framework.authorization.annotation.AuthUser;
import io.cidadel.framework.authorization.annotation.RequireAuth;
import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.strategiz.social.business.publish.AuthUrl;
import io.strategiz.social.business.publish.OAuthTokenExchangeService;
import io.strategiz.social.business.publish.SocialMediaProviderFactory;
import io.strategiz.social.data.entity.PlatformType;
import io.strategiz.social.data.entity.SocialIntegration;
import io.strategiz.social.service.social.dto.OAuthAuthorizeResponse;
import io.strategiz.social.service.social.dto.OAuthCallbackResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for OAuth 2.0 authorization flows with social media platforms. */
@RestController
@RequestMapping("/api/social/oauth")
@Tag(name = "Social OAuth", description = "OAuth 2.0 authorization flows for social media platforms")
public class SocialOAuthController {

	private static final Logger log = LoggerFactory.getLogger(SocialOAuthController.class);

	private final SocialMediaProviderFactory providerFactory;

	private final OAuthTokenExchangeService tokenExchangeService;

	public SocialOAuthController(SocialMediaProviderFactory providerFactory,
			OAuthTokenExchangeService tokenExchangeService) {
		this.providerFactory = providerFactory;
		this.tokenExchangeService = tokenExchangeService;
	}

	/**
	 * Generate an OAuth authorization URL for the given platform. The mobile app opens this
	 * URL in a browser for the user to authenticate. Returns the URL and the PKCE code
	 * verifier (which the client must store and send back during the callback).
	 */
	@GetMapping("/{platform}/authorize")
	@RequireAuth
	@Operation(summary = "Generate OAuth URL",
			description = "Generate an OAuth 2.0 authorization URL for the specified platform")
	public ResponseEntity<OAuthAuthorizeResponse> authorize(@PathVariable String platform,
			@RequestParam String redirectUri, @AuthUser AuthenticatedUser user) {
		PlatformType platformType = parsePlatform(platform);
		String state = UUID.randomUUID().toString();

		log.info("Generating OAuth URL for {} for user {}", platformType, user.getUserId());

		AuthUrl authUrl = providerFactory.getProvider(platformType).generateAuthUrl(redirectUri, state);

		return ResponseEntity.ok(new OAuthAuthorizeResponse(authUrl.getUrl(), authUrl.getCodeVerifier()));
	}

	/**
	 * Handle the OAuth callback from the social media platform. Exchanges the authorization
	 * code for access/refresh tokens and stores them as a SocialIntegration.
	 */
	@GetMapping("/{platform}/callback")
	@RequireAuth
	@Operation(summary = "OAuth callback",
			description = "Handle OAuth callback, exchange code for tokens, and store integration")
	public ResponseEntity<OAuthCallbackResponse> callback(@PathVariable String platform, @RequestParam String code,
			@RequestParam(required = false) String codeVerifier, @RequestParam String redirectUri,
			@AuthUser AuthenticatedUser user) {
		PlatformType platformType = parsePlatform(platform);

		log.info("Processing OAuth callback for {} for user {}", platformType, user.getUserId());

		try {
			SocialIntegration integration = tokenExchangeService.exchangeAndStore(platformType, code, codeVerifier,
					redirectUri, user.getUserId());

			return ResponseEntity
				.ok(new OAuthCallbackResponse(integration.getId(), platformType.name(), true));
		}
		catch (Exception e) {
			log.error("OAuth token exchange failed for {} for user {}: {}", platformType, user.getUserId(),
					e.getMessage(), e);
			return ResponseEntity.badRequest()
				.body(new OAuthCallbackResponse(null, platformType.name(), false));
		}
	}

	private PlatformType parsePlatform(String platform) {
		try {
			return PlatformType.valueOf(platform.toUpperCase());
		}
		catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Unsupported platform: " + platform);
		}
	}

}
