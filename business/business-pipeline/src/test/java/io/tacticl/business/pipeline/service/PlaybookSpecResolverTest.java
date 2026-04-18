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
    void resolve_allKnownPlaybooks_noException() {
        for (String name : new String[]{"FULL_PDLC","BUG_FIX","SMALL_FEATURE",
                "REFACTOR","INFRA_CHANGE","DOCS_ONLY","UI_CHANGE","SECURITY_PATCH"}) {
            assertThat(resolver.resolve(name)).isNotBlank();
        }
    }
}
