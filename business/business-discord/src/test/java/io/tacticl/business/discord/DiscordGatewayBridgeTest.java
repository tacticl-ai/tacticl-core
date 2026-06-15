package io.tacticl.business.discord;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.cidadel.framework.exception.CidadelException;
import io.tacticl.business.discord.identity.DiscordIdentityResolver;
import io.tacticl.business.pipeline.ingress.EntryPointResolver;
import io.tacticl.business.pipeline.ingress.IngressDispatchService;
import io.tacticl.business.pipeline.ingress.IngressErrorDetails;
import io.tacticl.business.pipeline.ingress.IngressRequest;
import io.tacticl.business.pipeline.ingress.RunOrigin;
import io.tacticl.client.discord.DiscordRestClient;
import io.tacticl.client.discord.dto.DiscordGatewayMessage;
import io.tacticl.client.discord.gateway.DiscordGatewayClient;
import io.tacticl.data.pipeline.entity.EntryPoint;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DiscordGatewayBridgeTest {

    private DiscordGatewayClient gatewayClient;
    private DiscordIdentityResolver identityResolver;
    private EntryPointResolver entryPointResolver;
    private IngressDispatchService ingress;
    private DiscordRestClient discord;
    private DiscordGatewayBridge bridge;

    @BeforeEach
    void setUp() {
        gatewayClient = mock(DiscordGatewayClient.class);
        identityResolver = mock(DiscordIdentityResolver.class);
        entryPointResolver = mock(EntryPointResolver.class);
        ingress = mock(IngressDispatchService.class);
        discord = mock(DiscordRestClient.class);
        bridge = new DiscordGatewayBridge(gatewayClient, identityResolver, entryPointResolver, ingress, discord);
    }

    private static DiscordGatewayMessage msg(String id, String authorId, boolean bot, String webhookId, String content) {
        return new DiscordGatewayMessage(id, "chan-1", "guild-1", authorId, "user", bot, webhookId, content);
    }

    @Test
    void botAuthored_isDropped() {
        bridge.onMessageCreate(msg("m1", "a1", true, null, "hi"));
        verifyNoInteractions(identityResolver, entryPointResolver, ingress);
    }

    @Test
    void webhookMessage_isDropped() {
        bridge.onMessageCreate(msg("m2", "a1", false, "wh-1", "hi"));
        verifyNoInteractions(identityResolver, entryPointResolver, ingress);
    }

    @Test
    void ownMessage_isDropped() {
        when(gatewayClient.getBotUserId()).thenReturn("bot-9");
        bridge.onMessageCreate(msg("m3", "bot-9", false, null, "hi"));
        verifyNoInteractions(identityResolver, entryPointResolver, ingress);
    }

    @Test
    void blankContent_isDropped() {
        bridge.onMessageCreate(msg("m4", "a1", false, null, "   "));
        verifyNoInteractions(entryPointResolver, ingress);
    }

    @Test
    void duplicateMessageId_isProcessedOnce() {
        EntryPoint ep = mock(EntryPoint.class);
        when(entryPointResolver.resolve(any(RunOrigin.class))).thenReturn(ep);
        when(identityResolver.resolve("a1")).thenReturn(Optional.of("u1"));

        DiscordGatewayMessage m = msg("dup", "a1", false, null, "hello");
        bridge.onMessageCreate(m);
        bridge.onMessageCreate(m); // redelivery on RESUME

        verify(ingress, timeout(2000)).dispatch(any(IngressRequest.class));
        // Second delivery was deduped: still exactly one dispatch after a settle window.
        verify(ingress, after(300).times(1)).dispatch(any(IngressRequest.class));
    }

    @Test
    void linkedUserInRegisteredChannel_dispatchesConversationTurn() {
        EntryPoint ep = mock(EntryPoint.class);
        when(entryPointResolver.resolve(any(RunOrigin.class))).thenReturn(ep);
        when(identityResolver.resolve("a1")).thenReturn(Optional.of("u1"));

        bridge.onMessageCreate(msg("m5", "a1", false, null, "build me a thing"));

        verify(ingress, timeout(2000)).dispatch(any(IngressRequest.class));
    }

    @Test
    void nonRegisteredChannel_neverDispatchesOrPrompts() {
        when(entryPointResolver.resolve(any(RunOrigin.class)))
            .thenThrow(new CidadelException(IngressErrorDetails.ENTRY_POINT_NOT_FOUND, "x", "y"));

        bridge.onMessageCreate(msg("m6", "a1", false, null, "hello"));

        // Give the worker time to run, then assert it stayed silent.
        verify(ingress, after(300).never()).dispatch(any(IngressRequest.class));
        verify(discord, never()).createChannelMessage(anyString(), any());
        verify(identityResolver, never()).resolve(anyString());
    }

    @Test
    void unlinkedUser_isPromptedOnce_andNotDispatched() {
        EntryPoint ep = mock(EntryPoint.class);
        when(entryPointResolver.resolve(any(RunOrigin.class))).thenReturn(ep);
        when(identityResolver.resolve("a1")).thenReturn(Optional.empty());

        bridge.onMessageCreate(msg("m7", "a1", false, null, "hello"));
        bridge.onMessageCreate(msg("m8", "a1", false, null, "still here?"));

        // Prompted at most once per author; never dispatched.
        verify(discord, timeout(2000)).createChannelMessage(anyString(), any());
        verify(discord, after(300).times(1)).createChannelMessage(anyString(), any());
        verify(ingress, never()).dispatch(any(IngressRequest.class));
    }
}
