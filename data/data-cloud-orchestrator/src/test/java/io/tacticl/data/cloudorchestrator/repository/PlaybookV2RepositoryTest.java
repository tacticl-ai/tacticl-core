package io.tacticl.data.cloudorchestrator.repository;

import io.tacticl.data.cloudorchestrator.entity.PlaybookV2;
import io.tacticl.data.sparks.entity.SparkType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaybookV2RepositoryTest {

    @Mock private PlaybookV2Repository repo;

    @Test
    void findBySparkTypesContaining_returnsMatchingPlaybooks() {
        PlaybookV2 pb = PlaybookV2.create("FULL_PDLC", "Full PDLC", "desc",
                List.of(SparkType.CODE), List.of());
        when(repo.findBySparkTypesContaining(SparkType.CODE)).thenReturn(List.of(pb));

        List<PlaybookV2> result = repo.findBySparkTypesContaining(SparkType.CODE);

        assertThat(result).containsExactly(pb);
    }

    @Test
    void findByActive_returnsActivePlaybooks() {
        PlaybookV2 pb = PlaybookV2.create("BUG_FIX", "Bug Fix", "desc",
                List.of(SparkType.CODE), List.of());
        when(repo.findByActive(true)).thenReturn(List.of(pb));

        List<PlaybookV2> result = repo.findByActive(true);

        assertThat(result).containsExactly(pb);
    }
}
