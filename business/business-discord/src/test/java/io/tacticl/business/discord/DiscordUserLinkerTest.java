package io.tacticl.business.discord;

import io.tacticl.client.discord.config.DiscordConfig;
import io.tacticl.data.discord.entity.DiscordAccountLink;
import io.tacticl.data.discord.repository.DiscordAccountLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiscordUserLinkerTest {

    private DiscordAccountLinkRepository linkRepo;
    private DiscordConfig config;
    private DiscordUserLinker linker;

    @BeforeEach
    void setUp() {
        linkRepo = mock(DiscordAccountLinkRepository.class);
        config = mock(DiscordConfig.class);
        when(config.getLinkTokenTtlMinutes()).thenReturn(15);
        when(linkRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        linker = new DiscordUserLinker(linkRepo, config);
    }

    @Test
    void beginLink_newSnowflake_createsPendingRowWithToken() {
        when(linkRepo.findByDiscordUserId("snow-1")).thenReturn(Optional.empty());

        String token = linker.beginLink("snow-1", "cooluser");

        assertThat(token).isNotBlank();
        ArgumentCaptor<DiscordAccountLink> saved = ArgumentCaptor.forClass(DiscordAccountLink.class);
        verify(linkRepo).save(saved.capture());
        DiscordAccountLink link = saved.getValue();
        assertThat(link.getDiscordUserId()).isEqualTo("snow-1");
        assertThat(link.getDiscordUsername()).isEqualTo("cooluser");
        assertThat(link.getLinkToken()).isEqualTo(token);
        assertThat(link.isActive()).isTrue();
        assertThat(link.isLinked()).isFalse();
    }

    @Test
    void beginLink_existingSnowflake_reissuesTokenOnSameRow() {
        DiscordAccountLink existing =
            DiscordAccountLink.pending("snow-1", "old", "old-token", 15);
        when(linkRepo.findByDiscordUserId("snow-1")).thenReturn(Optional.of(existing));

        String token = linker.beginLink("snow-1", "newname");

        // Updates the existing row (unique snowflake index forbids a second insert).
        ArgumentCaptor<DiscordAccountLink> saved = ArgumentCaptor.forClass(DiscordAccountLink.class);
        verify(linkRepo).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(existing);
        assertThat(saved.getValue().getLinkToken()).isEqualTo(token).isNotEqualTo("old-token");
        assertThat(saved.getValue().getDiscordUsername()).isEqualTo("newname");
        assertThat(saved.getValue().isActive()).isTrue();
    }

    @Test
    void redeemToken_validToken_bindsTacticlUser() {
        DiscordAccountLink pending = DiscordAccountLink.pending("snow-1", "u", "tok-abc", 15);
        when(linkRepo.findByLinkTokenAndIsActiveTrue("tok-abc")).thenReturn(Optional.of(pending));

        Optional<DiscordAccountLink> result = linker.redeemToken("tok-abc", "user-42");

        assertThat(result).isPresent();
        assertThat(result.get().getTacticlUserId()).isEqualTo("user-42");
        assertThat(result.get().isLinked()).isTrue();
        assertThat(result.get().getLinkToken()).isNull();  // cleared on redeem
        verify(linkRepo).save(pending);
    }

    @Test
    void redeemToken_expiredToken_returnsEmptyAndDoesNotBind() {
        DiscordAccountLink pending = DiscordAccountLink.pending("snow-1", "u", "tok-old", 15);
        pending.setLinkTokenExpiresAt(Instant.now().minusSeconds(60));  // force expiry
        when(linkRepo.findByLinkTokenAndIsActiveTrue("tok-old")).thenReturn(Optional.of(pending));

        Optional<DiscordAccountLink> result = linker.redeemToken("tok-old", "user-42");

        assertThat(result).isEmpty();
        verify(linkRepo, never()).save(any());
    }

    @Test
    void redeemToken_unknownToken_returnsEmpty() {
        when(linkRepo.findByLinkTokenAndIsActiveTrue("nope")).thenReturn(Optional.empty());

        assertThat(linker.redeemToken("nope", "user-42")).isEmpty();
        verify(linkRepo, never()).save(any());
    }

    @Test
    void redeemToken_blankArgs_returnsEmpty() {
        assertThat(linker.redeemToken(" ", "user-42")).isEmpty();
        assertThat(linker.redeemToken("tok", " ")).isEmpty();
        verify(linkRepo, never()).save(any());
    }

    @Test
    void unlink_activeLink_softDeletes() {
        DiscordAccountLink linked = DiscordAccountLink.linked("snow-1", "u", "user-42");
        when(linkRepo.findByTacticlUserIdAndIsActiveTrue("user-42")).thenReturn(Optional.of(linked));

        boolean removed = linker.unlink("user-42");

        assertThat(removed).isTrue();
        assertThat(linked.isActive()).isFalse();
        verify(linkRepo).save(linked);
    }

    @Test
    void unlink_noLink_returnsFalse() {
        when(linkRepo.findByTacticlUserIdAndIsActiveTrue("user-42")).thenReturn(Optional.empty());

        assertThat(linker.unlink("user-42")).isFalse();
        verify(linkRepo, never()).save(any());
    }
}
