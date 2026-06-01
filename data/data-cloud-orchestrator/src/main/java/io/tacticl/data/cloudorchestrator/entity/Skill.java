package io.tacticl.data.cloudorchestrator.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import tools.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * Skill registry entry — backs an Anthropic tool definition (per SAD §4.1 / §4.4b).
 *
 * <p>{@link #inputSchema} is the JSON Schema passed to Claude's tool-use API.
 * {@link #activityName} names the Temporal activity that handles invocations.
 */
@Document("skills")
public class Skill {

    @Id private String id;                  // kebab-case, e.g. "propose_implementation"
    private String name;                    // human-readable
    private String description;             // shown to LLM as tool description
    private JsonNode inputSchema;           // Anthropic tool-use input schema
    private String activityName;            // Temporal activity name
    @Indexed private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    protected Skill() {}

    public static Skill create(String id, String name, String description,
                               JsonNode inputSchema, String activityName) {
        Skill s = new Skill();
        s.id = id;
        s.name = name;
        s.description = description;
        s.inputSchema = inputSchema;
        s.activityName = activityName;
        s.active = true;
        s.createdAt = Instant.now();
        s.updatedAt = s.createdAt;
        return s;
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public JsonNode getInputSchema() { return inputSchema; }
    public String getActivityName() { return activityName; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setInputSchema(JsonNode inputSchema) { this.inputSchema = inputSchema; }
    public void setActivityName(String activityName) { this.activityName = activityName; }
    public void setActive(boolean active) { this.active = active; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
