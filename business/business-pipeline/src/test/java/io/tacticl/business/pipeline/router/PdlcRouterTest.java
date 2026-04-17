package io.tacticl.business.pipeline.router;

import io.tacticl.business.pipeline.service.PdlcV2Service;
import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.data.sparks.entity.SparkType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PdlcRouterTest {

    @Mock PdlcV2Service pdlcV2Service;

    @Test
    void route_whenFlagEnabled_andCodeSpark_callsV2Service() {
        PdlcRouter router = new PdlcRouter(pdlcV2Service, true);
        PipelineRun mockRun = PipelineRun.create("u", "s", "r", "url", "FULL_PDLC", List.of(), 10.0);
        when(pdlcV2Service.submitPipeline(any(), any(), any(), any(), any(), any(), any(), anyDouble()))
            .thenReturn(mockRun);

        Optional<PipelineRun> result = router.route(
            "user-1", "spark-1", "req", "url", SparkType.CODE, List.of(), "token", 10.0
        );

        assertThat(result).isPresent();
        verify(pdlcV2Service).submitPipeline(any(), any(), any(), any(), any(), any(), any(), anyDouble());
    }

    @Test
    void route_whenFlagDisabled_returnsEmpty() {
        PdlcRouter router = new PdlcRouter(pdlcV2Service, false);

        Optional<PipelineRun> result = router.route(
            "user-1", "spark-1", "req", "url", SparkType.CODE, List.of(), "token", 10.0
        );

        assertThat(result).isEmpty();
        verifyNoInteractions(pdlcV2Service);
    }

    @Test
    void route_whenFlagEnabled_butNotCodeOrDevops_returnsEmpty() {
        PdlcRouter router = new PdlcRouter(pdlcV2Service, true);

        Optional<PipelineRun> result = router.route(
            "user-1", "spark-1", "req", "url", SparkType.RESEARCH, List.of(), "token", 10.0
        );

        assertThat(result).isEmpty();
        verifyNoInteractions(pdlcV2Service);
    }

    @Test
    void route_whenFlagEnabled_andDevopsSpark_callsV2Service() {
        PdlcRouter router = new PdlcRouter(pdlcV2Service, true);
        PipelineRun mockRun = PipelineRun.create("u", "s", "r", "url", "FULL_PDLC", List.of(), 10.0);
        when(pdlcV2Service.submitPipeline(any(), any(), any(), any(), any(), any(), any(), anyDouble()))
            .thenReturn(mockRun);

        Optional<PipelineRun> result = router.route(
            "user-1", "spark-1", "req", "url", SparkType.DEVOPS, List.of(), "token", 10.0
        );

        assertThat(result).isPresent();
    }
}
