package io.tacticl.business.discord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.cidadel.framework.exception.CidadelException;
import io.tacticl.business.pipeline.ingress.ChannelType;
import io.tacticl.business.pipeline.ingress.EntryPointResolver;
import io.tacticl.business.pipeline.ingress.IngressDispatchService;
import io.tacticl.business.pipeline.ingress.IngressErrorDetails;
import io.tacticl.business.pipeline.ingress.IngressKind;
import io.tacticl.business.pipeline.ingress.IngressRequest;
import io.tacticl.business.pipeline.ingress.RunOrigin;
import io.tacticl.client.arbiter.conversation.ConverseEventListener;
import io.tacticl.client.arbiter.conversation.ConverseTurnInput;
import io.tacticl.client.arbiter.conversation.ConversationServiceClient;
import io.tacticl.client.discord.DiscordRestClient;
import io.tacticl.data.discord.entity.DiscordRunBinding;
import io.tacticl.data.discord.repository.DiscordRunBindingRepository;
import io.tacticl.data.pipeline.entity.EntryPoint;
import io.tacticl.data.pipeline.entity.PipelineRun;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class DiscordConversationTurnHandlerTest {

    private ConversationServiceClient client;
    private ObjectProvider<ConversationServiceClient> clientProvider;
    private DiscordRestClient discord;
    private IngressDispatchService ingress;
    private DiscordRunBindingRepository bindingRepo;
    private EntryPointResolver resolver;
    private DiscordConversationTurnHandler handler;

    private static final RunOrigin ORIGIN = new RunOrigin(ChannelType.DISCORD, "g:c", "chan-1", "msg-1");
    private static final String USER = "user-1";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        client = mock(ConversationServiceClient.class);
        clientProvider = mock(ObjectProvider.class);
        when(clientProvider.getIfAvailable()).thenReturn(client);
        discord = mock(DiscordRestClient.class);
        ingress = mock(IngressDispatchService.class);
        bindingRepo = mock(DiscordRunBindingRepository.class);
        resolver = mock(EntryPointResolver.class);
        EntryPoint ep = mock(EntryPoint.class);
        when(ep.getProductId()).thenReturn("strategiz");
        when(resolver.resolve(any(RunOrigin.class))).thenReturn(ep);
        handler = new DiscordConversationTurnHandler(clientProvider, discord, ingress, bindingRepo, resolver);
    }

    @Test
    void supports_onlyDiscord() {
        assertThat(handler.supports(ChannelType.DISCORD)).isTrue();
        assertThat(handler.supports(ChannelType.VOICE)).isFalse();
    }

    @Test
    void handleTurn_buildsInputWithChannelSessionAndProduct_thenStreams() {
        handler.handleTurn(USER, ORIGIN, "build a health endpoint", true);

        ArgumentCaptor<ConverseTurnInput> in = ArgumentCaptor.forClass(ConverseTurnInput.class);
        verify(client).converseTurn(in.capture(), any());
        ConverseTurnInput input = in.getValue();
        assertThat(input.productId()).isEqualTo("strategiz");
        assertThat(input.sessionId()).isEqualTo("chan-1"); // shared per-channel conversation
        assertThat(input.userId()).isEqualTo(USER);
        assertThat(input.text()).isEqualTo("build a health endpoint");
        assertThat(input.canDispatch()).isTrue();
    }

    @Test
    void streamedReply_isPostedToChannelOnDone() {
        handler.handleTurn(USER, ORIGIN, "hi", true);
        ConverseEventListener sink = captureSink();

        sink.onToken("Hello ", "analyst");
        sink.onToken("there", "analyst");
        sink.onDone();

        verify(discord).createChannelMessage("chan-1", Map.of("content", "Hello there"));
    }

    @Test
    void startPipelineToolUse_dispatchesExplicitTrigger_bindsRun_andAcks() {
        PipelineRun run = mock(PipelineRun.class);
        when(run.getId()).thenReturn("run-9");
        when(ingress.dispatch(any(IngressRequest.class))).thenReturn(Optional.of(run));

        handler.handleTurn(USER, ORIGIN, "yes go ahead", true);
        ConverseEventListener sink = captureSink();
        sink.onToolUse("start_pipeline", "{\"sparkInput\":\"build a health endpoint\"}", true);
        sink.onDone();

        // The dispatch is offloaded off the gRPC callback thread — verify with a timeout.
        ArgumentCaptor<IngressRequest> req = ArgumentCaptor.forClass(IngressRequest.class);
        verify(ingress, timeout(2000)).dispatch(req.capture());
        assertThat(req.getValue().kind()).isEqualTo(IngressKind.EXPLICIT_TRIGGER);
        assertThat(req.getValue().text()).isEqualTo("build a health endpoint");
        verify(bindingRepo, timeout(2000)).save(any(DiscordRunBinding.class));
        verify(discord, timeout(2000)).createChannelMessage(eq("chan-1"), any());
    }

    @Test
    void noBrainClientWired_dropsWithoutPostingOrDispatching() {
        when(clientProvider.getIfAvailable()).thenReturn(null);

        handler.handleTurn(USER, ORIGIN, "hi", true);

        verifyNoInteractions(discord, ingress);
    }

    @Test
    void nonRegisteredChannel_isDropped() {
        when(resolver.resolve(any(RunOrigin.class)))
            .thenThrow(new CidadelException(IngressErrorDetails.ENTRY_POINT_NOT_FOUND, "x", "y"));

        handler.handleTurn(USER, ORIGIN, "hi", true);

        verify(client, never()).converseTurn(any(), any());
        verifyNoInteractions(discord, ingress);
    }

    @Test
    void chunk_splitsOverDiscordLimit() {
        String big = "x".repeat(4500);
        List<String> parts = DiscordConversationTurnHandler.chunk(big, 2000);
        assertThat(parts).hasSize(3);
        assertThat(parts.get(0)).hasSize(2000);
        assertThat(parts.get(2)).hasSize(500);
        assertThat(String.join("", parts)).isEqualTo(big);
        assertThat(DiscordConversationTurnHandler.chunk("short", 2000)).containsExactly("short");
    }

    private ConverseEventListener captureSink() {
        ArgumentCaptor<ConverseEventListener> sink = ArgumentCaptor.forClass(ConverseEventListener.class);
        verify(client).converseTurn(any(), sink.capture());
        return sink.getValue();
    }
}
