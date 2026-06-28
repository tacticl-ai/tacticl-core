package io.tacticl.business.conversation.service;

import io.strategiz.social.client.github.config.GitHubConfig;
import io.tacticl.business.conversation.dto.MessageResponse;
import io.tacticl.business.pipeline.router.PdlcRouter;
import io.tacticl.business.sparks.service.SparkClassifierService;
import io.tacticl.business.sparks.service.SparkService;
import io.tacticl.client.arbiter.conversation.ConverseEventListener;
import io.tacticl.client.arbiter.conversation.ConverseTurnInput;
import io.tacticl.client.arbiter.conversation.ConversationServiceClient;
import io.tacticl.data.cloudorchestrator.entity.SessionStatus;
import io.tacticl.data.cloudorchestrator.entity.Turn;
import io.tacticl.data.conversation.entity.ConversationSession;
import io.tacticl.data.conversation.repository.ConversationSessionRepository;
import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.data.sparks.entity.Spark;
import io.tacticl.data.sparks.entity.SparkRoute;
import io.tacticl.data.sparks.entity.SparkType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Web/Telegram conversation entry point. This is a THIN synchronous delegate to the arbiter's
 * durable Temporal analyst (the same {@code ConverseTurn} brain {@code DiscordConversationTurnHandler}
 * uses) — it holds NO conversational logic of its own. The legacy in-JVM marker state machine
 * ({@code <<<PROPOSE>>>}/{@code <<<START>>>}/{@code <<<CREATE_REPO>>>} driven by a direct Anthropic
 * call) has been removed; every turn now runs on the orchestrator.
 *
 * <p>{@code sendMessage} relays the user text to {@link ConversationServiceClient#converseTurn}, blocks
 * on the streamed reply (gRPC callbacks land on a stream thread; a latch settles the REST thread),
 * accumulates the persona's reply, and dispatches any {@code start_pipeline} skill the persona invokes
 * through {@link PdlcRouter} (mirroring the old hand-off, but sourced from the tool call rather than a
 * marker). {@code createSession}/{@code listSessions}/{@code getSession} keep their exact contract: the
 * thin {@link ConversationSession} Mongo record is the source for the web list/get surface.
 *
 * <p>When the arbiter conversation client is absent (its flag {@code tacticl.voice.arbiter-conversation.enabled}
 * is off, e.g. local/QA) the turn degrades to a graceful offline message rather than failing.
 */
@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);
    private static final JsonMapper JSON = new JsonMapper();
    private static final String SKILL_START_PIPELINE = "start_pipeline";
    private static final String PRODUCT_TACTICL = "tacticl";
    private static final String DEFAULT_PERSONA = "product-manager";
    // Effectively uncapped during pre-production rollout — mirrors AgentCommandService default.
    private static final double DEFAULT_COST_CEILING_USD = 10_000.0;
    // Just above the gRPC client's 300s deadline so its onError settles the latch before we give up.
    private static final long TURN_TIMEOUT_SECONDS = 330L;

    private final ConversationSessionRepository sessionRepository;
    private final ObjectProvider<ConversationServiceClient> conversationClient;
    private final SparkService sparkService;
    private final SparkClassifierService sparkClassifierService;
    private final PdlcRouter pdlcRouter;
    private final GitHubConfig gitHubConfig;

    public ConversationService(ConversationSessionRepository sessionRepository,
                               ObjectProvider<ConversationServiceClient> conversationClient,
                               SparkService sparkService,
                               SparkClassifierService sparkClassifierService,
                               PdlcRouter pdlcRouter,
                               GitHubConfig gitHubConfig) {
        this.sessionRepository = sessionRepository;
        this.conversationClient = conversationClient;
        this.sparkService = sparkService;
        this.sparkClassifierService = sparkClassifierService;
        this.pdlcRouter = pdlcRouter;
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

        session.appendTurn(Turn.user(userMessage, "text"));

        ConversationServiceClient client = conversationClient.getIfAvailable();
        if (client == null) {
            log.warn("Conversation turn but no arbiter conversation client is wired "
                    + "(tacticl.voice.arbiter-conversation.enabled?) — degrading (session={})", sessionId);
            return offline(session,
                    "The conversation brain is offline right now — please try again shortly.");
        }

        // sessionId == the Mongo session id → the arbiter keys its durable conv-{id} workflow memory
        // by it, so each web conversation is its own persistent analyst session (history kept server-side).
        ConverseTurnInput input = new ConverseTurnInput(
                PRODUCT_TACTICL,
                userId,
                session.getId(),
                "t-" + UUID.randomUUID(),
                userMessage,
                /* personaHint */ null,
                /* history — the persistent brain keeps its own session memory */ List.of(),
                /* locale */ null,
                /* githubToken — enables the arbiter create_repo skill + pipeline repo access */
                gitHubConfig.getAppToken(),
                /* repos */ List.of(),
                /* pipelines — the brain can call run_status */ List.of(),
                /* canDispatch — the authenticated web caller owns this session */ true);

        WebSink sink = new WebSink();
        client.converseTurn(input, sink);

        boolean settled;
        try {
            settled = sink.done.await(TURN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return offline(session, "Sorry — that got interrupted. Try me again.");
        }
        if (!settled || sink.errorMessage != null) {
            String msg = sink.errorMessage != null && !sink.errorMessage.isBlank()
                    ? sink.errorMessage
                    : "Sorry — I hit a snag. Try me again.";
            return offline(session, msg);
        }

        String content = sink.reply.toString().trim();
        String personaId = sink.personaId != null ? sink.personaId : DEFAULT_PERSONA;

        String sparkId = null;
        String pipelineRunId = null;
        if (sink.startSparkInput != null && !sink.startSparkInput.isBlank()) {
            StartResult result = dispatchPipeline(userId, sink.startSparkInput, sink.startRepoUrl);
            sparkId = result.sparkId();
            pipelineRunId = result.pipelineRunId();
            session.recordStartedSpark(sparkId);
            session.changeStatus(SessionStatus.PIPELINE_ACTIVE);
        } else {
            session.changeStatus(SessionStatus.ENGAGED);
        }

        session.appendTurn(Turn.assistant(personaId, content, "text"));
        sessionRepository.save(session);

        return new MessageResponse(content, webStatus(session.getStatus()), sparkId, pipelineRunId);
    }

    public List<ConversationSession> listSessions(String userId) {
        return sessionRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    public Optional<ConversationSession> getSession(String sessionId, String userId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId);
    }

    /** Persist an assistant turn carrying a safe message and return it without advancing state. */
    private MessageResponse offline(ConversationSession session, String message) {
        session.appendTurn(Turn.assistant(DEFAULT_PERSONA, message, "text"));
        sessionRepository.save(session);
        return new MessageResponse(message, webStatus(session.getStatus()), null, null);
    }

    /**
     * Execute a {@code start_pipeline} skill the analyst invoked: create + classify the spark and,
     * for CODE/DEVOPS, route it onto the PDLC pipeline. Mirrors the retired marker hand-off but is
     * driven by the persona's tool call (sparkInput/repoUrl) rather than session proposal text.
     */
    private StartResult dispatchPipeline(String userId, String sparkInput, String repoUrl) {
        Spark spark = sparkService.create(userId, sparkInput);
        SparkType sparkType = sparkClassifierService.classify(sparkInput);
        spark = sparkService.classify(spark.getId(), userId, sparkType);

        if (sparkType == SparkType.CODE || sparkType == SparkType.DEVOPS) {
            Optional<PipelineRun> runOpt = pdlcRouter.route(
                    userId,
                    spark.getId(),
                    sparkInput,
                    repoUrl,
                    sparkType,
                    List.of(),
                    gitHubConfig.getAppToken(),
                    DEFAULT_COST_CEILING_USD);
            if (runOpt.isPresent()) {
                sparkService.markExecuting(spark.getId(), userId, SparkRoute.CLOUD, null);
                log.info("Conversation routed spark {} to pipeline run {} repo={}",
                        spark.getId(), runOpt.get().getId(), repoUrl);
                return new StartResult(spark.getId(), runOpt.get().getId());
            }
        }

        sparkService.markExecuting(spark.getId(), userId, SparkRoute.CLOUD, null);
        log.info("Conversation created spark {} for type {}", spark.getId(), sparkType);
        return new StartResult(spark.getId(), null);
    }

    /** Collapse the orchestrator's 10-state enum onto the 4-value web {@code ConversationStatus} union. */
    private static String webStatus(SessionStatus status) {
        return switch (status) {
            case IDLE, ENGAGED, GATHERING -> "GATHERING";
            case PROPOSING, CONFIRMED -> "PROPOSING";
            case PIPELINE_ACTIVE, PIPELINE_BLOCKED -> "ACTIVE";
            case COMPLETED, ABANDONED, CANCELLED -> "COMPLETED";
        };
    }

    /** Read the first present, non-blank string field (camel or snake) from a tool-input JSON. */
    private static String stringField(String inputJson, String camel, String snake) {
        if (inputJson == null || inputJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = JSON.readTree(inputJson);
            JsonNode v = root.path(camel);
            if (v.isMissingNode() || v.isNull()) {
                v = root.path(snake);
            }
            String s = v.asString("");
            return s.isBlank() ? null : s;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Accumulates one turn's streamed reply and captures a {@code start_pipeline} tool call.
     * gRPC serializes a stream's callbacks, so no extra synchronization is needed beyond the
     * terminal latch ({@code onDone}/{@code onError} — exactly one fires per turn).
     */
    private static final class WebSink implements ConverseEventListener {
        private final StringBuilder reply = new StringBuilder();
        private final CountDownLatch done = new CountDownLatch(1);
        private volatile String personaId;
        private volatile String startSparkInput;
        private volatile String startRepoUrl;
        private volatile String errorMessage;

        @Override
        public void onStarted(String personaId) {
            if (personaId != null && !personaId.isBlank()) {
                this.personaId = personaId;
            }
        }

        @Override
        public void onToken(String textDelta, String personaId) {
            if (personaId != null && !personaId.isBlank()) {
                this.personaId = personaId;
            }
            if (textDelta != null && !textDelta.isEmpty()) {
                reply.append(textDelta);
            }
        }

        @Override
        public void onToolUse(String name, String inputJson, boolean terminal) {
            if (!SKILL_START_PIPELINE.equals(name)) {
                // create_repo and other arbiter-internal skills run inside the arbiter; ignore here.
                return;
            }
            this.startSparkInput = stringField(inputJson, "sparkInput", "spark_input");
            this.startRepoUrl = stringField(inputJson, "repoUrl", "repo_url");
        }

        @Override
        public void onDone() {
            done.countDown();
        }

        @Override
        public void onError(String userSafeMessage) {
            this.errorMessage = userSafeMessage;
            done.countDown();
        }
    }

    private record StartResult(String sparkId, String pipelineRunId) {}
}
