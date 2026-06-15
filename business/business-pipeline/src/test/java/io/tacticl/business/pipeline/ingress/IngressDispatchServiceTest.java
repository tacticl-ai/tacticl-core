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
    @Mock ObjectProvider<io.strategiz.social.client.github.config.GitHubConfig> gitHubConfigProvider;
    @Mock ObjectProvider<io.tacticl.business.profile.service.UserRepoService> userRepoServiceProvider;

    IngressDispatchService service;

    private static final String ADMIN = "user-admin";
    private static final String OUTSIDER = "user-outsider";

    @BeforeEach
    void setUp() {
        service = new IngressDispatchService(entryPointResolver, sparkService,
            sparkClassifierService, pdlcV2Service, conversationProvider, materializerProvider,
            gitHubConfigProvider, userRepoServiceProvider);
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
    void dispatch_explicitTrigger_usesResolvedPatAndRequestRepoUrl_overridingEntryPoint() {
        // A resolved Tacticl PAT + a caller-supplied repoUrl (the arbiter-provisioned repo)
        // must OVERRIDE the EntryPoint's repoUrl + (unresolved) github token ref.
        var gh = mock(io.strategiz.social.client.github.config.GitHubConfig.class);
        when(gh.getAppToken()).thenReturn("ghp_resolved_pat");
        when(gitHubConfigProvider.getIfAvailable()).thenReturn(gh);
        when(entryPointResolver.resolve(any(RunOrigin.class))).thenReturn(entryPoint());
        Spark spark = Spark.create(ADMIN, "Add login");
        when(sparkService.create(eq(ADMIN), eq("Add login"), eq(SparkInitiatorSource.DISCORD),
            eq(ADMIN), isNull())).thenReturn(spark);
        when(sparkClassifierService.classify("Add login")).thenReturn(SparkType.CODE);
        PipelineRun run = PipelineRun.create(ADMIN, spark.getId(), "Add login", "https://repo",
            "FULL_PDLC", List.of(), 7.5);
        when(pdlcV2Service.submitPipeline(anyString(), anyString(), anyString(), anyString(),
            anyString(), anyString(), anyList(), anyString(), anyDouble())).thenReturn(run);

        IngressRequest req = new IngressRequest(discordOrigin(), ADMIN, IngressKind.EXPLICIT_TRIGGER,
            "Add login", List.of(), "tacticl", "interaction-1", null,
            "https://github.com/tacticl-ai/login.git");
        service.dispatch(req);

        // repoUrl from the request, token = the resolved PAT (NOT the EntryPoint's ref).
        verify(pdlcV2Service).submitPipeline(eq("tacticl"), eq(ADMIN), eq(spark.getId()),
            eq("Add login"), eq("https://github.com/tacticl-ai/login.git"), eq("FULL_PDLC"),
            eq(List.of()), eq("ghp_resolved_pat"), eq(7.5));
    }

    @Test
    void dispatch_explicitTrigger_registersRepoToUserMemory() {
        var repoMemory = mock(io.tacticl.business.profile.service.UserRepoService.class);
        when(userRepoServiceProvider.getIfAvailable()).thenReturn(repoMemory);
        when(entryPointResolver.resolve(any(RunOrigin.class))).thenReturn(entryPoint());
        Spark spark = Spark.create(ADMIN, "Add login");
        when(sparkService.create(eq(ADMIN), eq("Add login"), eq(SparkInitiatorSource.DISCORD),
            eq(ADMIN), isNull())).thenReturn(spark);
        when(sparkClassifierService.classify("Add login")).thenReturn(SparkType.CODE);
        PipelineRun run = PipelineRun.create(ADMIN, spark.getId(), "Add login", "https://repo",
            "FULL_PDLC", List.of(), 7.5);
        when(pdlcV2Service.submitPipeline(anyString(), anyString(), anyString(), anyString(),
            anyString(), anyString(), anyList(), anyString(), anyDouble())).thenReturn(run);

        // Caller-supplied repoUrl (arbiter-provisioned) → registered as CREATED.
        IngressRequest req = new IngressRequest(discordOrigin(), ADMIN, IngressKind.EXPLICIT_TRIGGER,
            "Add login", List.of(), "tacticl", "interaction-1", null,
            "https://github.com/tacticl-ai/login.git");
        service.dispatch(req);

        verify(repoMemory).registerRepoUse(ADMIN, "https://github.com/tacticl-ai/login.git",
            io.tacticl.data.profile.entity.RepoSource.CREATED);
    }

    @Test
    void dispatch_explicitTrigger_repoMemoryFailureDoesNotBlockSubmit() {
        var repoMemory = mock(io.tacticl.business.profile.service.UserRepoService.class);
        when(userRepoServiceProvider.getIfAvailable()).thenReturn(repoMemory);
        doThrow(new RuntimeException("mongo down")).when(repoMemory)
            .registerRepoUse(anyString(), anyString(), any());
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

        // Registry blew up but the pipeline still submitted.
        assertThat(result).contains(run);
        verify(pdlcV2Service).submitPipeline(anyString(), anyString(), anyString(), anyString(),
            anyString(), anyString(), anyList(), anyString(), anyDouble());
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
    void dispatch_conversationTurn_discordAdmin_delegatesWithCanDispatchTrue() {
        // A Discord conversation turn is plain chat (no EntryPoint REQUIRED — the turn proceeds even
        // if none resolves), but the EntryPoint IS consulted best-effort to learn whether THIS caller
        // may also authorize a build. An admin ⇒ canDispatch=true.
        when(entryPointResolver.resolve(any(RunOrigin.class))).thenReturn(entryPoint());
        ConversationTurnHandler handler = mock(ConversationTurnHandler.class);
        when(conversationProvider.getIfAvailable()).thenReturn(handler);
        var origin = discordOrigin();
        var req = new IngressRequest(origin, ADMIN, IngressKind.CONVERSATION_TURN,
            "hello there", List.of(), "tacticl", "interaction-5", null);

        Optional<PipelineRun> result = service.dispatch(req);

        assertThat(result).isEmpty();
        verify(handler).handleTurn(ADMIN, origin, "hello there", true);
        verifyNoInteractions(pdlcV2Service);
    }

    @Test
    void dispatch_conversationTurn_discordNonAdmin_delegatesWithCanDispatchFalse() {
        // A non-admin on a shared channel may CONVERSE but never DISPATCH — fail-closed to false.
        when(entryPointResolver.resolve(any(RunOrigin.class))).thenReturn(entryPoint());
        ConversationTurnHandler handler = mock(ConversationTurnHandler.class);
        when(conversationProvider.getIfAvailable()).thenReturn(handler);
        var origin = discordOrigin();
        var req = new IngressRequest(origin, OUTSIDER, IngressKind.CONVERSATION_TURN,
            "hello there", List.of(), "tacticl", "interaction-5b", null);

        service.dispatch(req);

        verify(handler).handleTurn(OUTSIDER, origin, "hello there", false);
    }

    @Test
    void dispatch_conversationTurn_discordNoEntryPoint_failsClosedToFalse() {
        // No governing EntryPoint resolves ⇒ no admin set exists ⇒ no dispatch (but the chat still flows).
        when(entryPointResolver.resolve(any(RunOrigin.class)))
            .thenThrow(new CidadelException(IngressErrorDetails.ENTRY_POINT_NOT_FOUND, "business-pipeline", "x"));
        ConversationTurnHandler handler = mock(ConversationTurnHandler.class);
        when(conversationProvider.getIfAvailable()).thenReturn(handler);
        var origin = discordOrigin();
        var req = new IngressRequest(origin, ADMIN, IngressKind.CONVERSATION_TURN,
            "hello there", List.of(), "tacticl", "interaction-5c", null);

        service.dispatch(req);

        verify(handler).handleTurn(ADMIN, origin, "hello there", false);
    }

    @Test
    void dispatch_conversationTurn_voiceOwner_canDispatchTrue_withoutResolvingEntryPoint() {
        // Owner channels (WEB/VOICE) act on the caller's own authenticated session ⇒ canDispatch=true,
        // and the EntryPoint registry is NOT consulted at all.
        ConversationTurnHandler handler = mock(ConversationTurnHandler.class);
        when(conversationProvider.getIfAvailable()).thenReturn(handler);
        var origin = new RunOrigin(ChannelType.VOICE, "sess-9", "sess-9", null);
        var req = new IngressRequest(origin, ADMIN, IngressKind.CONVERSATION_TURN,
            "build me a thing", List.of(), "tacticl", "interaction-5d", null);

        service.dispatch(req);

        verify(handler).handleTurn(ADMIN, origin, "build me a thing", true);
        verifyNoInteractions(entryPointResolver);
        verifyNoInteractions(pdlcV2Service);
    }

    @Test
    void dispatch_conversationTurn_unlinkedIdentity_throwsUnlinked() {
        var req = new IngressRequest(discordOrigin(), null, IngressKind.CONVERSATION_TURN,
            "hello", List.of(), "tacticl", "interaction-6", null);

        assertThatThrownBy(() -> service.dispatch(req))
            .isInstanceOf(CidadelException.class)
            .satisfies(e -> assertThat(((CidadelException) e).getErrorDetails())
                .isEqualTo(IngressErrorDetails.UNLINKED_IDENTITY));

        // Link is enforced before any handler/registry lookup.
        verifyNoInteractions(entryPointResolver);
    }

    @Test
    void dispatch_conversationTurn_noHandlerWired_dropsSilently() {
        when(conversationProvider.getIfAvailable()).thenReturn(null);
        var req = new IngressRequest(discordOrigin(), ADMIN, IngressKind.CONVERSATION_TURN,
            "hello", List.of(), "tacticl", "interaction-7", null);

        Optional<PipelineRun> result = service.dispatch(req);

        assertThat(result).isEmpty();
        verifyNoInteractions(entryPointResolver);
        verifyNoInteractions(pdlcV2Service);
    }
}
