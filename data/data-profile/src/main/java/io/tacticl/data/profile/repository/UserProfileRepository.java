package io.tacticl.data.profile.repository;

import io.tacticl.data.profile.entity.UserProfile;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface UserProfileRepository extends MongoRepository<UserProfile, String> {
    Optional<UserProfile> findByCidadelUserIdAndIsActiveTrue(String cidadelUserId);
}
