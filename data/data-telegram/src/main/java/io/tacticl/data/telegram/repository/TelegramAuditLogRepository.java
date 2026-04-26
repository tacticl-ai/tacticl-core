package io.tacticl.data.telegram.repository;

import io.tacticl.data.telegram.entity.TelegramAuditLog;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface TelegramAuditLogRepository extends MongoRepository<TelegramAuditLog, String> {

    List<TelegramAuditLog> findByChatIdOrderByCreatedAtDesc(long chatId);

    List<TelegramAuditLog> findByTelegramUserIdOrderByCreatedAtDesc(long telegramUserId);
}
