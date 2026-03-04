package io.strategiz.social.service.repo.controller;

import io.cidadel.framework.authorization.annotation.AuthUser;
import io.cidadel.framework.authorization.annotation.RequireAuth;
import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.strategiz.social.data.entity.AccessLevel;
import io.strategiz.social.data.entity.RepoGrant;
import io.strategiz.social.data.entity.RepoProvider;
import io.strategiz.social.data.repository.RepoGrantRepository;
import io.strategiz.social.service.repo.dto.GrantRepoRequest;
import io.strategiz.social.service.repo.dto.RepoGrantResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
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

/** REST controller for managing repository access grants. */
@RestController
@RequestMapping("/api/repos")
@Tag(name = "Repositories", description = "Grant and manage repository access for agent execution")
public class RepoController {

	private static final Logger log = LoggerFactory.getLogger(RepoController.class);

	private final RepoGrantRepository repoGrantRepository;

	public RepoController(RepoGrantRepository repoGrantRepository) {
		this.repoGrantRepository = repoGrantRepository;
	}

	@GetMapping
	@RequireAuth
	@Operation(summary = "List granted repos")
	public ResponseEntity<List<RepoGrantResponse>> listRepos(@AuthUser AuthenticatedUser user) {
		List<RepoGrant> grants = repoGrantRepository.findActiveByUserId(user.getUserId());
		List<RepoGrantResponse> response = grants.stream().map(this::toResponse).toList();
		return ResponseEntity.ok(response);
	}

	@PostMapping("/grant")
	@RequireAuth
	@Operation(summary = "Grant repo access", description = "Grant access to a repository for agent execution")
	public ResponseEntity<RepoGrantResponse> grantRepo(@Valid @RequestBody GrantRepoRequest request,
			@AuthUser AuthenticatedUser user) {
		log.info("Repo grant from user {}: {} ({})", user.getUserId(), request.getRepoFullName(),
				request.getProvider());

		RepoProvider provider;
		try {
			provider = RepoProvider.valueOf(request.getProvider().toUpperCase());
		}
		catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().build();
		}

		AccessLevel accessLevel = AccessLevel.READ;
		if (request.getAccessLevel() != null) {
			try {
				accessLevel = AccessLevel.valueOf(request.getAccessLevel().toUpperCase());
			}
			catch (IllegalArgumentException e) {
				// Default to READ
			}
		}

		String grantId = UUID.randomUUID().toString();
		RepoGrant grant = new RepoGrant();
		grant.setId(grantId);
		grant.setUserId(user.getUserId());
		grant.setProvider(provider);
		grant.setRepoFullName(request.getRepoFullName());
		grant.setAccessLevel(accessLevel);
		grant.setGrantedAt(Instant.now());
		repoGrantRepository.save(user.getUserId(), grant, grantId);

		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(grant));
	}

	@DeleteMapping("/{id}")
	@RequireAuth
	@Operation(summary = "Revoke repo access")
	public ResponseEntity<Void> revokeRepo(@PathVariable String id, @AuthUser AuthenticatedUser user) {
		Optional<RepoGrant> opt = repoGrantRepository.findById(user.getUserId(), id);
		if (opt.isEmpty()) {
			return ResponseEntity.notFound().build();
		}

		RepoGrant grant = opt.get();
		grant.setIsActive(false);
		repoGrantRepository.save(user.getUserId(), grant, grant.getId());

		log.info("Revoked repo grant {} for user {}", id, user.getUserId());
		return ResponseEntity.noContent().build();
	}

	private RepoGrantResponse toResponse(RepoGrant grant) {
		RepoGrantResponse r = new RepoGrantResponse();
		r.setId(grant.getId());
		r.setProvider(grant.getProvider().name());
		r.setRepoFullName(grant.getRepoFullName());
		r.setAccessLevel(grant.getAccessLevel().name());
		r.setGrantedAt(grant.getGrantedAt());
		return r;
	}

}
