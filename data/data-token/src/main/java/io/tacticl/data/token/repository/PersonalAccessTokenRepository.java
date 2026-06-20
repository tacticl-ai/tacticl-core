package io.tacticl.data.token.repository;

import io.tacticl.data.token.entity.PersonalAccessToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

/** User personal access tokens ({@code personal_access_tokens}). */
public interface PersonalAccessTokenRepository extends MongoRepository<PersonalAccessToken, String> {

    /** A user's active tokens, newest first. */
    List<PersonalAccessToken> findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(String userId);

    /** A single token scoped to its owning user (ownership check for revoke). */
    Optional<PersonalAccessToken> findByIdAndUserId(String id, String userId);

    /** Active token by hash — for future auth-filter validation (not wired this slice). */
    Optional<PersonalAccessToken> findByTokenHashAndIsActiveTrue(String tokenHash);
}
