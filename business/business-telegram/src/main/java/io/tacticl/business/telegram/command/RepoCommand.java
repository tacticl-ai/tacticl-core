package io.tacticl.business.telegram.command;

import io.tacticl.business.telegram.identity.TelegramIdentityResolver;
import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.business.telegram.permission.MemberPermissionService;
import io.tacticl.business.telegram.permission.PermissionCheck;
import io.tacticl.business.telegram.router.CommandContext;
import io.tacticl.business.telegram.router.CommandHandler;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import io.tacticl.data.conversation.entity.ConversationSession;
import io.tacticl.data.conversation.entity.SessionStatus;
import io.tacticl.data.conversation.repository.ConversationSessionRepository;
import io.tacticl.data.telegram.entity.MemberRole;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import io.tacticl.data.telegram.repository.TelegramProjectLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Handles {@code /repo <github-url>}: stamps a GitHub repo URL onto the active
 * conversation session for this (chat, sender) so that when the conversation
 * reaches alignment ({@code <<<START>>>}) the URL is forwarded into the PDLC
 * pipeline as the working repo.
 *
 * <p>Manual escape hatch — auto-detection / auto-create-by-agent is deferred per
 * the conversational-spark plan. The URL can be set during {@code GATHERING} or
 * {@code PROPOSING}; once a pipeline is {@code ACTIVE} the repo is locked.
 */
@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class RepoCommand implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(RepoCommand.class);

    // WHY: spec calls for a simple `https://github.com/<owner>/<repo>` shape with
    // no trailing path segments — the `/?` allows an optional trailing slash.
    private static final Pattern GH_URL =
            Pattern.compile("^https://github\\.com/[A-Za-z0-9._-]+/[A-Za-z0-9._-]+/?$");
    private static final List<SessionStatus> EDITABLE = List.of(
            SessionStatus.GATHERING, SessionStatus.PROPOSING);
    private static final List<SessionStatus> RESUMABLE = List.of(
            SessionStatus.GATHERING, SessionStatus.PROPOSING, SessionStatus.ACTIVE);

    private final TelegramIdentityResolver identity;
    private final MemberPermissionService permissions;
    private final TelegramProjectLinkRepository projectRepo;
    private final ConversationSessionRepository sessionRepo;
    private final TelegramOutboundQueue outbound;

    public RepoCommand(TelegramIdentityResolver identity,
                       MemberPermissionService permissions,
                       TelegramProjectLinkRepository projectRepo,
                       ConversationSessionRepository sessionRepo,
                       TelegramOutboundQueue outbound) {
        this.identity = identity;
        this.permissions = permissions;
        this.projectRepo = projectRepo;
        this.sessionRepo = sessionRepo;
        this.outbound = outbound;
    }

    @Override
    public String commandName() {
        return "/repo";
    }

    @Override
    public Scope scope() {
        return Scope.GROUP;
    }

    @Override
    public String description() {
        return "Set the GitHub repo for the active spark";
    }

    @Override
    public void handle(CommandContext ctx) {
        long chatId = ctx.chatId();

        Optional<String> userOpt = identity.resolveByChatId(ctx.telegramUserId());
        if (userOpt.isEmpty()) {
            reply(chatId, "You must link your Tacticl account first.");
            return;
        }
        String userId = userOpt.get();

        PermissionCheck check = permissions.require(chatId, userId, MemberRole.CONTRIBUTOR);
        if (!check.allowed()) {
            reply(chatId, "You need contributor role to set the repo.");
            return;
        }

        String arg = ctx.argsAfterCommand();
        if (arg.isBlank() || !GH_URL.matcher(arg.trim()).matches()) {
            reply(chatId, "Invalid URL. Use: /repo https://github.com/<owner>/<repo>");
            return;
        }
        String url = arg.trim();

        Optional<TelegramProjectLink> linkOpt = projectRepo.findByChatIdAndIsActiveTrue(chatId);
        if (linkOpt.isEmpty()) {
            reply(chatId, "No active project in this group. Use /init first.");
            return;
        }
        String projectId = linkOpt.get().getProjectId();

        Optional<ConversationSession> sessionOpt = sessionRepo
                .findFirstByProjectIdAndUserIdAndStatusInOrderByUpdatedAtDesc(
                        projectId, userId, RESUMABLE);
        if (sessionOpt.isEmpty()) {
            reply(chatId, "No active spark — start one by mentioning me or /spark <idea>.");
            return;
        }
        ConversationSession session = sessionOpt.get();
        if (!EDITABLE.contains(session.getStatus())) {
            String existing = session.getRepoUrl();
            String suffix = (existing == null || existing.isBlank()) ? "" : " with " + existing;
            reply(chatId, "Pipeline already running" + suffix + "; cannot change repo mid-run.");
            return;
        }

        session.setRepoUrl(url);
        sessionRepo.save(session);
        logger.info("Set repoUrl on session {} (project {}, user {}) to {}",
                session.getId(), projectId, userId, url);
        reply(chatId, "Repo set: " + url + ". You can keep going.");
    }

    private void reply(long chatId, String text) {
        outbound.enqueue(chatId, new OutboundMessage(SendMessageRequest.plain(chatId, text)));
    }
}
