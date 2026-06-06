package io.tacticl.business.pipeline.ingress;

import io.tacticl.business.pipeline.ingress.IngressRegistryProperties.EntryPointDef;
import io.tacticl.data.pipeline.entity.EntryPoint;
import io.tacticl.data.pipeline.repository.EntryPointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntryPointSeederTest {

    private EntryPointRepository repo;
    private IngressRegistryProperties properties;
    private EntryPointSeeder seeder;

    @BeforeEach
    void setUp() {
        repo = mock(EntryPointRepository.class);
        properties = new IngressRegistryProperties();
        seeder = new EntryPointSeeder(repo, properties);
    }

    @Test
    void emptyConfig_seedsNothing() {
        seeder.run(null);
        verify(repo, never()).save(any());
    }

    @Test
    void newRow_insertsAlertEntryPointWithBugFixPlaybook() {
        properties.setEntryPoints(List.of(alertDef()));
        when(repo.findByChannelAndExternalKeyAndIsActiveTrue("DISCORD", "guild-1:sev2"))
            .thenReturn(Optional.empty());

        seeder.run(null);

        ArgumentCaptor<EntryPoint> saved = ArgumentCaptor.forClass(EntryPoint.class);
        verify(repo).save(saved.capture());
        EntryPoint ep = saved.getValue();
        assertThat(ep.getChannel()).isEqualTo("DISCORD");
        assertThat(ep.getExternalKey()).isEqualTo("guild-1:sev2");
        assertThat(ep.getDefaultPlaybook()).isEqualTo("BUG_FIX");
        assertThat(ep.getRepoUrl()).isEqualTo("https://github.com/tacticl-ai/app.git");
        assertThat(ep.isAdmin("user-42")).isTrue();
        assertThat(ep.isActive()).isTrue();
    }

    @Test
    void existingRow_refreshedInPlaceNotDuplicated() {
        EntryPoint existing = EntryPoint.create("DISCORD", "guild-1:sev2", "tacticl",
            "https://old", "FULL_PDLC", "tacticl-{userId}", java.util.Set.of("old-admin"),
            5.0, null, false);
        when(repo.findByChannelAndExternalKeyAndIsActiveTrue("DISCORD", "guild-1:sev2"))
            .thenReturn(Optional.of(existing));

        properties.setEntryPoints(List.of(alertDef()));
        seeder.run(null);

        ArgumentCaptor<EntryPoint> saved = ArgumentCaptor.forClass(EntryPoint.class);
        verify(repo).save(saved.capture());
        // Same row id (update), config wins on mutable fields.
        assertThat(saved.getValue().getId()).isEqualTo(existing.getId());
        assertThat(saved.getValue().getDefaultPlaybook()).isEqualTo("BUG_FIX");
        assertThat(saved.getValue().getRepoUrl()).isEqualTo("https://github.com/tacticl-ai/app.git");
        assertThat(saved.getValue().isAdmin("user-42")).isTrue();
        assertThat(saved.getValue().isAdmin("old-admin")).isFalse();
    }

    @Test
    void unknownChannelOrBlankKey_skipped() {
        EntryPointDef badChannel = alertDef();
        badChannel.setChannel("SLACK");          // not a ChannelType
        EntryPointDef blankKey = alertDef();
        blankKey.setExternalKey("  ");
        properties.setEntryPoints(List.of(badChannel, blankKey));

        seeder.run(null);

        verify(repo, never()).findByChannelAndExternalKeyAndIsActiveTrue(anyString(), eq("guild-1:sev2"));
        verify(repo, never()).save(any());
    }

    private static EntryPointDef alertDef() {
        EntryPointDef def = new EntryPointDef();
        def.setChannel("DISCORD");
        def.setExternalKey("guild-1:sev2");
        def.setProductId("tacticl");
        def.setRepoUrl("https://github.com/tacticl-ai/app.git");
        def.setPlaybook("BUG_FIX");
        def.setAdminUserIds(List.of("user-42"));
        def.setCostCeilingUsd(10.0);
        return def;
    }
}
