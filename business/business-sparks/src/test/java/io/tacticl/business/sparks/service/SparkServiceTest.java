package io.tacticl.business.sparks.service;

import io.tacticl.data.sparks.entity.*;
import io.tacticl.data.sparks.repository.SparkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SparkServiceTest {

    @Mock SparkRepository sparkRepository;
    @InjectMocks SparkService sparkService;

    @Test
    void create_savesAndReturnsSpark() {
        when(sparkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Spark spark = sparkService.create("user-1", "build me a REST API");
        assertThat(spark.getStatus()).isEqualTo(SparkStatus.PENDING);
        assertThat(spark.getUserId()).isEqualTo("user-1");
        verify(sparkRepository).save(any());
    }

    @Test
    void create_withProvenance_stampsFieldsBeforeSinglePersist() {
        when(sparkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Spark spark = sparkService.create(
                "user-1",
                "build me a REST API",
                SparkInitiatorSource.TELEGRAM_GROUP,
                "user-1",
                "project-1");

        ArgumentCaptor<Spark> captor = ArgumentCaptor.forClass(Spark.class);
        verify(sparkRepository, times(1)).save(captor.capture());
        Spark persisted = captor.getValue();
        assertThat(persisted.getInitiatorSource()).isEqualTo(SparkInitiatorSource.TELEGRAM_GROUP);
        assertThat(persisted.getInitiatorUserId()).isEqualTo("user-1");
        assertThat(persisted.getProjectId()).isEqualTo("project-1");
        assertThat(persisted.getUserId()).isEqualTo("user-1");
        assertThat(persisted.getStatus()).isEqualTo(SparkStatus.PENDING);
        assertThat(spark).isSameAs(persisted);
    }

    @Test
    void classify_updatesSparkAndSaves() {
        Spark spark = Spark.create("user-1", "build me a REST API");
        when(sparkRepository.findByIdAndUserId(spark.getId(), "user-1")).thenReturn(Optional.of(spark));
        when(sparkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        sparkService.classify(spark.getId(), "user-1", SparkType.CODE);
        assertThat(spark.getStatus()).isEqualTo(SparkStatus.ROUTING);
        verify(sparkRepository).save(spark);
    }

    @Test
    void get_returnsOptionalSpark() {
        Spark spark = Spark.create("user-1", "test");
        when(sparkRepository.findByIdAndUserId("spark-1", "user-1")).thenReturn(Optional.of(spark));
        Optional<Spark> result = sparkService.get("user-1", "spark-1");
        assertThat(result).isPresent();
    }

    @Test
    void cancel_setsStatusCancelled() {
        Spark spark = Spark.create("user-1", "test");
        when(sparkRepository.findByIdAndUserId(spark.getId(), "user-1")).thenReturn(Optional.of(spark));
        when(sparkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        sparkService.cancel(spark.getId(), "user-1");
        assertThat(spark.getStatus()).isEqualTo(SparkStatus.CANCELLED);
    }

    @Test
    void list_returnsPaginatedSparks() {
        Spark spark = Spark.create("user-1", "test");
        when(sparkRepository.findByUserIdOrderByCreatedAtDesc(eq("user-1"), any()))
                .thenReturn(new PageImpl<>(List.of(spark), PageRequest.of(0, 20), 1));
        var page = sparkService.list("user-1", 0, 20);
        assertThat(page.getContent()).hasSize(1);
    }
}
