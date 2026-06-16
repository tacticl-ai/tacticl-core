package io.tacticl.service.profile.dto;

import io.tacticl.data.profile.entity.UserRepo;

/** A remembered repo (per-user repo memory) as returned to clients for the Settings Repos section. */
public record RepoDto(String repoUrl, String owner, String name, String source, String kind) {

    public static RepoDto from(UserRepo repo) {
        return new RepoDto(
                repo.getRepoUrl(),
                repo.getOwner(),
                repo.getName(),
                repo.getSource() == null ? null : repo.getSource().name(),
                repo.getKind() == null ? null : repo.getKind().name());
    }
}
