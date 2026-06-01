package io.tacticl.business.pipeline.ingress;

import io.cidadel.framework.exception.CidadelException;
import io.tacticl.data.pipeline.entity.EntryPoint;
import io.tacticl.data.pipeline.repository.EntryPointRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EntryPointResolverTest {

    @Mock EntryPointRepository repository;

    private EntryPoint ep(String externalKey, boolean isDefault) {
        return EntryPoint.create("DISCORD", externalKey, "tacticl", "https://repo", "FULL_PDLC",
                                 "tacticl-{userId}", Set.of("admin-1"), 5.0, "ref", isDefault);
    }

    @Test
    void resolve_narrowestKeyMatches_returnsNarrowestEntryPoint() {
        EntryPoint narrow = ep("guild:chan", false);
        when(repository.findByChannelAndExternalKeyAndIsActiveTrue("DISCORD", "guild:chan"))
            .thenReturn(Optional.of(narrow));

        EntryPoint result = resolver().resolve(ChannelType.DISCORD, List.of("guild:chan", "guild"));

        assertThat(result).isSameAs(narrow);
        verify(repository, never())
            .findByChannelAndExternalKeyAndIsActiveTrue("DISCORD", "guild");
    }

    @Test
    void resolve_narrowMisses_fallsBackToWiderKey() {
        when(repository.findByChannelAndExternalKeyAndIsActiveTrue("DISCORD", "guild:chan"))
            .thenReturn(Optional.empty());
        EntryPoint wide = ep("guild", false);
        when(repository.findByChannelAndExternalKeyAndIsActiveTrue("DISCORD", "guild"))
            .thenReturn(Optional.of(wide));

        EntryPoint result = resolver().resolve(ChannelType.DISCORD, List.of("guild:chan", "guild"));

        assertThat(result).isSameAs(wide);
    }

    @Test
    void resolve_allKeysMiss_fallsBackToChannelDefault() {
        when(repository.findByChannelAndExternalKeyAndIsActiveTrue(eq("DISCORD"), anyString()))
            .thenReturn(Optional.empty());
        EntryPoint dflt = ep("__default__", true);
        when(repository.findFirstByChannelAndIsDefaultForChannelTrueAndIsActiveTrue("DISCORD"))
            .thenReturn(Optional.of(dflt));

        EntryPoint result = resolver().resolve(ChannelType.DISCORD, List.of("guild:chan", "guild"));

        assertThat(result).isSameAs(dflt);
    }

    @Test
    void resolve_noMatchAndNoDefault_throwsEntryPointNotFound() {
        when(repository.findByChannelAndExternalKeyAndIsActiveTrue(eq("DISCORD"), anyString()))
            .thenReturn(Optional.empty());
        when(repository.findFirstByChannelAndIsDefaultForChannelTrueAndIsActiveTrue("DISCORD"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver().resolve(ChannelType.DISCORD, List.of("guild:chan")))
            .isInstanceOf(CidadelException.class)
            .satisfies(e -> assertThat(((CidadelException) e).getErrorDetails())
                .isEqualTo(IngressErrorDetails.ENTRY_POINT_NOT_FOUND));
    }

    @Test
    void resolve_blankCandidateKeys_areSkipped() {
        EntryPoint dflt = ep("__default__", true);
        when(repository.findFirstByChannelAndIsDefaultForChannelTrueAndIsActiveTrue("WEB"))
            .thenReturn(Optional.of(dflt));

        EntryPoint result = resolver().resolve(ChannelType.WEB, List.of("", "  "));

        assertThat(result).isSameAs(dflt);
        verify(repository, never())
            .findByChannelAndExternalKeyAndIsActiveTrue(eq("WEB"), anyString());
    }

    @Test
    void resolveFromOrigin_widensGuildChannelKeyToGuild() {
        // D1 regression: the LIVE Discord path calls resolve(RunOrigin) with externalKey
        // "guild:chan" and MUST widen to the guild-only seeded row (not just probe the full key).
        when(repository.findByChannelAndExternalKeyAndIsActiveTrue("DISCORD", "guild:chan"))
            .thenReturn(Optional.empty());
        EntryPoint guildRow = ep("guild", false);
        when(repository.findByChannelAndExternalKeyAndIsActiveTrue("DISCORD", "guild"))
            .thenReturn(Optional.of(guildRow));

        EntryPoint result = resolver().resolve(
            new RunOrigin(ChannelType.DISCORD, "guild:chan", "chan", "msg-1"));

        assertThat(result).isSameAs(guildRow);
    }

    private EntryPointResolver resolver() {
        return new EntryPointResolver(repository);
    }
}
