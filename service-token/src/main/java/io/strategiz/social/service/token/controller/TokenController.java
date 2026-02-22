package io.strategiz.social.service.token.controller;

import io.strategiz.framework.authorization.annotation.AuthUser;
import io.strategiz.framework.authorization.annotation.RequireAuth;
import io.strategiz.framework.authorization.context.AuthenticatedUser;
import io.strategiz.social.data.entity.AgentToken;
import io.strategiz.social.data.entity.TokenProvider;
import io.strategiz.social.data.repository.AgentTokenRepository;
import io.strategiz.social.service.token.dto.CreateTokenRequest;
import io.strategiz.social.service.token.dto.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for managing agent API tokens. */
@RestController
@RequestMapping("/api/tokens")
@Tag(name = "Tokens", description = "Manage API tokens for agent execution")
public class TokenController {

	private static final Logger log = LoggerFactory.getLogger(TokenController.class);

	private final AgentTokenRepository tokenRepository;

	public TokenController(AgentTokenRepository tokenRepository) {
		this.tokenRepository = tokenRepository;
	}

	@GetMapping
	@RequireAuth
	@Operation(summary = "List managed tokens")
	public ResponseEntity<List<TokenResponse>> listTokens(@AuthUser AuthenticatedUser user) {
		List<AgentToken> tokens = tokenRepository.findActiveByUserId(user.getUserId());
		List<TokenResponse> response = tokens.stream().map(this::toResponse).toList();
		return ResponseEntity.ok(response);
	}

	@PostMapping
	@RequireAuth
	@Operation(summary = "Add a token", description = "Add a new API token (stored securely in Vault)")
	public ResponseEntity<TokenResponse> createToken(@Valid @RequestBody CreateTokenRequest request,
			@AuthUser AuthenticatedUser user) {
		log.info("Token creation from user {}: {} ({})", user.getUserId(), request.getLabel(), request.getProvider());

		TokenProvider provider;
		try {
			provider = TokenProvider.valueOf(request.getProvider().toUpperCase());
		}
		catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().build();
		}

		String tokenId = UUID.randomUUID().toString();

		// TODO: Store actual token value in Vault, get back a tokenRef
		String tokenRef = "vault:secret/tacticl-user/" + user.getUserId() + "/" + tokenId;

		AgentToken token = new AgentToken();
		token.setId(tokenId);
		token.setUserId(user.getUserId());
		token.setProvider(provider);
		token.setLabel(request.getLabel());
		token.setTokenRef(tokenRef);
		token.setUsageLimits(request.getUsageLimits());
		token.setCurrentUsage(Map.of("todayTokens", 0, "monthTokens", 0));
		tokenRepository.save(token, tokenId);

		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(token));
	}

	@DeleteMapping("/{id}")
	@RequireAuth
	@Operation(summary = "Remove a token")
	public ResponseEntity<Void> deleteToken(@PathVariable String id, @AuthUser AuthenticatedUser user) {
		Optional<AgentToken> opt = tokenRepository.findById(id);
		if (opt.isEmpty() || !opt.get().getUserId().equals(user.getUserId())) {
			return ResponseEntity.notFound().build();
		}

		AgentToken token = opt.get();
		token.setActive(false);
		tokenRepository.save(token, token.getId());

		log.info("Deleted token {} for user {}", id, user.getUserId());
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{id}/usage")
	@RequireAuth
	@Operation(summary = "Get token usage stats")
	public ResponseEntity<TokenResponse> getTokenUsage(@PathVariable String id, @AuthUser AuthenticatedUser user) {
		Optional<AgentToken> opt = tokenRepository.findById(id);
		if (opt.isEmpty() || !opt.get().getUserId().equals(user.getUserId())) {
			return ResponseEntity.notFound().build();
		}

		return ResponseEntity.ok(toResponse(opt.get()));
	}

	private TokenResponse toResponse(AgentToken token) {
		TokenResponse r = new TokenResponse();
		r.setId(token.getId());
		r.setProvider(token.getProvider().name());
		r.setLabel(token.getLabel());
		r.setUsageLimits(token.getUsageLimits());
		r.setCurrentUsage(token.getCurrentUsage());
		return r;
	}

}
