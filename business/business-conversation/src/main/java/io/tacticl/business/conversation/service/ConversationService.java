package io.tacticl.business.conversation.service;

import io.cidadel.client.anthropic.AnthropicDirectClient;
import io.cidadel.client.base.llm.model.LlmMessage;
import io.cidadel.client.base.llm.model.LlmResponse;
import io.cidadel.framework.exception.CidadelException;
import io.strategiz.social.client.github.GitHubClient;
import io.strategiz.social.client.github.config.GitHubConfig;
import io.strategiz.social.client.github.model.GitHubRepository;
import io.tacticl.business.conversation.dto.MessageResponse;
import io.tacticl.business.pipeline.router.PdlcRouter;
import io.tacticl.business.sparks.service.SparkClassifierService;
import io.tacticl.business.sparks.service.SparkService;
import io.tacticl.data.conversation.entity.ConversationMessage;
import io.tacticl.data.conversation.entity.ConversationSession;
import io.tacticl.data.conversation.entity.SessionStatus;
import io.tacticl.data.conversation.repository.ConversationSessionRepository;
import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.data.pipeline.repository.PipelineRunRepository;
import io.tacticl.data.sparks.entity.Spark;
import io.tacticl.data.sparks.entity.SparkRoute;
import io.tacticl.data.sparks.entity.SparkType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);
    private static final String PROPOSE_MARKER = "<<<PROPOSE>>>";
    private static final String START_MARKER = "<<<START>>>";
    private static final String CREATE_REPO_MARKER_PREFIX = "<<<CREATE_REPO:";
    private static final Pattern CREATE_REPO_PATTERN =
            Pattern.compile("<<<CREATE_REPO:(\\{.*?\\})>>>", Pattern.DOTALL);
    private static final Pattern CREATE_REPO_STRIP_PATTERN =
            Pattern.compile("<<<CREATE_REPO:[^>]*>>>", Pattern.DOTALL);
    private static final String CONVERSATION_MODEL = "claude-sonnet-4-6";
    private static final JsonMapper JSON = new JsonMapper();
    // Effectively uncapped during pre-production rollout — mirrors AgentCommandService default.
    private static final double DEFAULT_COST_CEILING_USD = 10_000.0;

    private static final String GATHERING_SYSTEM_PROMPT_TEMPLATE = """
            You are Tacticl, a personal AI assistant gathering requirements before starting work.

            Current spark repo: %s

            Rules:
            1. Ask ONE clarifying question at a time. Never more than one per message.
            2. Be conversational and concise. Match the user's energy.
            3. If the request is software work (CODE/DEVOPS) and no repo is set above, the user
               needs a GitHub repo before you can hand off to the pipeline. Propose a name + owner
               + visibility, get the user's confirmation in chat, then emit the marker on its own
               line in your next message:
                  <<<CREATE_REPO:{"name":"<repo-name>","owner":"<user-or-org>","private":true}>>>
               - `name`: short kebab-case slug.
               - `owner`: the user's GitHub username or an org they administer. Ask if unsure.
               - `private`: default true unless the user asks for public.
               You may include an optional `"description"`.
               After CREATE_REPO emit nothing else in that message — the next user turn carries
               the result.
            4. If the user explicitly tells you the repo to use (e.g. they ran /repo or pasted a
               URL), do NOT create a new one. The repo line above will reflect it on the next turn.
            5. When you fully understand what's needed AND (for code/devops work) the repo is set,
               present a bullet-point plan summary and ask "Ready to start?". End that exact
               message with this marker on its own line: <<<PROPOSE>>>
            6. If the user approves ("yes", "go ahead", "start", "looks good", "perfect", etc.),
               write a short confirmation. End that message with this marker on its own line:
               <<<START>>>
            7. If the user revises your proposal, go back to clarifying — do NOT use <<<PROPOSE>>>
               until you have a final plan again.
            8. For non-software tasks (research, social, creative, data) no repo is needed.
               Proceed straight to PROPOSE/START.

            Emit one marker per message at most. Don't combine CREATE_REPO with PROPOSE or START.
            Be natural. You're a helpful colleague, not a form.
            """;

    private static final String ACTIVE_SYSTEM_PROMPT_TEMPLATE = """
            You are Tacticl, a personal AI assistant. You previously gathered requirements and \
            started a %s task for this user (spark ID: %s).

            Current pipeline status: %s

            You can:
            - Answer questions about what's happening in the pipeline
            - Acknowledge course corrections the user wants to make (note: applying live changes \
            to a running pipeline requires human review via the checkpoint system)

            Keep responses concise and informative.
            """;

    private final ConversationSessionRepository sessionRepository;
    private final AnthropicDirectClient anthropicClient;
    private final SparkService sparkService;
    private final SparkClassifierService sparkClassifierService;
    private final PdlcRouter pdlcRouter;
    private final PipelineRunRepository pipelineRunRepository;
    private final GitHubClient gitHubClient;
    private final GitHubConfig gitHubConfig;

    public ConversationService(ConversationSessionRepository sessionRepository,
                               AnthropicDirectClient anthropicClient,
                               SparkService sparkService,
                               SparkClassifierService sparkClassifierService,
                               PdlcRouter pdlcRouter,
                               PipelineRunRepository pipelineRunRepository,
                               GitHubClient gitHubClient,
                               GitHubConfig gitHubConfig) {
        this.sessionRepository = sessionRepository;
        this.anthropicClient = anthropicClient;
        this.sparkService = sparkService;
        this.sparkClassifierService = sparkClassifierService;
        this.pdlcRouter = pdlcRouter;
        this.pipelineRunRepository = pipelineRunRepository;
        this.gitHubClient = gitHubClient;
        this.gitHubConfig = gitHubConfig;
    }

    public ConversationSession createSession(String userId, String firstMessage) {
        ConversationSession session = ConversationSession.create(userId, firstMessage);
        return sessionRepository.save(session);
    }

    /**
     * Create a Telegram-group-scoped conversation session bound to a {@code projectId}.
     * Used by {@code TelegramConversationAdapter} when no resumable session exists for
     * the {@code (projectId, userId)} pair. Distinct from the web-initiated
     * {@link #createSession(String, String)} which has no project scope.
     */
    public ConversationSession createSession(String userId, String projectId, String firstMessage) {
        ConversationSession session = ConversationSession.createForTelegramGroup(userId, projectId, firstMessage);
        return sessionRepository.save(session);
    }

    public MessageResponse sendMessage(String sessionId, String userId, String userMessage) {
        ConversationSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        session.addMessage(ConversationMessage.user(userMessage));

        String systemPrompt = buildSystemPrompt(session);
        List<LlmMessage> messages = buildMessages(session);

        LlmResponse llmResponse = anthropicClient.generateContent(CONVERSATION_MODEL, messages, systemPrompt);
        String rawContent = llmResponse != null && llmResponse.getContent() != null
                ? llmResponse.getContent()
                : "I didn't quite catch that. Could you try again?";

        // CREATE_REPO must be handled BEFORE START / PROPOSE so a CREATE_REPO response can't
        // accidentally trigger pipeline start. If the LLM emits CREATE_REPO while we are
        // already in PROPOSING (rule violation by the prompt), revert to GATHERING — the
        // proposal needs to be reissued with the new repo URL referenced.
        if (rawContent.contains(CREATE_REPO_MARKER_PREFIX)) {
            if (session.getStatus() == SessionStatus.PROPOSING) {
                session.revertToGathering();
            }
            String cleanContent = handleCreateRepoMarker(session, rawContent);
            session.addMessage(ConversationMessage.assistant(cleanContent));
            sessionRepository.save(session);
            return new MessageResponse(cleanContent, session.getStatus().name(), null, null);
        }

        if (rawContent.contains(START_MARKER)) {
            String cleanContent = rawContent.replace(START_MARKER, "").trim();
            session.addMessage(ConversationMessage.assistant(cleanContent));

            StartResult result = startImplementation(session, userId);
            session.markActive(result.sparkId());
            sessionRepository.save(session);

            return new MessageResponse(cleanContent, SessionStatus.ACTIVE.name(),
                    result.sparkId(), result.pipelineRunId());

        } else if (rawContent.contains(PROPOSE_MARKER)) {
            String cleanContent = rawContent.replace(PROPOSE_MARKER, "").trim();
            SparkType detectedType = sparkClassifierService.classify(userMessage);
            session.markProposing(detectedType.name(), cleanContent);
            session.addMessage(ConversationMessage.assistant(cleanContent));
            sessionRepository.save(session);

            return new MessageResponse(cleanContent, SessionStatus.PROPOSING.name(), null, null);

        } else {
            if (session.getStatus() == SessionStatus.PROPOSING) {
                session.revertToGathering();
            }
            session.addMessage(ConversationMessage.assistant(rawContent));
            sessionRepository.save(session);

            return new MessageResponse(rawContent, session.getStatus().name(), null, null);
        }
    }

    public List<ConversationSession> listSessions(String userId) {
        return sessionRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    public Optional<ConversationSession> getSession(String sessionId, String userId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId);
    }

    private String buildSystemPrompt(ConversationSession session) {
        if (session.getStatus() == SessionStatus.ACTIVE && session.getSparkId() != null) {
            String status = pipelineRunRepository
                    .findFirstBySparkIdAndUserIdOrderByCreatedAtDesc(session.getSparkId(), session.getUserId())
                    .map(run -> run.getStatus().name())
                    .orElse("UNKNOWN");
            return ACTIVE_SYSTEM_PROMPT_TEMPLATE.formatted(
                    session.getDetectedSparkType(), session.getSparkId(), status);
        }
        String repoStatus = session.getRepoUrl() == null || session.getRepoUrl().isBlank()
                ? "(not yet created — propose one if this is code/devops work)"
                : session.getRepoUrl();
        return GATHERING_SYSTEM_PROMPT_TEMPLATE.formatted(repoStatus);
    }

    private List<LlmMessage> buildMessages(ConversationSession session) {
        List<LlmMessage> messages = new ArrayList<>();
        for (ConversationMessage msg : session.getMessages()) {
            if ("user".equals(msg.getRole())) {
                messages.add(LlmMessage.user(msg.getContent()));
            } else {
                messages.add(LlmMessage.assistant(msg.getContent()));
            }
        }
        return messages;
    }

    /**
     * Parse a <<<CREATE_REPO:{...}>>> marker, invoke GitHubClient.createRepo, and
     * return the user-visible reply (marker stripped, repo URL or error appended).
     * The caller is responsible for persisting the returned content as an assistant
     * message and saving the session.
     */
    private String handleCreateRepoMarker(ConversationSession session, String rawContent) {
        Matcher matcher = CREATE_REPO_PATTERN.matcher(rawContent);
        if (!matcher.find()) {
            log.warn("CREATE_REPO marker present but JSON could not be extracted from: {}", rawContent);
            String stripped = stripCreateRepoMarker(rawContent);
            return appendNote(stripped,
                    "Sorry, I couldn't parse the repo spec. Please try again with name and owner.");
        }

        String stripped = stripCreateRepoMarker(rawContent);
        CreateRepoSpec spec;
        try {
            JsonNode node = JSON.readTree(matcher.group(1));
            String name = optionalText(node, "name");
            String owner = optionalText(node, "owner");
            Boolean isPrivate = node.has("private") && !node.get("private").isNull()
                    ? node.get("private").asBoolean()
                    : null;
            String description = optionalText(node, "description");
            spec = new CreateRepoSpec(name, owner, isPrivate, description);
        } catch (JacksonException e) {
            log.warn("Failed to parse CREATE_REPO JSON payload: {}", e.getMessage());
            return appendNote(stripped,
                    "Sorry, I couldn't parse the repo spec. Please try again with name and owner.");
        }

        if (spec.name() == null || spec.name().isBlank()
                || spec.owner() == null || spec.owner().isBlank()) {
            log.warn("CREATE_REPO missing required field — name='{}' owner='{}'", spec.name(), spec.owner());
            return appendNote(stripped,
                    "Sorry, I couldn't parse the repo spec. Please try again with name and owner.");
        }

        boolean privateFlag = spec.isPrivate() == null || spec.isPrivate();
        try {
            GitHubRepository repo = gitHubClient.createRepo(
                    spec.name(), spec.owner(), privateFlag, spec.description(), gitHubConfig.getAppToken());
            session.setRepoUrl(repo.htmlUrl());
            log.info("Created repo {} for session {}", repo.htmlUrl(), session.getId());
            return appendNote(stripped, "Created " + repo.htmlUrl());
        } catch (CidadelException e) {
            log.warn("CREATE_REPO failed for name='{}' owner='{}': {}", spec.name(), spec.owner(), e.getMessage());
            return appendNote(stripped,
                    "Couldn't create the repo: " + humanize(e.getMessage()) + ". Want to try a different name?");
        }
    }

    private static String humanize(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            return "unknown error";
        }
        return errorCode.toLowerCase().replace('_', ' ');
    }

    private static String stripCreateRepoMarker(String content) {
        return CREATE_REPO_STRIP_PATTERN.matcher(content).replaceAll("").stripTrailing().trim();
    }

    private static String appendNote(String body, String note) {
        if (body.isBlank()) {
            return note;
        }
        return body + "\n\n" + note;
    }

    private static String optionalText(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String value = node.get(field).asString("");
        return value.isEmpty() ? null : value;
    }

    private StartResult startImplementation(ConversationSession session, String userId) {
        // Use the full proposal text so PDLC receives complete requirements, not the truncated title
        String sparkInput = session.getProposalText() != null
                ? session.getProposalText()
                : session.getTitle();
        SparkType sparkType = session.getDetectedSparkType() != null
                ? SparkType.valueOf(session.getDetectedSparkType())
                : SparkType.CODE;

        Spark spark = sparkService.create(userId, sparkInput);
        spark = sparkService.classify(spark.getId(), userId, sparkType);

        if (sparkType == SparkType.CODE || sparkType == SparkType.DEVOPS) {
            Optional<PipelineRun> runOpt = pdlcRouter.route(
                    userId,
                    spark.getId(),
                    sparkInput,
                    session.getRepoUrl(),
                    sparkType,
                    List.of(),
                    null,
                    DEFAULT_COST_CEILING_USD);
            if (runOpt.isPresent()) {
                sparkService.markExecuting(spark.getId(), userId, SparkRoute.CLOUD, null);
                log.info("Conversation {} routed spark {} to pipeline run {} repo={}",
                        session.getId(), spark.getId(), runOpt.get().getId(), session.getRepoUrl());
                return new StartResult(spark.getId(), runOpt.get().getId());
            }
        }

        sparkService.markExecuting(spark.getId(), userId, SparkRoute.CLOUD, null);
        log.info("Conversation {} created spark {} for type {}", session.getId(), spark.getId(), sparkType);
        return new StartResult(spark.getId(), null);
    }

    private record StartResult(String sparkId, String pipelineRunId) {}

    private record CreateRepoSpec(String name, String owner, Boolean isPrivate, String description) {}
}
