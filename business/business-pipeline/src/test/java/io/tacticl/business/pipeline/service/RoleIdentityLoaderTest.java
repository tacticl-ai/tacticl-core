package io.tacticl.business.pipeline.service;

import io.tacticl.data.pipeline.entity.PdlcRole;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RoleIdentityLoaderTest {

    private final RoleIdentityLoader loader = new RoleIdentityLoader();

    @Test
    void loadAll_returnsAllTwelveRoles() {
        Map<String, String> all = loader.loadAll();
        assertThat(all).hasSize(12);
        // PM → PO rename (Wave 2 cloud-agent-orchestrator migration; PdlcRole.PO).
        assertThat(all).containsKeys("implementer", "reviewer", "po", "researcher",
            "planner", "architect", "designer", "tester",
            "security_analyst", "technical_writer", "devops", "retro_analyst");
    }

    @Test
    void loadAll_implementerContainsKeyContent() {
        Map<String, String> all = loader.loadAll();
        assertThat(all.get("implementer"))
            .contains("# IMPLEMENTER")
            .contains("report.sh");
    }

    @Test
    void loadIdentity_returnsNonEmpty() {
        for (PdlcRole role : PdlcRole.values()) {
            String identity = loader.loadIdentity(role);
            assertThat(identity).isNotBlank()
                .as("Identity for %s should not be blank", role);
        }
    }
}
