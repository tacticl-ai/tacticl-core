package io.tacticl.business.sparks.service;

import io.tacticl.data.sparks.entity.*;
import io.tacticl.data.sparks.repository.CheckpointRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckpointServiceTest {

    @Mock CheckpointRepository checkpointRepository;
    @InjectMocks CheckpointService checkpointService;

    @Test
    void create_savesCheckpoint() {
        when(checkpointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Checkpoint cp = checkpointService.create("spark-1", "user-1",
                CheckpointType.APPROVAL, "Approve PR?");
        assertThat(cp.getStatus()).isEqualTo(CheckpointStatus.PENDING);
        verify(checkpointRepository).save(cp);
    }

    @Test
    void resolve_updatesCheckpointStatus() {
        Checkpoint cp = Checkpoint.create("spark-1", "user-1",
                CheckpointType.APPROVAL, "Approve?");
        when(checkpointRepository.findByIdAndSparkIdAndUserId("cp-1", "spark-1", "user-1"))
                .thenReturn(Optional.of(cp));
        when(checkpointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Checkpoint resolved = checkpointService.resolve("cp-1", "spark-1", "user-1",
                CheckpointStatus.APPROVED, "go ahead");
        assertThat(resolved.getStatus()).isEqualTo(CheckpointStatus.APPROVED);
    }

    @Test
    void resolve_notFound_throws() {
        when(checkpointRepository.findByIdAndSparkIdAndUserId(any(), any(), any()))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> checkpointService.resolve(
                "cp-1", "spark-1", "user-1", CheckpointStatus.APPROVED, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
