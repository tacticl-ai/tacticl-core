package io.tacticl.business.discord;

import io.tacticl.business.discord.identity.DiscordIdentityResolver;
import io.tacticl.business.pipeline.ingress.IngressDispatchService;
import io.tacticl.business.pipeline.ingress.IngressRequest;
import io.tacticl.client.discord.DiscordRestClient;
import io.tacticl.client.discord.config.DiscordConfig;
import io.tacticl.data.discord.entity.DiscordRunBinding;
import io.tacticl.data.discord.repository.DiscordRunBindingRepository;
import io.tacticl.data.pipeline.entity.PipelineRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiscordInteractionDispatcherTest {

    private DiscordIdentityResolver identityResolver;
    private IngressDispatchService ingressDispatchService;
    private DiscordRunBindingRepository bindingRepo;
    private DiscordRestClient discord;
    private DiscordUserLinker linker;
    private DiscordConfig config;
    private DiscordInteractionDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        identityResolver = mock(DiscordIdentityResolver.class);
        ingressDispatchService = mock(IngressDispatchService.class);
        bindingRepo = mock(DiscordRunBindingRepository.class);
        discord = mock(DiscordRestClient.class);
        linker = mock(DiscordUserLinker.class);
        config = mock(DiscordConfig.class);
        when(config.getLinkTokenTtlMinutes()).thenReturn(15);
        dispatcher = new DiscordInteractionDispatcher(
            identityResolver, new DiscordInboundAdapter(), ingressDispatchService, bindingRepo, discord,
            linker, config);
    }

    @Test
    void dispatchAsync_unlinkedUser_doesNotDispatchAndPromptsLink() {
        when(identityResolver.resolve("snow-1")).thenReturn(Optional.empty());

        dispatcher.dispatchAsync(slashInteraction("snow-1"), "token-1");

        verify(ingressDispatchService, never()).dispatch(any());
        // The unlinked user gets a private "link your account" followup, never a dispatch.
        verify(discord).createFollowupMessage(eq("token-1"), any());
    }

    @Test
    void dispatchAsync_linkedTrigger_dispatchesAndPersistsRunBinding() {
        when(identityResolver.resolve("snow-1")).thenReturn(Optional.of("user-42"));
        PipelineRun run = mock(PipelineRun.class);
        when(run.getId()).thenReturn("run-99");
        when(ingressDispatchService.dispatch(any(IngressRequest.class))).thenReturn(Optional.of(run));

        dispatcher.dispatchAsync(slashInteraction("snow-1"), "token-1");

        ArgumentCaptor<IngressRequest> reqCaptor = ArgumentCaptor.forClass(IngressRequest.class);
        verify(ingressDispatchService).dispatch(reqCaptor.capture());
        assertThat(reqCaptor.getValue().tacticlUserId()).isEqualTo("user-42");

        ArgumentCaptor<DiscordRunBinding> bindingCaptor = ArgumentCaptor.forClass(DiscordRunBinding.class);
        verify(bindingRepo).save(bindingCaptor.capture());
        assertThat(bindingCaptor.getValue().getPipelineRunId()).isEqualTo("run-99");
        assertThat(bindingCaptor.getValue().getChannelId()).isEqualTo("chan-7");
    }

    @Test
    void dispatchAsync_linkCommand_issuesTokenAndNeverDispatches() {
        // /link runs before the identity gate; the snowflake need not be linked yet.
        when(linker.beginLink(eq("snow-1"), any())).thenReturn("TOKEN-123");

        dispatcher.dispatchAsync(linkInteraction("snow-1", "cooluser"), "token-1");

        verify(linker).beginLink(eq("snow-1"), eq("cooluser"));
        verify(identityResolver, never()).resolve(anyString());
        verify(ingressDispatchService, never()).dispatch(any());

        ArgumentCaptor<Map<String, Object>> msg = ArgumentCaptor.forClass(Map.class);
        verify(discord).createFollowupMessage(eq("token-1"), msg.capture());
        assertThat(String.valueOf(msg.getValue().get("content"))).contains("TOKEN-123");
    }

    @Test
    void dispatchAsync_malformedInteraction_doesNotThrowAndWarnsUser() {
        when(identityResolver.resolve(anyString())).thenReturn(Optional.of("user-42"));
        Map<String, Object> bad = Map.of(
            "id", "int-bad", "type", 2, "token", "token-1",
            "member", Map.of("user", Map.of("id", "snow-1")),
            "data", Map.of("type", 1, "name", "pdlc", "options", List.of()) // missing prompt → throws
        );

        // Must not propagate — the async path swallows and warns the user.
        dispatcher.dispatchAsync(bad, "token-1");

        verify(ingressDispatchService, never()).dispatch(any());
        verify(discord).createFollowupMessage(eq("token-1"), any());
    }

    private static Map<String, Object> slashInteraction(String discordUserId) {
        return Map.of(
            "id", "int-1",
            "type", 2,
            "token", "token-1",
            "guild_id", "guild-9",
            "channel", Map.of("id", "chan-7"),
            "member", Map.of("user", Map.of("id", discordUserId)),
            "data", Map.of(
                "type", 1, "name", "pdlc",
                "options", List.of(Map.of("name", "prompt", "value", "ship it"))
            )
        );
    }

    private static Map<String, Object> linkInteraction(String discordUserId, String username) {
        return Map.of(
            "id", "int-link",
            "type", 2,
            "token", "token-1",
            "guild_id", "guild-9",
            "channel", Map.of("id", "chan-7"),
            "member", Map.of("user", Map.of("id", discordUserId, "username", username)),
            "data", Map.of("type", 1, "name", "link")
        );
    }
}
