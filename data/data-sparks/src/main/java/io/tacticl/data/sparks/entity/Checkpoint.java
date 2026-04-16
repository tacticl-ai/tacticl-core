package io.tacticl.data.sparks.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.UUID;

@Document("checkpoints")
public class Checkpoint {

    @Id private String id;
    @Indexed private String sparkId;
    @Indexed private String userId;
    private CheckpointType type;
    private String prompt;
    private CheckpointStatus status;
    private String resolutionInstructions;
    private Instant createdAt;
    private Instant resolvedAt;

    protected Checkpoint() {}

    public static Checkpoint create(String sparkId, String userId,
                                    CheckpointType type, String prompt) {
        Checkpoint c = new Checkpoint();
        c.id = UUID.randomUUID().toString();
        c.sparkId = sparkId;
        c.userId = userId;
        c.type = type;
        c.prompt = prompt;
        c.status = CheckpointStatus.PENDING;
        c.createdAt = Instant.now();
        return c;
    }

    public void resolve(CheckpointStatus decision, String instructions) {
        this.status = decision;
        this.resolutionInstructions = instructions;
        this.resolvedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getSparkId() { return sparkId; }
    public String getUserId() { return userId; }
    public CheckpointType getType() { return type; }
    public String getPrompt() { return prompt; }
    public CheckpointStatus getStatus() { return status; }
    public String getResolutionInstructions() { return resolutionInstructions; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }
}
