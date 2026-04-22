package io.tacticl.business.telegram.event;

import io.tacticl.client.telegram.dto.Message;
import io.tacticl.data.telegram.repository.TelegramProjectLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class GroupMigrationHandler {

    private static final Logger logger = LoggerFactory.getLogger(GroupMigrationHandler.class);

    private final TelegramProjectLinkRepository projectRepo;

    public GroupMigrationHandler(TelegramProjectLinkRepository projectRepo) {
        this.projectRepo = projectRepo;
    }

    public void handle(Message message) {
        Long newChatId = message.migrate_to_chat_id();
        if (newChatId == null) return;
        long oldChatId = message.chat().id();
        projectRepo.findByChatIdAndIsActiveTrue(oldChatId).ifPresent(link -> {
            link.migrateTo(newChatId);
            projectRepo.save(link);
            logger.info("Project {} migrated from chat {} to supergroup {}",
                link.getProjectId(), oldChatId, newChatId);
        });
    }
}
