package io.strategiz.social.service.agent.controller;

import io.cidadel.framework.authorization.annotation.RequireAuth;
import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.strategiz.social.business.agent.service.CredentialService;
import io.strategiz.social.data.entity.SocialIntegration;
import io.strategiz.social.service.agent.dto.AccountResponse;
import io.strategiz.social.service.agent.dto.CredentialResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for credential and account management. */
@RestController
@RequestMapping("/v1")
public class CredentialController {

	private final CredentialService credentialService;

	public CredentialController(CredentialService credentialService) {
		this.credentialService = credentialService;
	}

	/** Get credentials for a platform (used by devices during execution). */
	@GetMapping("/credentials/{platform}")
	@RequireAuth
	public ResponseEntity<CredentialResponse> getCredentials(@PathVariable String platform,
			@io.cidadel.framework.authorization.annotation.AuthUser AuthenticatedUser user) {
		Optional<Map<String, Object>> credentials = credentialService.getCredentials(user.getUserId(), platform);
		if (credentials.isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		Map<String, Object> creds = credentials.get();
		CredentialResponse response = new CredentialResponse();
		response.setPlatform((String) creds.get("platform"));
		response.setUsername((String) creds.get("username"));
		response.setPlatformUserId((String) creds.get("platformUserId"));
		response.setConnected(true);
		response.setTokenRefreshNeeded(Boolean.TRUE.equals(creds.get("tokenRefreshNeeded")));
		return ResponseEntity.ok(response);
	}

	/** Register new credentials (used by devices after creating accounts). */
	@PostMapping("/credentials/{platform}")
	@RequireAuth
	public ResponseEntity<Map<String, String>> registerCredentials(@PathVariable String platform,
			@RequestBody Map<String, String> credentials,
			@io.cidadel.framework.authorization.annotation.AuthUser AuthenticatedUser user) {
		SocialIntegration si = credentialService.registerCredentials(user.getUserId(), platform, credentials);
		return ResponseEntity.ok(Map.of("id", si.getId(), "platform", platform, "status", "registered"));
	}

	/** List all connected accounts for the authenticated user. */
	@GetMapping("/accounts")
	@RequireAuth
	public ResponseEntity<List<AccountResponse>> listAccounts(
			@io.cidadel.framework.authorization.annotation.AuthUser AuthenticatedUser user) {
		List<SocialIntegration> integrations = credentialService.listAccounts(user.getUserId());
		List<AccountResponse> accounts = integrations.stream().map(this::toAccountResponse).toList();
		return ResponseEntity.ok(accounts);
	}

	/** Disconnect an account. */
	@DeleteMapping("/accounts/{integrationId}")
	@RequireAuth
	public ResponseEntity<Void> disconnectAccount(@PathVariable String integrationId,
			@io.cidadel.framework.authorization.annotation.AuthUser AuthenticatedUser user) {
		boolean success = credentialService.disconnectAccount(user.getUserId(), integrationId);
		return success ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
	}

	private AccountResponse toAccountResponse(SocialIntegration si) {
		AccountResponse response = new AccountResponse();
		response.setId(si.getId());
		response.setPlatform(si.getPlatform() != null ? si.getPlatform().name() : null);
		response.setPlatformUsername(si.getPlatformUsername());
		response.setActive(si.getIsActive());
		response.setCreatedAt(si.getCreatedDate() != null ? si.getCreatedDate().toDate().toInstant() : null);
		response.setUpdatedAt(si.getModifiedDate() != null ? si.getModifiedDate().toDate().toInstant() : null);
		return response;
	}

}
