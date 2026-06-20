package io.tacticl.service.token.controller;

import io.cidadel.framework.authorization.annotation.AuthUser;
import io.cidadel.framework.authorization.annotation.RequireAuth;
import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.cidadel.service.base.controller.BaseController;
import io.tacticl.business.token.PersonalAccessTokenService;
import io.tacticl.business.token.PersonalAccessTokenService.IssuedToken;
import io.tacticl.service.token.dto.CreateTokenRequest;
import io.tacticl.service.token.dto.CreatedTokenResponse;
import io.tacticl.service.token.dto.TokenResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Personal access token management for the Settings "API Tokens" section.
 *
 * <p>The plaintext token is returned exactly once (on {@code POST}); subsequent {@code GET}s
 * only ever expose a masked label. Token <em>validation</em> against the auth filter is a
 * follow-up — this controller is CRUD management only.
 */
@RestController
@RequestMapping("/v1/tokens")
public class TokenController extends BaseController {

    private final PersonalAccessTokenService tokenService;

    public TokenController(PersonalAccessTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected String getModuleName() {
        return "tokens";
    }

    /** Issue a new token. The plaintext secret is in the response — shown only here. */
    @PostMapping
    @RequireAuth
    public ResponseEntity<CreatedTokenResponse> create(@AuthUser AuthenticatedUser user,
                                                       @RequestBody CreateTokenRequest body) {
        IssuedToken issued = tokenService.issue(user.getUserId(), body == null ? null : body.name());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CreatedTokenResponse.from(issued.record(), issued.plaintext()));
    }

    /** List the user's active tokens (masked — never the plaintext). */
    @GetMapping
    @RequireAuth
    public ResponseEntity<List<TokenResponse>> list(@AuthUser AuthenticatedUser user) {
        List<TokenResponse> tokens = tokenService.list(user.getUserId()).stream()
                .map(TokenResponse::from)
                .toList();
        return ResponseEntity.ok(tokens);
    }

    /** Revoke (soft-delete) a token. */
    @DeleteMapping("/{id}")
    @RequireAuth
    public ResponseEntity<Void> revoke(@AuthUser AuthenticatedUser user, @PathVariable String id) {
        boolean revoked = tokenService.revoke(user.getUserId(), id);
        return revoked ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
