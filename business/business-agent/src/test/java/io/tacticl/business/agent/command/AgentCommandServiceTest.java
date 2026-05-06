package io.tacticl.business.agent.command;

import io.cidadel.client.anthropic.AnthropicDirectClient;
import io.cidadel.client.base.llm.model.LlmResponse;
import io.tacticl.business.pipeline.router.PdlcRouter;
import io.tacticl.business.sparks.service.SparkClassifierService;
import io.tacticl.business.sparks.service.SparkService;
import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.data.sparks.entity.Spark;
import io.tacticl.data.sparks.entity.SparkInitiatorSource;
import io.tacticl.data.sparks.entity.SparkRoute;
import io.tacticl.data.sparks.entity.SparkType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentCommandServiceTest {

    private SparkService sparks;
    private SparkClassifierService classifier;
    private AnthropicDirectClient anthropic;
    private PdlcRouter pdlcRouter;
    private AgentCommandService service;

    @BeforeEach
    void setUp() {
        sparks = mock(SparkService.class);
        classifier = mock(SparkClassifierService.class);
        anthropic = mock(AnthropicDirectClient.class);
        pdlcRouter = mock(PdlcRouter.class);
        service = new AgentCommandService(sparks, classifier, anthropic, pdlcRouter);
    }

    @Test
    void routesCodeSparkToPdlcAndReturnsRunId() {
        Spark spark = Spark.create("u1", "ship it");
        String sparkId = spark.getId();
        when(sparks.create(eq("u1"), eq("ship it"), eq(SparkInitiatorSource.TELEGRAM_GROUP), eq("u1"), eq("p1")))
                .thenReturn(spark);
        when(classifier.classify("ship it")).thenReturn(SparkType.CODE);
        when(sparks.classify(sparkId, "u1", SparkType.CODE)).thenReturn(spark);
        when(sparks.markExecuting(eq(sparkId), eq("u1"), eq(SparkRoute.CLOUD), any())).thenReturn(spark);
        PipelineRun run = mock(PipelineRun.class);
        when(run.getId()).thenReturn("run-1");
        when(pdlcRouter.route(eq("u1"), eq(sparkId), eq("ship it"), eq("https://r"),
                eq(SparkType.CODE), eq(List.of()), any(), eq(50.0))).thenReturn(Optional.of(run));

        AgentCommand cmd = AgentCommand.fromTelegramGroup("u1", "ship it", "p1", "https://r");
        AgentCommandResult result = service.execute(cmd);

        assertThat(result.sparkId()).isEqualTo(sparkId);
        assertThat(result.pipelineRunId()).isEqualTo("run-1");
        assertThat(result.executionMode()).isEqualTo("ASYNC");
        assertThat(result.succeeded()).isTrue();
    }

    @Test
    void fallsBackToAnthropicForNonPipelineSparks() {
        Spark spark = Spark.create("u1", "haiku please");
        String sparkId = spark.getId();
        when(sparks.create(eq("u1"), eq("haiku please"), any(), eq("u1"), any())).thenReturn(spark);
        when(classifier.classify("haiku please")).thenReturn(SparkType.CREATIVE);
        when(sparks.classify(sparkId, "u1", SparkType.CREATIVE)).thenReturn(spark);
        when(sparks.markExecuting(eq(sparkId), eq("u1"), eq(SparkRoute.CLOUD), any())).thenReturn(spark);

        LlmResponse llm = mock(LlmResponse.class);
        when(llm.getContent()).thenReturn("the rain falls / softly on stones / spring returns");
        when(llm.getTotalTokens()).thenReturn(42);
        when(anthropic.generateContent(eq("claude-sonnet-4-6"), anyList(), anyString())).thenReturn(llm);

        AgentCommandResult result = service.execute(AgentCommand.fromHttp("u1", "haiku please", null));

        assertThat(result.responseText()).contains("rain falls");
        assertThat(result.tokensUsed()).isEqualTo(42);
        assertThat(result.executionMode()).isEqualTo("SYNC");
        verify(sparks).markCompleted(sparkId, "u1", 42, "claude-sonnet-4-6");
    }

    @Test
    void marksSparkFailedWhenAnthropicThrows() {
        Spark spark = Spark.create("u1", "hi");
        String sparkId = spark.getId();
        when(sparks.create(eq("u1"), eq("hi"), any(), eq("u1"), any())).thenReturn(spark);
        when(classifier.classify("hi")).thenReturn(SparkType.RESEARCH);
        when(sparks.classify(sparkId, "u1", SparkType.RESEARCH)).thenReturn(spark);
        when(sparks.markExecuting(eq(sparkId), eq("u1"), eq(SparkRoute.CLOUD), any())).thenReturn(spark);
        when(anthropic.generateContent(anyString(), anyList(), anyString()))
                .thenThrow(new RuntimeException("upstream 503"));

        AgentCommandResult result = service.execute(AgentCommand.fromHttp("u1", "hi", null));

        assertThat(result.succeeded()).isFalse();
        assertThat(result.sparkStatus()).isEqualTo("FAILED");
        verify(sparks).markFailed(sparkId, "u1");
    }

    @Test
    void honoursCallerCostCeilingWhenProvided() {
        Spark spark = Spark.create("u1", "deploy");
        when(sparks.create(any(), any(), any(), any(), any())).thenReturn(spark);
        when(classifier.classify(any())).thenReturn(SparkType.DEVOPS);
        when(sparks.classify(any(), any(), any())).thenReturn(spark);
        when(sparks.markExecuting(any(), any(), any(), any())).thenReturn(spark);
        PipelineRun run = mock(PipelineRun.class);
        when(run.getId()).thenReturn("run-x");
        when(pdlcRouter.route(any(), any(), any(), any(), any(), any(), any(), eq(12.5)))
                .thenReturn(Optional.of(run));

        AgentCommand cmd = new AgentCommand("u1", "deploy", null, 12.5,
                SparkInitiatorSource.TELEGRAM_GROUP, "p1", null);
        service.execute(cmd);

        verify(pdlcRouter).route(eq("u1"), any(), eq("deploy"), any(),
                eq(SparkType.DEVOPS), eq(List.of()), any(), eq(12.5));
    }
}
