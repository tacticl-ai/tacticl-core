package io.tacticl.service.profile.controller;

import io.cidadel.framework.authorization.annotation.AuthUser;
import io.cidadel.framework.authorization.annotation.RequireAuth;
import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.cidadel.service.base.controller.BaseController;
import io.tacticl.business.profile.service.UserRepoService;
import io.tacticl.data.profile.entity.UserRepo;
import io.tacticl.service.profile.dto.AttachRepoRequest;
import io.tacticl.service.profile.dto.RepoDto;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Per-user repo memory read surface — powers the Settings "Repos" section. Replaces the dead/unwired
 * {@code service-repo} module that left {@code /v1/repos} returning 404.
 */
@RestController
@RequestMapping("/v1/repos")
public class ReposController extends BaseController {

    private final UserRepoService userRepoService;

    public ReposController(UserRepoService userRepoService) {
        this.userRepoService = userRepoService;
    }

    @Override
    protected String getModuleName() {
        return "repos";
    }

    @GetMapping
    @RequireAuth
    public ResponseEntity<List<RepoDto>> list(@AuthUser AuthenticatedUser user) {
        List<RepoDto> repos = userRepoService.list(user.getUserId()).stream()
                .map(RepoDto::from)
                .toList();
        return ResponseEntity.ok(repos);
    }

    /** Attach/grant a GitHub repo to the user's repo memory. */
    @PostMapping
    @RequireAuth
    public ResponseEntity<RepoDto> attach(@AuthUser AuthenticatedUser user,
                                          @RequestBody AttachRepoRequest body) {
        UserRepo repo = userRepoService.attach(user.getUserId(), body.repoUrl());
        return ResponseEntity.ok(RepoDto.from(repo));
    }

    /** Revoke (soft-delete) an attached repo. */
    @DeleteMapping("/{repoId}")
    @RequireAuth
    public ResponseEntity<Void> revoke(@AuthUser AuthenticatedUser user,
                                       @PathVariable String repoId) {
        boolean revoked = userRepoService.revoke(user.getUserId(), repoId);
        return revoked ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
