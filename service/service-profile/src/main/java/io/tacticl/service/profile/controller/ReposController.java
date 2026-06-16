package io.tacticl.service.profile.controller;

import io.cidadel.framework.authorization.annotation.AuthUser;
import io.cidadel.framework.authorization.annotation.RequireAuth;
import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.cidadel.service.base.controller.BaseController;
import io.tacticl.business.profile.service.UserRepoService;
import io.tacticl.service.profile.dto.RepoDto;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
}
