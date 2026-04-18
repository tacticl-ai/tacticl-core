package io.tacticl.business.pipeline.service;

import io.tacticl.data.pipeline.entity.PdlcRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

@Service
public class RoleIdentityLoader {

    private static final Logger log = LoggerFactory.getLogger(RoleIdentityLoader.class);

    // Eager-loaded at construction to fail fast on missing resources
    private final Map<PdlcRole, String> identities;

    public RoleIdentityLoader() {
        this.identities = new EnumMap<>(PdlcRole.class);
        for (PdlcRole role : PdlcRole.values()) {
            String resourcePath = "role-identities/" + role.name().toLowerCase() + ".md";
            try {
                ClassPathResource resource = new ClassPathResource(resourcePath);
                if (resource.exists()) {
                    identities.put(role, resource.getContentAsString(StandardCharsets.UTF_8));
                } else {
                    log.warn("No identity file found for role {} at {}, using stub", role, resourcePath);
                    identities.put(role, defaultIdentity(role));
                }
            } catch (IOException e) {
                log.warn("Failed to load identity for role {}: {}", role, e.getMessage());
                identities.put(role, defaultIdentity(role));
            }
        }
    }

    /**
     * Returns a Map of role name → identity markdown for all 12 roles.
     * Suitable for direct use as the roleIdentities field in SubmitPipelineRequest.
     */
    public Map<String, String> loadAll() {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        identities.forEach((role, identity) -> result.put(role.name(), identity));
        return result;
    }

    /**
     * Returns the identity markdown for a single role by name.
     */
    public String loadIdentity(PdlcRole role) {
        return identities.getOrDefault(role, defaultIdentity(role));
    }

    private String defaultIdentity(PdlcRole role) {
        return "# " + role.name() + "\n\nNo identity configured for this role.";
    }
}
