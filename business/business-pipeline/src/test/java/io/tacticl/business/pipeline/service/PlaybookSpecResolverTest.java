package io.tacticl.business.pipeline.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlaybookSpecResolverTest {

    private final PlaybookSpecResolver resolver = new PlaybookSpecResolver();

    @Test
    void resolve_knownPlaybook_containsRoles() {
        String spec = resolver.resolve("BUG_FIX");
        assertThat(spec)
            .contains("BUG_FIX")
            .contains("IMPLEMENTER")
            .contains("REVIEWER");
    }

    @Test
    void resolve_unknownPlaybook_fallsBackToFullPdlc() {
        String spec = resolver.resolve("UNKNOWN");
        assertThat(spec).contains("PM").contains("RETRO_ANALYST");
    }

    @Test
    void resolve_pdlcFix_emitsMergeGateConfigNotLegacyRoles() {
        // The arbiter Temporal path reads a PdlcPlaybookConfig; the human PR gate engages
        // ONLY when mergeGateRole is present. Must NOT be the legacy {name, roles} shape.
        String spec = resolver.resolve("pdlc-fix");
        assertThat(spec)
            .contains("mergeGateRole").contains("test")
            .contains("reworkEntryRole").contains("implementer")
            .contains("reworkMax")
            .doesNotContain("\"roles\"")
            .doesNotContain("FULL_PDLC");
    }

    @Test
    void resolve_allKnownPlaybooks_noException() {
        for (String name : new String[]{"FULL_PDLC","BUG_FIX","SMALL_FEATURE",
                "REFACTOR","INFRA_CHANGE","DOCS_ONLY","UI_CHANGE","SECURITY_PATCH"}) {
            assertThat(resolver.resolve(name)).isNotBlank();
        }
    }
}
