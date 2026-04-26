package io.tacticl.business.telegram.command;

import io.cidadel.framework.exception.CidadelException;
import io.tacticl.business.telegram.audit.TelegramAuditLogger;
import io.tacticl.business.telegram.identity.TelegramIdentityResolver;
import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.business.telegram.router.CommandContext;
import io.tacticl.business.telegram.router.CommandHandler;
import io.tacticl.client.telegram.TelegramBotClient;
import io.tacticl.client.telegram.dto.ForumTopic;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import io.tacticl.data.pipeline.entity.PdlcRole;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import io.tacticl.data.telegram.repository.TelegramProjectLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles the {@code /init} slash-command: claims the current Telegram group
 * as a Tacticl project for the linked sender.
 *
 * <p>Behaviour:
 * <ol>
 *   <li>If the sender is not linked to a Tacticl user → DM them a link prompt
 *       and abort (no project is created).</li>
 *   <li>If the group already has an active {@link TelegramProjectLink} → reply
 *       in the group with the existing project id and abort.</li>
 *   <li>Otherwise persist a new link, auto-create one forum topic per
 *       {@link PdlcRole} when the chat is a forum supergroup, and post a
 *       welcome message citing the owner and the available command set.</li>
 * </ol>
 *
 * <p>V1 generates a random UUID as the project id. A dedicated Project entity
 * is tracked in a later chunk of the Telegram integration plan.
 */
@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class InitCommand implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(InitCommand.class);

    private static final String LINK_PROMPT =
            "Link your Tacticl account first: https://tacticl.ai/telegram/link";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final TelegramIdentityResolver identity;
    private final TelegramProjectLinkRepository projectRepo;
    private final TelegramOutboundQueue outbound;
    private final TelegramBotClient bot;
    private final TelegramAuditLogger auditLogger;

    public InitCommand(TelegramIdentityResolver identity,
                       TelegramProjectLinkRepository projectRepo,
                       TelegramOutboundQueue outbound,
                       TelegramBotClient bot,
                       TelegramAuditLogger auditLogger) {
        this.identity = identity;
        this.projectRepo = projectRepo;
        this.outbound = outbound;
        this.bot = bot;
        this.auditLogger = auditLogger;
    }

    @Override
    public String commandName() {
        return "/init";
    }

    @Override
    public Scope scope() {
        return Scope.GROUP;
    }

    @Override
    public void handle(CommandContext ctx) {
        Optional<String> tacticlUserId = identity.resolveByChatId(ctx.telegramUserId());
        if (tacticlUserId.isEmpty()) {
            // DM the user their link instructions — chat_id == telegram user id
            // for private chats with the bot.
            outbound.enqueue(ctx.telegramUserId(), new OutboundMessage(
                    SendMessageRequest.plain(ctx.telegramUserId(), LINK_PROMPT)));
            audit(ctx, null, Map.of("rejected", "unlinked_sender"));
            return;
        }

        Optional<TelegramProjectLink> existing =
                projectRepo.findByChatIdAndIsActiveTrue(ctx.chatId());
        if (existing.isPresent()) {
            outbound.enqueue(ctx.chatId(), new OutboundMessage(
                    SendMessageRequest.plain(ctx.chatId(),
                            "Already linked to project " + existing.get().getProjectId())));
            audit(ctx, tacticlUserId.get(),
                    Map.of("rejected", "already_linked", "projectId", existing.get().getProjectId()));
            return;
        }

        String projectId = UUID.randomUUID().toString();
        String title = Optional.ofNullable(ctx.raw().chat().title()).orElse("Untitled group");
        TelegramProjectLink link = TelegramProjectLink.create(
                projectId, ctx.chatId(), tacticlUserId.get(), title);
        projectRepo.save(link);
        logger.info("Project {} claimed by {} for chat {}",
                projectId, tacticlUserId.get(), ctx.chatId());

        // Auto-provision one forum topic per PdlcRole when the chat supports them.
        // Boolean.TRUE.equals is null-safe — Telegram omits is_forum on non-forum chats.
        if ("supergroup".equals(ctx.raw().chat().type())
                && Boolean.TRUE.equals(ctx.raw().chat().is_forum())) {
            Map<PdlcRole, Long> topics = new LinkedHashMap<>();
            for (PdlcRole role : PdlcRole.values()) {
                try {
                    ForumTopic topic = bot.createForumTopic(ctx.chatId(), role.name());
                    topics.put(role, topic.message_thread_id());
                } catch (CidadelException e) {
                    // WHY: CHAT_NOT_FORUM (and other API rejections) apply to the whole chat —
                    // retrying for subsequent roles is futile. Persist whatever we got and move on.
                    logger.warn("Forum-topic creation failed for project {} chat {} role {} — aborting topic loop",
                            projectId, ctx.chatId(), role.name(), e);
                    break;
                }
            }
            if (!topics.isEmpty()) {
                // Second save lets @Version drive optimistic locking on the topics attachment.
                link.setForumTopics(topics);
                projectRepo.save(link);
            }
        }

        String welcome = String.format(
                "Project created by @%s.%n"
                        + "Owner: %s%n"
                        + "Commands: /grant /revoke /transfer /members /status /help",
                Optional.ofNullable(ctx.senderUsername()).orElse("unknown"),
                tacticlUserId.get());
        outbound.enqueue(ctx.chatId(), new OutboundMessage(
                SendMessageRequest.plain(ctx.chatId(), welcome)));

        audit(ctx, tacticlUserId.get(), Map.of("projectId", projectId));
    }

    private void audit(CommandContext ctx, String tacticlUserId, Map<String, ?> payload) {
        String json;
        try {
            json = MAPPER.writeValueAsString(payload);
        } catch (RuntimeException e) {
            // WHY: serialization failure must not derail audit — drop payload, keep verb.
            json = null;
        }
        auditLogger.record(ctx.chatId(), ctx.telegramUserId(), tacticlUserId, "INIT", json);
    }
}
