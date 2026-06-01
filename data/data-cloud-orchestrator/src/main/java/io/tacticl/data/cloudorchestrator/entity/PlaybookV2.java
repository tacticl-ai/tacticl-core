package io.tacticl.data.cloudorchestrator.entity;

import io.tacticl.data.sparks.entity.SparkType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Mongo-backed playbook (per SAD §8.1). Replaces the hardcoded
 * {@code PlaybookSpecResolver} maps.
 *
 * <p>The {@code V2} suffix avoids collision with any legacy {@code Playbook} type
 * still living in {@code data-pipeline}; once the legacy resolver is deleted this
 * can be renamed.
 */
@Document("playbooks")
public class PlaybookV2 {

    @Id private String id;                  // e.g. "FULL_PDLC", "BUG_FIX"
    private String displayName;
    private String description;
    @Indexed private List<SparkType> sparkTypes;   // multikey index
    private List<PhaseConfig> phases;
    @Indexed private boolean active;
    private int version;
    private Instant createdAt;
    private Instant updatedAt;

    protected PlaybookV2() {}

    public static PlaybookV2 create(String id, String displayName, String description,
                                    List<SparkType> sparkTypes, List<PhaseConfig> phases) {
        PlaybookV2 pb = new PlaybookV2();
        pb.id = id;
        pb.displayName = displayName;
        pb.description = description;
        pb.sparkTypes = sparkTypes != null ? sparkTypes : new ArrayList<>();
        pb.phases = phases != null ? phases : new ArrayList<>();
        pb.active = true;
        pb.version = 1;
        pb.createdAt = Instant.now();
        pb.updatedAt = pb.createdAt;
        return pb;
    }

    public void bumpVersion() {
        this.version += 1;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public List<SparkType> getSparkTypes() { return sparkTypes; }
    public List<PhaseConfig> getPhases() { return phases; }
    public boolean isActive() { return active; }
    public int getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setId(String id) { this.id = id; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setDescription(String description) { this.description = description; }
    public void setSparkTypes(List<SparkType> sparkTypes) { this.sparkTypes = sparkTypes; }
    public void setPhases(List<PhaseConfig> phases) { this.phases = phases; }
    public void setActive(boolean active) { this.active = active; }
    public void setVersion(int version) { this.version = version; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
