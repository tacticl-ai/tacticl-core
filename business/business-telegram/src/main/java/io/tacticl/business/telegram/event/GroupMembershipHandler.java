package io.tacticl.business.telegram.event;

import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.client.telegram.config.TelegramConfig;
import io.tacticl.client.telegram.dto.ChatMemberUpdate;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import io.tacticl.data.telegram.repository.TelegramProjectLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class GroupMembershipHandler {

    private static final Logger logger = LoggerFactory.getLogger(GroupMembershipHandler.class);

    private static final String WELCOME =
        "\uD83D\uDC4B Hi! I'm ready to run a Tacticl project in this group. " +
        "A linked Tacticl user can claim this group by sending /init.";

    private final TelegramConfig config;
    private final TelegramProjectLinkRepository projectRepo;
    private final TelegramOutboundQueue outbound;

    public GroupMembershipHandler(TelegramConfig config,
                                  TelegramProjectLinkRepository projectRepo,
                                  TelegramOutboundQueue outbound) {
        this.config = config;
        this.projectRepo = projectRepo;
        this.outbound = outbound;
    }

    public void handle(ChatMemberUpdate update) {
        String newStatus = update.new_chat_member() != null ? update.new_chat_member().status() : null;
        String oldStatus = update.old_chat_member() != null ? update.old_chat_member().status() : null;
        String username  = update.new_chat_member() != null && update.new_chat_member().user() != null
                           ? update.new_chat_member().user().username() : null;

        // Only act on events about the bot itself
        if (username == null || !username.equalsIgnoreCase(config.getBotUsername())) return;

        long chatId = update.chat().id();

        boolean added = isPresent(newStatus) && !isPresent(oldStatus);
        boolean removed = isPresent(oldStatus) && !isPresent(newStatus);

        if (added) {
            outbound.enqueue(chatId, new OutboundMessage(SendMessageRequest.plain(chatId, WELCOME)));
        } else if (removed) {
            projectRepo.findByChatIdAndIsActiveTrue(chatId).ifPresent(link -> {
                link.orphan();
                projectRepo.save(link);
                logger.info("Project {} orphaned by bot removal from chat {}", link.getProjectId(), chatId);
            });
        }
    }

    private boolean isPresent(String status) {
        return "member".equals(status) || "administrator".equals(status) || "creator".equals(status);
    }
}
