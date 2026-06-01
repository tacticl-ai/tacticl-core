package io.tacticl.business.pipeline.channel;

import io.tacticl.business.pipeline.dto.PipelineCallbackEvent;
import io.tacticl.data.cloudorchestrator.entity.SessionStatus;
import io.tacticl.data.cloudorchestrator.entity.Turn;
import io.tacticl.data.conversation.entity.ConversationSession;
import io.tacticl.data.conversation.repository.ConversationSessionRepository;
import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.data.pipeline.repository.PipelineRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Mirrors pipeline events into the originating ConversationSession.messages so
 * the user-facing thread (web chat or Telegram) reflects pipeline progress.
 * Additive to TelegramEventChannel/SseEventChannel — those continue to fan out
 * to live channels; this one is the durable record on the session.
 */
@Component
public class ConversationEventChannel implements PipelineEventChannel {

    private static final Logger log = LoggerFactory.getLogger(ConversationEventChannel.class);

    private final ConversationSessionRepository sessionRepo;
    private final PipelineRunRepository runRepo;

    public ConversationEventChannel(ConversationSessionRepository sessionRepo,
                                    PipelineRunRepository runRepo) {
        this.sessionRepo = sessionRepo;
        this.runRepo = runRepo;
    }

    @Override
    public void emit(PipelineCallbackEvent event) {
        if (event == null || event.pipelineRunId() == null) return;
        Optional<PipelineRun> runOpt = runRepo.findById(event.pipelineRunId());
        if (runOpt.isEmpty()) return;
        String sparkId = runOpt.get().getSparkId();
        if (sparkId == null) return;
        Optional<ConversationSession> sessionOpt = sessionRepo.findBySparkId(sparkId);
        if (sessionOpt.isEmpty()) return;
        ConversationSession session = sessionOpt.get();
        String summary = summarize(event);
        if (summary == null) return;
        session.appendTurn(Turn.assistant("product-manager", summary, "text"));
        if (isTerminal(event.eventType()) && session.getStatus() != SessionStatus.COMPLETED) {
            session.changeStatus(SessionStatus.COMPLETED);
        }
        sessionRepo.save(session);
        log.debug("Mirrored pipeline event {} (run={}, session={}) into conversation",
                event.eventType(), event.pipelineRunId(), session.getId());
    }

    @Override
    public void complete(String pipelineRunId) {
        // No emitter-side cleanup; the channel doesn't hold per-run state.
    }

    private static String summarize(PipelineCallbackEvent e) {
        return switch (e.eventType()) {
            case "ROLE_STARTED" -> e.role() + ": working";
            case "ROLE_COMPLETED" -> e.role() + ": done";
            case "CHECKPOINT_REQUESTED" -> "Checkpoint requested (" + e.role() + ")";
            case "PIPELINE_COMPLETED" -> "Pipeline complete.";
            case "PIPELINE_FAILED" -> "Pipeline failed.";
            case "PIPELINE_CANCELLED" -> "Pipeline cancelled.";
            default -> null;
        };
    }

    private static boolean isTerminal(String type) {
        return "PIPELINE_COMPLETED".equals(type)
            || "PIPELINE_FAILED".equals(type)
            || "PIPELINE_CANCELLED".equals(type);
    }
}
