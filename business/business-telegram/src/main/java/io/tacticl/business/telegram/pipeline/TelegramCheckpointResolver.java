package io.tacticl.business.telegram.pipeline;

import io.tacticl.business.pipeline.service.PdlcV2Service;
import io.tacticl.data.pipeline.entity.CheckpointDecision;
import io.tacticl.data.pipeline.entity.PipelineCheckpoint;
import io.tacticl.data.pipeline.repository.PipelineCheckpointRepository;
import io.tacticl.data.sparks.entity.Spark;
import io.tacticl.data.sparks.repository.SparkRepository;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import io.tacticl.data.telegram.repository.TelegramProjectLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Bridges Telegram callback buttons to {@link PdlcV2Service#resolveCheckpoint}.
 * Isolated from webhook parsing so the logic is unit-testable without Telegram plumbing.
 *
 * <p>Performs a defense-in-depth project-scope assertion: the checkpoint's spark must
 * belong to the same project that is linked to the calling chat. Without this check,
 * a malicious Telegram client could craft a callback_data carrying a checkpoint id from
 * a different project and slip past the permission check (which is chat-scoped).
 */
@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class TelegramCheckpointResolver {

    private static final Logger logger = LoggerFactory.getLogger(TelegramCheckpointResolver.class);

    private final PipelineCheckpointRepository checkpointRepo;
    private final SparkRepository sparkRepo;
    private final TelegramProjectLinkRepository linkRepo;
    private final PdlcV2Service pdlcV2Service;

    public TelegramCheckpointResolver(PipelineCheckpointRepository checkpointRepo,
                                      SparkRepository sparkRepo,
                                      TelegramProjectLinkRepository linkRepo,
                                      PdlcV2Service pdlcV2Service) {
        this.checkpointRepo = checkpointRepo;
        this.sparkRepo = sparkRepo;
        this.linkRepo = linkRepo;
        this.pdlcV2Service = pdlcV2Service;
    }

    public Result resolve(long chatId, String tacticlUserId, String checkpointId, String action) {
        Optional<CheckpointDecision> decisionOpt = mapDecision(action);
        if (decisionOpt.isEmpty()) {
            return Result.failure(ResultCode.INVALID_ACTION, "Invalid action");
        }

        Optional<PipelineCheckpoint> checkpointOpt = checkpointRepo.findById(checkpointId);
        if (checkpointOpt.isEmpty()) {
            return Result.failure(ResultCode.NOT_FOUND, "Checkpoint no longer pending");
        }
        PipelineCheckpoint checkpoint = checkpointOpt.get();
        String sparkId = checkpoint.getSparkId();

        // Defense in depth: confirm this checkpoint actually belongs to the chat's project.
        // PermissionCheck upstream only verifies the caller's role in this chat; it does not
        // know which project the checkpoint came from. We look the spark up by id alone
        // (no user filter) because the caller may legitimately be a runner who is not the
        // spark owner — ownership is enforced downstream by PdlcV2Service.
        Optional<Spark> sparkOpt = sparkRepo.findById(sparkId);
        if (sparkOpt.isEmpty()) {
            logger.warn("Checkpoint scope check: spark {} not found (checkpoint={})",
                        sparkId, checkpointId);
            return Result.failure(ResultCode.FORBIDDEN, "Checkpoint does not belong to this chat");
        }
        String sparkProjectId = sparkOpt.get().getProjectId();
        if (sparkProjectId == null || sparkProjectId.isBlank()) {
            return Result.failure(ResultCode.FORBIDDEN, "Not a group-scoped checkpoint");
        }
        Optional<TelegramProjectLink> linkOpt = linkRepo.findByChatIdAndIsActiveTrue(chatId);
        if (linkOpt.isEmpty()) {
            return Result.failure(ResultCode.FORBIDDEN, "No link for this chat");
        }
        String chatProjectId = linkOpt.get().getProjectId();
        if (!sparkProjectId.equals(chatProjectId)) {
            // WHY: cross-project callback — log loud (potential cross-chat attack) and refuse.
            logger.warn("Checkpoint scope rejected: checkpoint={} sparkProject={} chatProject={} user={}",
                        checkpointId, sparkProjectId, chatProjectId, tacticlUserId);
            return Result.failure(ResultCode.FORBIDDEN, "Checkpoint does not belong to this chat's project");
        }

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

    public enum ResultCode { OK, INVALID_ACTION, NOT_FOUND, FORBIDDEN, ERROR }

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
