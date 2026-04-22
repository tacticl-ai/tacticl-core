package io.tacticl.data.sparks.entity;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SparkTest {

    @Test
    void create_setsInitialState() {
        Spark spark = Spark.create("user-1", "build me a REST API");
        assertThat(spark.getId()).isNotBlank();
        assertThat(spark.getUserId()).isEqualTo("user-1");
        assertThat(spark.getInput()).isEqualTo("build me a REST API");
        assertThat(spark.getStatus()).isEqualTo(SparkStatus.PENDING);
        assertThat(spark.getCreatedAt()).isNotNull();
        assertThat(spark.getType()).isNull();
        assertThat(spark.getRoute()).isNull();
    }

    @Test
    void classify_setsTypeAndTransitionsToRouting() {
        Spark spark = Spark.create("user-1", "build me a REST API");
        spark.classify(SparkType.CODE);
        assertThat(spark.getType()).isEqualTo(SparkType.CODE);
        assertThat(spark.getStatus()).isEqualTo(SparkStatus.ROUTING);
    }

    @Test
    void markExecuting_setsRouteAndTimestamp() {
        Spark spark = Spark.create("user-1", "build me a REST API");
        spark.classify(SparkType.CODE);
        spark.markExecuting(SparkRoute.CLOUD, null);
        assertThat(spark.getStatus()).isEqualTo(SparkStatus.EXECUTING);
        assertThat(spark.getRoute()).isEqualTo(SparkRoute.CLOUD);
        assertThat(spark.getStartedAt()).isNotNull();
    }

    @Test
    void markCompleted_setsCompletedStatus() {
        Spark spark = Spark.create("user-1", "test");
        spark.classify(SparkType.RESEARCH);
        spark.markExecuting(SparkRoute.CLOUD, null);
        spark.markCompleted(500, "claude-haiku-4-5");
        assertThat(spark.getStatus()).isEqualTo(SparkStatus.COMPLETED);
        assertThat(spark.getTokenCost()).isEqualTo(500);
        assertThat(spark.getModelUsed()).isEqualTo("claude-haiku-4-5");
        assertThat(spark.getCompletedAt()).isNotNull();
    }

    @Test
    void cancel_setsStatusToCancelled() {
        Spark spark = Spark.create("user-1", "test");
        spark.cancel();
        assertThat(spark.getStatus()).isEqualTo(SparkStatus.CANCELLED);
    }

    @Test
    void projectId_defaultsToNullAndIsMutable() {
        Spark spark = Spark.create("user-1", "test");
        assertThat(spark.getProjectId()).isNull();
        spark.setProjectId("project-42");
        assertThat(spark.getProjectId()).isEqualTo("project-42");
    }
}
