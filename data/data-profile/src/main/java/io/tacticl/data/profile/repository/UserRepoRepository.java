package io.tacticl.data.profile.repository;

import io.tacticl.data.profile.entity.UserRepo;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Per-user repo memory ({@code user_repos}). */
public interface UserRepoRepository extends MongoRepository<UserRepo, String> {

    /** Idempotent-upsert lookup: the existing row for this user+repo, if any. */
    Optional<UserRepo> findByCidadelUserIdAndRepoUrlAndIsActiveTrue(String cidadelUserId, String repoUrl);

    /** Grounding read: a user's repos, most-recently-used first (cap with Pageable/limit at the service). */
    List<UserRepo> findByCidadelUserIdAndIsActiveTrueOrderByLastUsedAtDesc(String cidadelUserId);

    /** A single repo scoped to its owning user (ownership check for revoke). */
    Optional<UserRepo> findByIdAndCidadelUserId(String id, String cidadelUserId);
}
