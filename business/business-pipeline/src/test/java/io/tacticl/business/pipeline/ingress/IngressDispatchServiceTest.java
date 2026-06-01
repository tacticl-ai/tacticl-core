package io.tacticl.business.pipeline.ingress;

import io.cidadel.framework.exception.CidadelException;
import io.tacticl.business.pipeline.service.PdlcV2Service;
import io.tacticl.business.sparks.service.SparkClassifierService;
import io.tacticl.business.sparks.service.SparkService;
import io.tacticl.data.pipeline.entity.CheckpointDecision;
import io.tacticl.data.pipeline.entity.EntryPoint;
import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.data.sparks.entity.Spark;
import io.tacticl.data.sparks.entity.SparkInitiatorSource;
import io.tacticl.data.sparks.entity.SparkType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngressDispatchServiceTest {

    @Mock EntryPointResolver entryPointResolver;
    @Mock SparkService sparkService;
    @Mock SparkClassifierService sparkClassifierService;
    @Mock PdlcV2Service pdlcV2Service;
    @Mock ObjectProvider<ConversationTurnHandler> conversationProvider;
    @Mock ObjectProvider<AttachmentMaterializer> materializerProvider;

    IngressDispatchService service;

    private static final String ADMIN = "user-admin";
    private static final String OUTSIDER = "user-outsider";

    @BeforeEach
    void setUp() {
        service = new IngressDispatchService(entryPointResolver, sparkService,
            sparkClassifierService, pdlcV2Service, conversationProvider, materializerProvider);
    }

    private EntryPoint entryPoint() {
        return EntryPoint.create("DISCORD", "guild:chan", "tacticl",
            "https://github.com/tacticl/sandbox", "FULL_PDLC", "tacticl-{userId}",
            Set.of(ADMIN), 7.5, "secret/tacticl/github-token", false);
    }

    private RunOrigin discordOrigin() {
        return new RunOrigin(ChannelType.DISCORD, "guild:chan", "chan-123", null);
    }

    private IngressRequest trigger(String userId, String text, List<Attachment> attachments) {
        return new IngressRequest(discordOrigin(), userId, IngressKind.EXPLICIT_TRIGGER, text,
            attachments, "tacticl", "interaction-1", null);
    }

    @Test
    void dispatch_explicitTrigger_authorizedAdmin_submitsPipelineWithEntryPointProduct() {
        when(entryPointResolver.resolve(any(RunOrigin.class))).thenReturn(entryPoint());
        Spark spark = Spark.create(ADMIN, "Add login");
        when(sparkService.create(eq(ADMIN), eq("Add login"), eq(SparkInitiatorSource.DISCORD),
            eq(ADMIN), isNull())).thenReturn(spark);
        when(sparkClassifierService.classify("Add login")).thenReturn(SparkType.CODE);
        PipelineRun run = PipelineRun.create(ADMIN, spark.getId(), "Add login", "https://repo",
            "FULL_PDLC", List.of(), 7.5);
        when(pdlcV2Service.submitPipeline(anyString(), anyString(), anyString(), anyString(),
            anyString(), anyString(), anyList(), anyString(), anyDouble())).thenReturn(run);

        Optional<PipelineRun> result = service.dispatch(trigger(ADMIN, "Add login", List.of()));

        assertThat(result).contains(run);
        ArgumentCaptor<String> product = ArgumentCaptor.forClass(String.class);
        verify(pdlcV2Service).submitPipeline(product.capture(), eq(ADMIN), eq(spark.getId()),
            eq("Add login"), eq("https://github.com/tacticl/sandbox"), eq("FULL_PDLC"),
            eq(List.of()), eq("secret/tacticl/github-token"), eq(7.5));
        assertThat(product.getValue()).isEqualTo("tacticl");
        verify(sparkClassifierService).classify("Add login");
        verify(sparkService).classify(spark.getId(), ADMIN, SparkType.CODE);
    }

    @Test
    void dispatch_explicitTrigger_nonAdmin_throwsNotAuthorized_andNeverSubmits() {
        when(entryPointResolver.resolve(any(RunOrigin.class))).thenReturn(entryPoint());

        assertThatThrownBy(() -> service.dispatch(trigger(OUTSIDER, "Add login", List.of())))
            .isInstanceOf(CidadelException.class)
            .satisfies(e -> assertThat(((CidadelException) e).getErrorDetails())
                .isEqualTo(IngressErrorDetails.NOT_AUTHORIZED));

        verifyNoInteractions(pdlcV2Service);
        verifyNoInteractions(sparkService);
    }

    @Test
    void dispatch_explicitTrigger_unlinkedIdentity_throwsUnlinked_andNeverSubmits() {
        when(entryPointResolver.resolve(any(RunOrigin.class))).thenReturn(entryPoint());

        assertThatThrownBy(() -> service.dispatch(trigger(null, "Add login", List.of())))
            .isInstanceOf(CidadelException.class)
            .satisfies(e -> assertThat(((CidadelException) e).getErrorDetails())
                .isEqualTo(IngressErrorDetails.UNLINKED_IDENTITY));

        verifyNoInteractions(pdlcV2Service);
        verifyNoInteractions(sparkService);
    }

    @Test
    void dispatch_explicitTrigger_blankText_throwsInvalidRequest() {
        when(entryPointResolver.resolve(any(RunOrigin.class))).thenReturn(entryPoint());

        assertThatThrownBy(() -> service.dispatch(trigger(ADMIN, "   ", List.of())))
            .isInstanceOf(CidadelException.class)
            .satisfies(e -> assertThat(((CidadelException) e).getErrorDetails())
                .isEqualTo(IngressErrorDetails.INVALID_REQUEST));

        verifyNoInteractions(pdlcV2Service);
    }

    @Test
    void dispatch_explicitTrigger_withMaterializer_materializesAttachments() {
        when(entryPointResolver.resolve(any(RunOrigin.class))).thenReturn(entryPoint());
        AttachmentMaterializer materializer = mock(AttachmentMaterializer.class);
        when(materializerProvider.getIfAvailable()).thenReturn(materializer);
        when(materializer.materialize(anyList())).thenReturn(List.of("minio://b/k"));
        Spark spark = Spark.create(ADMIN, "Add login");
        when(sparkService.create(any(), any(), any(), any(), any())).thenReturn(spark);
        when(sparkClassifierService.classify(anyString())).thenReturn(SparkType.CODE);
        when(pdlcV2Service.submitPipeline(anyString(), anyString(), anyString(), anyString(),
            anyString(), anyString(), anyList(), anyString(), anyDouble()))
            .thenReturn(PipelineRun.create(ADMIN, spark.getId(), "x", "r", "FULL_PDLC", List.of(), 7.5));

        Attachment att = new Attachment("shot.png", "image/png", "https://cdn/shot.png", null, 10);
        service.dispatch(trigger(ADMIN, "Add login", List.of(att)));

        verify(materializer).materialize(List.of(att));
    }

    @Test
    void dispatch_checkpointDecision_authorizedAdmin_resolvesCheckpoint() {
        when(entryPointResolver.resolve(any(RunOrigin.class))).thenReturn(entryPoint());
        var decision = new CheckpointDecisionPayload("spark-9", "cp-9", CheckpointDecision.APPROVED, "lgtm");
        var req = new IngressRequest(discordOrigin(), ADMIN, IngressKind.CHECKPOINT_DECISION,
            null, List.of(), "tacticl", "interaction-2", decision);

        Optional<PipelineRun> result = service.dispatch(req);

        assertThat(result).isEmpty();
        verify(pdlcV2Service).resolveCheckpoint(ADMIN, "spark-9", "cp-9",
            CheckpointDecision.APPROVED, "lgtm");
    }

    @Test
    void dispatch_checkpointDecision_missingPayload_throwsInvalidRequest() {
        when(entryPointResolver.resolve(any(RunOrigin.class))).thenReturn(entryPoint());
        var req = new IngressRequest(discordOrigin(), ADMIN, IngressKind.CHECKPOINT_DECISION,
            null, List.of(), "tacticl", "interaction-3", null);

        assertThatThrownBy(() -> service.dispatch(req))
            .isInstanceOf(CidadelException.class)
            .satisfies(e -> assertThat(((CidadelException) e).getErrorDetails())
                .isEqualTo(IngressErrorDetails.INVALID_REQUEST));

        verify(pdlcV2Service, never()).resolveCheckpoint(any(), any(), any(), any(), any());
    }

    @Test
    void dispatch_cancelRun_authorizedAdmin_cancelsPipeline() {
        when(entryPointResolver.resolve(any(RunOrigin.class))).thenReturn(entryPoint());
        var decision = new CheckpointDecisionPayload("spark-7", null, null, null);
        var req = new IngressRequest(discordOrigin(), ADMIN, IngressKind.CANCEL_RUN,
            null, List.of(), "tacticl", "interaction-4", decision);

        Optional<PipelineRun> result = service.dispatch(req);

        assertThat(result).isEmpty();
        verify(pdlcV2Service).cancelPipeline(ADMIN, "spark-7");
    }

    @Test
    void dispatch_conversationTurn_withHandler_delegatesToHandler() {
        when(entryPointResolver.resolve(any(RunOrigin.class))).thenReturn(entryPoint());
        ConversationTurnHandler handler = mock(ConversationTurnHandler.class);
        when(conversationProvider.getIfAvailable()).thenReturn(handler);
        var origin = discordOrigin();
        var req = new IngressRequest(origin, ADMIN, IngressKind.CONVERSATION_TURN,
            "hello there", List.of(), "tacticl", "interaction-5", null);

        Optional<PipelineRun> result = service.dispatch(req);

        assertThat(result).isEmpty();
        verify(handler).handleTurn(ADMIN, origin, "hello there");
        verifyNoInteractions(pdlcV2Service);
    }

    @Test
    void dispatch_conversationTurn_unlinkedIdentity_throwsUnlinked() {
        when(entryPointResolver.resolve(any(RunOrigin.class))).thenReturn(entryPoint());
        var req = new IngressRequest(discordOrigin(), null, IngressKind.CONVERSATION_TURN,
            "hello", List.of(), "tacticl", "interaction-6", null);

        assertThatThrownBy(() -> service.dispatch(req))
            .isInstanceOf(CidadelException.class)
            .satisfies(e -> assertThat(((CidadelException) e).getErrorDetails())
                .isEqualTo(IngressErrorDetails.UNLINKED_IDENTITY));
    }

    @Test
    void dispatch_conversationTurn_noHandlerWired_dropsSilently() {
        when(entryPointResolver.resolve(any(RunOrigin.class))).thenReturn(entryPoint());
        when(conversationProvider.getIfAvailable()).thenReturn(null);
        var req = new IngressRequest(discordOrigin(), ADMIN, IngressKind.CONVERSATION_TURN,
            "hello", List.of(), "tacticl", "interaction-7", null);

        Optional<PipelineRun> result = service.dispatch(req);

        assertThat(result).isEmpty();
        verifyNoInteractions(pdlcV2Service);
    }
}
