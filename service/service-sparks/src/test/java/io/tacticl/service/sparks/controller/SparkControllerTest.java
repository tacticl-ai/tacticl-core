package io.tacticl.service.sparks.controller;

import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.tacticl.business.sparks.service.CheckpointService;
import io.tacticl.business.sparks.service.SparkClassifierService;
import io.tacticl.business.sparks.service.SparkEventEmitter;
import io.tacticl.business.sparks.service.SparkService;
import io.tacticl.data.sparks.entity.Checkpoint;
import io.tacticl.data.sparks.entity.CheckpointStatus;
import io.tacticl.data.sparks.entity.CheckpointType;
import io.tacticl.data.sparks.entity.Spark;
import io.tacticl.data.sparks.entity.SparkType;
import io.tacticl.service.sparks.dto.CreateSparkRequestDto;
import io.tacticl.service.sparks.dto.ResolveCheckpointRequestDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SparkControllerTest {

    @Mock SparkService sparkService;
    @Mock CheckpointService checkpointService;
    @Mock SparkClassifierService classifierService;
    @Mock SparkEventEmitter sparkEventEmitter;
    @InjectMocks SparkController controller;

    private AuthenticatedUser user(String id) {
        AuthenticatedUser u = mock(AuthenticatedUser.class);
        when(u.getUserId()).thenReturn(id);
        return u;
    }

    @Test
    void createSpark_returns201AndSparkSummary() {
        Spark spark = Spark.create("user-1", "build me a REST API");
        when(sparkService.create("user-1", "build me a REST API")).thenReturn(spark);
        when(classifierService.classify("build me a REST API")).thenReturn(SparkType.CODE);
        when(sparkService.classify(eq(spark.getId()), eq("user-1"), eq(SparkType.CODE)))
                .thenReturn(spark);

        ResponseEntity<?> resp = controller.createSpark(
                user("user-1"), new CreateSparkRequestDto("build me a REST API", "session-1"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void listSparks_returnsPage() {
        Spark spark = Spark.create("user-1", "test");
        when(sparkService.list("user-1", 0, 20))
                .thenReturn(new PageImpl<>(List.of(spark), PageRequest.of(0, 20), 1));

        ResponseEntity<?> resp = controller.listSparks(user("user-1"), 0, 20);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getSpark_foundReturns200() {
        Spark spark = Spark.create("user-1", "test");
        when(sparkService.get("user-1", spark.getId())).thenReturn(Optional.of(spark));

        ResponseEntity<?> resp = controller.getSpark(user("user-1"), spark.getId());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getSpark_notFoundReturns404() {
        when(sparkService.get("user-1", "missing")).thenReturn(Optional.empty());

        ResponseEntity<?> resp = controller.getSpark(user("user-1"), "missing");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void cancelSpark_returns204() {
        ResponseEntity<?> resp = controller.cancelSpark(user("user-1"), "spark-1");

        verify(sparkService).cancel("spark-1", "user-1");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void streamEvents_returnsSseEmitter() {
        Spark spark = Spark.create("user-1", "test");
        SseEmitter sse = new SseEmitter(30_000L);
        when(sparkService.get("user-1", "spark-1")).thenReturn(Optional.of(spark));
        when(sparkEventEmitter.register(eq("spark-1"), any())).thenReturn(sse);

        SseEmitter result = controller.streamEvents(user("user-1"), "spark-1");

        assertThat(result).isNotNull();
    }

    @Test
    void streamEvents_unknownSpark_throws404() {
        when(sparkService.get("user-1", "unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.streamEvents(user("user-1"), "unknown"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void resolveCheckpoint_returns200() {
        Checkpoint cp = Checkpoint.create("spark-1", "user-1",
                CheckpointType.APPROVAL, "Approve?");
        when(checkpointService.resolve("cp-1", "spark-1", "user-1",
                CheckpointStatus.APPROVED, "go ahead")).thenReturn(cp);

        ResponseEntity<?> resp = controller.resolveCheckpoint(
                user("user-1"), "spark-1", "cp-1",
                new ResolveCheckpointRequestDto("APPROVE", "go ahead"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
