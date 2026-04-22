package io.tacticl.data.sparks.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.UUID;

@Document("sparks")
public class Spark {

    @Id private String id;
    @Indexed private String userId;
    private String input;
    private SparkType type;
    private SparkStatus status;
    private SparkRoute route;
    private String deviceId;
    private String pipelineRunId;
    private SparkInitiatorSource initiatorSource;
    private String initiatorUserId;
    private int tokenCost;
    private String modelUsed;
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;

    protected Spark() {}

    public static Spark create(String userId, String input) {
        Spark s = new Spark();
        s.id = UUID.randomUUID().toString();
        s.userId = userId;
        s.input = input;
        s.status = SparkStatus.PENDING;
        s.createdAt = Instant.now();
        return s;
    }

    public void classify(SparkType type) {
        this.type = type;
        this.status = SparkStatus.ROUTING;
    }

    public void markExecuting(SparkRoute route, String deviceId) {
        this.route = route;
        this.deviceId = deviceId;
        this.status = SparkStatus.EXECUTING;
        this.startedAt = Instant.now();
    }

    public void markCheckpoint() { this.status = SparkStatus.CHECKPOINT; }
    public void markExecutingFromCheckpoint() { this.status = SparkStatus.EXECUTING; }

    public void markCompleted(int tokenCost, String modelUsed) {
        this.status = SparkStatus.COMPLETED;
        this.tokenCost = tokenCost;
        this.modelUsed = modelUsed;
        this.completedAt = Instant.now();
    }

    public void markFailed() {
        this.status = SparkStatus.FAILED;
        this.completedAt = Instant.now();
    }

    public void cancel() {
        this.status = SparkStatus.CANCELLED;
        this.completedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getInput() { return input; }
    public SparkType getType() { return type; }
    public SparkStatus getStatus() { return status; }
    public SparkRoute getRoute() { return route; }
    public String getDeviceId() { return deviceId; }
    public String getPipelineRunId() { return pipelineRunId; }
    public int getTokenCost() { return tokenCost; }
    public String getModelUsed() { return modelUsed; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setPipelineRunId(String pipelineRunId) { this.pipelineRunId = pipelineRunId; }
    public SparkInitiatorSource getInitiatorSource() { return initiatorSource; }
    public void setInitiatorSource(SparkInitiatorSource initiatorSource) { this.initiatorSource = initiatorSource; }
    public String getInitiatorUserId() { return initiatorUserId; }
    public void setInitiatorUserId(String initiatorUserId) { this.initiatorUserId = initiatorUserId; }
}
