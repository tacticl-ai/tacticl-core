package io.tacticl.data.cloudorchestrator.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Persona registry entry (per SAD §4.1).
 *
 * <p>One row per persona id (kebab-case, e.g. {@code product-manager}, {@code architect}).
 * Conversational personas drive in-JVM Anthropic calls; PDLC personas drive ephemeral
 * Arbiter containers (per SAD §4.5).
 *
 * <p>Editing creates v(n+1); old versions are retained for replay/reproducibility
 * (per SAD §4.2 and §4.5.2).
 */
@Document("personas")
public class Persona {

    @Id private String id;                  // kebab-case, e.g. "product-manager"
    @Indexed private PersonaFamily family;
    private String displayName;
    private String description;
    private String systemPrompt;            // markdown body
    private String defaultModel;            // e.g. "claude-haiku-4-5"
    private List<String> skillIds;          // references skills.id
    private VoicePreset voicePreset;        // nullable; null for PDLC personas
    @Indexed private boolean active;
    private int version;                    // bumped on edit
    private Instant createdAt;
    private Instant updatedAt;

    protected Persona() {}

    public static Persona create(String id, PersonaFamily family, String displayName,
                                 String description, String systemPrompt, String defaultModel,
                                 List<String> skillIds, VoicePreset voicePreset) {
        Persona p = new Persona();
        p.id = id;
        p.family = family;
        p.displayName = displayName;
        p.description = description;
        p.systemPrompt = systemPrompt;
        p.defaultModel = defaultModel;
        p.skillIds = skillIds != null ? skillIds : new ArrayList<>();
        p.voicePreset = voicePreset;
        p.active = true;
        p.version = 1;
        p.createdAt = Instant.now();
        p.updatedAt = p.createdAt;
        return p;
    }

    /** Bump version + updatedAt after an edit (callers are expected to mutate other fields first). */
    public void bumpVersion() {
        this.version += 1;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public PersonaFamily getFamily() { return family; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public String getSystemPrompt() { return systemPrompt; }
    public String getDefaultModel() { return defaultModel; }
    public List<String> getSkillIds() { return skillIds; }
    public VoicePreset getVoicePreset() { return voicePreset; }
    public boolean isActive() { return active; }
    public int getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setId(String id) { this.id = id; }
    public void setFamily(PersonaFamily family) { this.family = family; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setDescription(String description) { this.description = description; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public void setDefaultModel(String defaultModel) { this.defaultModel = defaultModel; }
    public void setSkillIds(List<String> skillIds) { this.skillIds = skillIds; }
    public void setVoicePreset(VoicePreset voicePreset) { this.voicePreset = voicePreset; }
    public void setActive(boolean active) { this.active = active; }
    public void setVersion(int version) { this.version = version; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
