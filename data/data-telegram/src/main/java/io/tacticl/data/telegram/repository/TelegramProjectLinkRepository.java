package io.tacticl.data.telegram.repository;

import io.tacticl.data.telegram.entity.ProjectStatus;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface TelegramProjectLinkRepository extends MongoRepository<TelegramProjectLink, String> {

    Optional<TelegramProjectLink> findByChatIdAndIsActiveTrue(long chatId);

    Optional<TelegramProjectLink> findByProjectIdAndIsActiveTrue(String projectId);

    List<TelegramProjectLink> findByOwnerUserIdAndIsActiveTrue(String ownerUserId);

    List<TelegramProjectLink> findByStatusAndIsActiveTrue(ProjectStatus status);
}
