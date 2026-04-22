package io.tacticl.data.telegram.repository;

import io.tacticl.data.telegram.entity.TelegramMemberGrant;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface TelegramMemberGrantRepository extends MongoRepository<TelegramMemberGrant, String> {

    List<TelegramMemberGrant> findByProjectIdAndIsActiveTrue(String projectId);

    Optional<TelegramMemberGrant> findByProjectIdAndTacticlUserIdAndIsActiveTrue(String projectId, String userId);

    List<TelegramMemberGrant> findByTacticlUserIdAndIsActiveTrue(String tacticlUserId);
}
