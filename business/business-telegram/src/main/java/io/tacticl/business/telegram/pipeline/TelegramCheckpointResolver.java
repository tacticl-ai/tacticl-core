package io.tacticl.business.telegram.pipeline;

import io.tacticl.business.pipeline.service.PdlcV2Service;
import io.tacticl.data.pipeline.entity.CheckpointDecision;
import io.tacticl.data.pipeline.entity.PipelineCheckpoint;
import io.tacticl.data.pipeline.repository.PipelineCheckpointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Bridges Telegram callback buttons to {@link PdlcV2Service#resolveCheckpoint}.
 * Isolated from webhook parsing so the logic is unit-testable without Telegram plumbing.
 */
@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class TelegramCheckpointResolver {

    private static final Logger logger = LoggerFactory.getLogger(TelegramCheckpointResolver.class);

    private final PipelineCheckpointRepository checkpointRepo;
    private final PdlcV2Service pdlcV2Service;

    public TelegramCheckpointResolver(PipelineCheckpointRepository checkpointRepo,
                                      PdlcV2Service pdlcV2Service) {
        this.checkpointRepo = checkpointRepo;
        this.pdlcV2Service = pdlcV2Service;
    }

    public Result resolve(String tacticlUserId, String checkpointId, String action) {
        Optional<CheckpointDecision> decisionOpt = mapDecision(action);
        if (decisionOpt.isEmpty()) {
            return Result.failure(ResultCode.INVALID_ACTION, "Invalid action");
        }

        Optional<PipelineCheckpoint> checkpointOpt = checkpointRepo.findById(checkpointId);
        if (checkpointOpt.isEmpty()) {
            return Result.failure(ResultCode.NOT_FOUND, "Checkpoint no longer pending");
        }

        String sparkId = checkpointOpt.get().getSparkId();
        CheckpointDecision decision = decisionOpt.get();
        try {
            pdlcV2Service.resolveCheckpoint(tacticlUserId, sparkId, checkpointId, decision, null);
        } catch (RuntimeException e) {
            // Log and surface a concise status — the upstream handler renders a toast to the user.
            logger.warn("resolveCheckpoint failed for user={} checkpoint={} action={}: {}",
                        tacticlUserId, checkpointId, action, e.getMessage());
            return Result.failure(ResultCode.ERROR, "Could not update checkpoint");
        }

        return Result.success(decision, sparkId);
    }

    // Actions mirror the callback_data format "cp:<action>:<checkpointId>" emitted by the formatter.
    private Optional<CheckpointDecision> mapDecision(String action) {
        if (action == null) return Optional.empty();
        return switch (action) {
            case "approve" -> Optional.of(CheckpointDecision.APPROVED);
            case "changes" -> Optional.of(CheckpointDecision.REWORK);
            case "reject"  -> Optional.of(CheckpointDecision.CANCEL);
            default -> Optional.empty();
        };
    }

    public enum ResultCode { OK, INVALID_ACTION, NOT_FOUND, ERROR }

    public record Result(ResultCode code, CheckpointDecision decision,
                         String sparkId, String message) {
        public static Result success(CheckpointDecision decision, String sparkId) {
            return new Result(ResultCode.OK, decision, sparkId, null);
        }
        public static Result failure(ResultCode code, String message) {
            return new Result(code, null, null, message);
        }
        public boolean isSuccess() { return code == ResultCode.OK; }
    }
}
