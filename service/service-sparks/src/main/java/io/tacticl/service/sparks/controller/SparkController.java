package io.tacticl.service.sparks.controller;

import io.cidadel.framework.authorization.annotation.AuthUser;
import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.cidadel.service.base.controller.BaseController;
import io.tacticl.business.sparks.service.CheckpointService;
import io.tacticl.business.sparks.service.SparkClassifierService;
import io.tacticl.business.sparks.service.SparkEventEmitter;
import io.tacticl.business.sparks.service.SparkService;
import io.tacticl.data.sparks.entity.Checkpoint;
import io.tacticl.data.sparks.entity.CheckpointStatus;
import io.tacticl.data.sparks.entity.Spark;
import io.tacticl.data.sparks.entity.SparkType;
import io.tacticl.service.sparks.dto.CheckpointDetailDto;
import io.tacticl.service.sparks.dto.CreateSparkRequestDto;
import io.tacticl.service.sparks.dto.ResolveCheckpointRequestDto;
import io.tacticl.service.sparks.dto.SparkDetailDto;
import io.tacticl.service.sparks.dto.SparkSummaryDto;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.Map;

@RestController
@RequestMapping("/v1/sparks")
public class SparkController extends BaseController {

    private final SparkService sparkService;
    private final CheckpointService checkpointService;
    private final SparkClassifierService classifierService;
    private final SparkEventEmitter sparkEventEmitter;

    public SparkController(SparkService sparkService,
                           CheckpointService checkpointService,
                           SparkClassifierService classifierService,
                           SparkEventEmitter sparkEventEmitter) {
        this.sparkService = sparkService;
        this.checkpointService = checkpointService;
        this.classifierService = classifierService;
        this.sparkEventEmitter = sparkEventEmitter;
    }

    @Override
    protected String getModuleName() { return "sparks"; }

    @PostMapping
    public ResponseEntity<SparkSummaryDto> createSpark(
            @AuthUser AuthenticatedUser user,
            @RequestBody CreateSparkRequestDto body) {
        Spark spark = sparkService.create(user.getUserId(), body.input());
        SparkType type = classifierService.classify(body.input());
        spark = sparkService.classify(spark.getId(), user.getUserId(), type);
        return ResponseEntity.status(HttpStatus.CREATED).body(toSummary(spark));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listSparks(
            @AuthUser AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Spark> sparksPage = sparkService.list(user.getUserId(), page, size);
        return ResponseEntity.ok(Map.of(
                "content", sparksPage.getContent().stream().map(this::toSummary).toList(),
                "totalElements", sparksPage.getTotalElements(),
                "page", page,
                "size", size
        ));
    }

    @GetMapping("/{sparkId}")
    public ResponseEntity<SparkDetailDto> getSpark(
            @AuthUser AuthenticatedUser user,
            @PathVariable String sparkId) {
        return sparkService.get(user.getUserId(), sparkId)
                .map(s -> ResponseEntity.ok(toDetail(s)))
                .orElse(ResponseEntity.<SparkDetailDto>notFound().build());
    }

    @DeleteMapping("/{sparkId}")
    public ResponseEntity<Void> cancelSpark(
            @AuthUser AuthenticatedUser user,
            @PathVariable String sparkId) {
        sparkService.cancel(sparkId, user.getUserId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/{sparkId}/events", produces = "text/event-stream")
    public SseEmitter streamEvents(
            @AuthUser AuthenticatedUser user,
            @PathVariable String sparkId) {
        sparkService.get(user.getUserId(), sparkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        SseEmitter emitter = new SseEmitter(300_000L);
        return sparkEventEmitter.register(sparkId, emitter);
    }

    @PostMapping("/{sparkId}/checkpoint/{checkpointId}")
    public ResponseEntity<CheckpointDetailDto> resolveCheckpoint(
            @AuthUser AuthenticatedUser user,
            @PathVariable String sparkId,
            @PathVariable String checkpointId,
            @RequestBody ResolveCheckpointRequestDto body) {
        CheckpointStatus decision = "APPROVE".equalsIgnoreCase(body.decision())
                ? CheckpointStatus.APPROVED : CheckpointStatus.DENIED;
        Checkpoint cp = checkpointService.resolve(
                checkpointId, sparkId, user.getUserId(), decision, body.instructions());
        return ResponseEntity.ok(toCheckpointDto(cp));
    }

    private SparkSummaryDto toSummary(Spark s) {
        return new SparkSummaryDto(
                s.getId(), s.getStatus().name(),
                s.getType() != null ? s.getType().name() : null,
                s.getRoute() != null ? s.getRoute().name() : null,
                s.getCreatedAt() != null ? s.getCreatedAt().toString() : null,
                s.getCompletedAt() != null ? s.getCompletedAt().toString() : null
        );
    }

    private SparkDetailDto toDetail(Spark s) {
        return new SparkDetailDto(
                s.getId(), s.getInput(), s.getStatus().name(),
                s.getType() != null ? s.getType().name() : null,
                s.getRoute() != null ? s.getRoute().name() : null,
                s.getDeviceId(), s.getPipelineRunId(),
                s.getTokenCost(), s.getModelUsed(),
                s.getCreatedAt() != null ? s.getCreatedAt().toString() : null,
                s.getStartedAt() != null ? s.getStartedAt().toString() : null,
                s.getCompletedAt() != null ? s.getCompletedAt().toString() : null
        );
    }

    private CheckpointDetailDto toCheckpointDto(Checkpoint cp) {
        return new CheckpointDetailDto(
                cp.getId(), cp.getSparkId(), cp.getType().name(), cp.getPrompt(),
                cp.getStatus().name(),
                cp.getCreatedAt() != null ? cp.getCreatedAt().toString() : null,
                cp.getResolvedAt() != null ? cp.getResolvedAt().toString() : null
        );
    }
}
