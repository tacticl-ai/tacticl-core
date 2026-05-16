package io.tacticl.business.conversation.service;

import io.cidadel.client.anthropic.AnthropicDirectClient;
import io.cidadel.client.base.llm.model.LlmMessage;
import io.cidadel.client.base.llm.model.LlmResponse;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);
    private static final String PROPOSE_MARKER = "<<<PROPOSE>>>";
    private static final String START_MARKER = "<<<START>>>";
    private static final String CONVERSATION_MODEL = "claude-sonnet-4-6";
    // Effectively uncapped during pre-production rollout — mirrors AgentCommandService default.
    private static final double DEFAULT_COST_CEILING_USD = 10_000.0;

    private static final String GATHERING_SYSTEM_PROMPT = """
            You are Tacticl, a personal AI assistant gathering requirements before starting work.

            Rules:
            1. Ask ONE clarifying question at a time. Never more than one per message.
            2. Be conversational and concise. Match the user's energy.
            3. When you fully understand what's needed, present a bullet-point plan summary and \
            ask "Ready to start?". End that exact message with this marker on its own line: <<<PROPOSE>>>
            4. If the user approves ("yes", "go ahead", "start", "looks good", "perfect", etc.), \
            write a short confirmation. End that message with this marker on its own line: <<<START>>>
            5. If the user revises your proposal, go back to clarifying — do NOT use <<<PROPOSE>>> \
            until you have a final plan again.

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

    public ConversationService(ConversationSessionRepository sessionRepository,
                               AnthropicDirectClient anthropicClient,
                               SparkService sparkService,
                               SparkClassifierService sparkClassifierService,
                               PdlcRouter pdlcRouter,
                               PipelineRunRepository pipelineRunRepository) {
        this.sessionRepository = sessionRepository;
        this.anthropicClient = anthropicClient;
        this.sparkService = sparkService;
        this.sparkClassifierService = sparkClassifierService;
        this.pdlcRouter = pdlcRouter;
        this.pipelineRunRepository = pipelineRunRepository;
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
        return GATHERING_SYSTEM_PROMPT;
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
}
