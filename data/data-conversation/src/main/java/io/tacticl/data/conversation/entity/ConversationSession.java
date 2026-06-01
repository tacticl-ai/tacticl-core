package io.tacticl.data.conversation.entity;

import io.tacticl.data.cloudorchestrator.entity.CostBreakdown;
import io.tacticl.data.cloudorchestrator.entity.SessionMode;
import io.tacticl.data.cloudorchestrator.entity.Turn;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Conversation session — Mongo projection of a {@code CloudAgentSessionWorkflow}
 * (per SAD §9.1).
 *
 * <p>The new model uses {@link #turns} (append-only, persona-tagged, modality-aware)
 * and the extended {@link io.tacticl.data.cloudorchestrator.entity.SessionStatus}
 * state machine. Legacy fields ({@link #messages}, {@link #sparkId},
 * {@link #detectedSparkType}, {@link #proposalText}) are preserved as
 * {@code @Deprecated} for backward compat during the single-cut migration.
 *
 * <p><b>Not on this entity:</b> the list of currently-active pipelines. That is
 * a Mongo query on {@code pipeline_runs} by {@code userId} (cross-session;
 * per SAD §9.1 note).
 */
@Document("conversation_sessions")
public class ConversationSession {

    @Id private String id;
    @Indexed private String userId;

    /** Temporal workflow id ({@code CloudAgentSessionWorkflow}). Nullable on legacy records. */
    @Indexed private String workflowId;

    private String title;

    /**
     * Session status — new model uses the extended state machine in
     * {@link io.tacticl.data.cloudorchestrator.entity.SessionStatus}.
     */
    private io.tacticl.data.cloudorchestrator.entity.SessionStatus status;

    /** Input/output mode (per SAD §5.1). */
    private SessionMode mode;

    /**
     * Sparks created from THIS session (append-only). Reading "user's active
     * pipelines" is a query on {@code pipeline_runs} by {@code userId} — NOT
     * a join through this list (per SAD §9.1 / §3.6).
     */
    private List<String> sessionStartedSparkIds;

    /** Persona currently responding in THIS session. */
    private String activePersonaId;

    /** Pipeline this session is currently engaging with (per-session focus). */
    private String focusedPipelineId;

    /** Per-session cumulative cost breakdown. */
    private CostBreakdown costAccumulator;

    /** Per-session cost ceiling in USD. Default 5.0. */
    private double costCeilingUsd;

    /** Append-only conversation transcript for THIS session. */
    private List<Turn> turns;

    @Indexed private String projectId;

    /**
     * Initiator source code (e.g. {@code "TELEGRAM_GROUP"}, {@code "WEB"}). Stored as
     * a string rather than an enum to avoid a cross-module dependency from
     * {@code data-conversation} to {@code data-sparks}, which the project's
     * {@code data-* → framework-* only} rule forbids. If the set of sources grows,
     * lift {@code SparkInitiatorSource} into a shared types module and switch this
     * field to that enum.
     */
    private String initiatorSource;

    private String repoUrl;

    // ---------- Legacy fields (deprecated; retained for migration compat) ----------

    /**
     * @deprecated Use {@link #turns}. The legacy flat {@code messages} list is kept as
     *     a derived view for backward compat during the single-cut migration.
     *     Future writes go to {@code turns}; existing reads continue to work.
     */
    @Deprecated private List<ConversationMessage> messages;

    /**
     * @deprecated Replaced by {@link #sessionStartedSparkIds}. Retained for migration
     *     compatibility — pre-rollout records hold a single sparkId here.
     */
    @Deprecated @Indexed private String sparkId;

    /**
     * @deprecated The session no longer pre-classifies spark type; classification
     *     happens at spark creation time. Retained for legacy data only.
     */
    @Deprecated private String detectedSparkType;

    /**
     * @deprecated Legacy proposal text from the pre-Temporal {@code ConversationService}.
     *     Replaced by the structured proposal Turn / artifacts.
     */
    @Deprecated private String proposalText;

    private Instant createdAt;
    private Instant updatedAt;

    protected ConversationSession() {}

    public static ConversationSession create(String userId, String firstMessage) {
        ConversationSession s = new ConversationSession();
        s.id = UUID.randomUUID().toString();
        s.userId = userId;
        s.title = firstMessage != null && firstMessage.length() > 57
            ? firstMessage.substring(0, 57) + "..."
            : firstMessage;
        // Legacy default: GATHERING (matches pre-orchestrator ConversationService contract).
        // The new CloudAgentSessionWorkflow explicitly sets IDLE → ENGAGED via changeStatus on its own path.
        s.status = io.tacticl.data.cloudorchestrator.entity.SessionStatus.GATHERING;
        s.mode = SessionMode.TEXT_ONLY;
        s.sessionStartedSparkIds = new ArrayList<>();
        s.turns = new ArrayList<>();
        s.messages = new ArrayList<>();
        s.costAccumulator = new CostBreakdown();
        s.costCeilingUsd = 5.0;
        s.createdAt = Instant.now();
        s.updatedAt = s.createdAt;
        return s;
    }

    public static ConversationSession createForTelegramGroup(String userId, String projectId, String firstMessage) {
        ConversationSession s = create(userId, firstMessage);
        s.projectId = projectId;
        s.initiatorSource = "TELEGRAM_GROUP";
        return s;
    }

    /** Append a new persona/user turn to the session. */
    public void appendTurn(Turn turn) {
        if (this.turns == null) this.turns = new ArrayList<>();
        this.turns.add(turn);
        this.updatedAt = Instant.now();
    }

    public void recordStartedSpark(String sparkId) {
        if (this.sessionStartedSparkIds == null) this.sessionStartedSparkIds = new ArrayList<>();
        this.sessionStartedSparkIds.add(sparkId);
        this.updatedAt = Instant.now();
    }

    public void changeStatus(io.tacticl.data.cloudorchestrator.entity.SessionStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public void changeMode(SessionMode mode) {
        this.mode = mode;
        this.updatedAt = Instant.now();
    }

    // --- Legacy bridge API (pre-cloud-agent-orchestrator) ----------------------------
    // These methods exist only so the legacy ConversationService in business-conversation
    // and its tests + Telegram callsites keep compiling during the single-cut migration
    // (PRD §7 / SAD §10). They translate the old API to the new turn/status model.
    // DELETE these when ConversationService is removed in the Phase-4 cutover.

    /** @deprecated Use {@link #appendTurn(Turn)} with a Turn built from cloudorchestrator entities. */
    @Deprecated
    public void addMessage(ConversationMessage message) {
        if (message == null) return;
        // Bridge writes to BOTH the legacy messages list (for existing readers and tests)
        // AND the new turns list (for the new conversation model). Both are deprecated paths;
        // they'll be removed when ConversationService and its tests are deleted in Phase 4.
        if (this.messages == null) this.messages = new ArrayList<>();
        this.messages.add(message);
        String modality = "text";
        Turn turn = "user".equalsIgnoreCase(message.getRole())
                ? Turn.user(message.getContent(), modality)
                : Turn.assistant("product-manager", message.getContent(), modality);
        appendTurn(turn);  // also bumps updatedAt
    }

    /** @deprecated Use {@link #changeStatus(io.tacticl.data.cloudorchestrator.entity.SessionStatus)}. */
    @Deprecated
    public void revertToGathering() {
        changeStatus(io.tacticl.data.cloudorchestrator.entity.SessionStatus.GATHERING);
    }

    /** @deprecated Use {@link #recordStartedSpark(String)} + {@link #changeStatus}. */
    @Deprecated
    public void markActive(String sparkId) {
        if (sparkId != null) {
            this.sparkId = sparkId;     // legacy single-spark field
            recordStartedSpark(sparkId);
        }
        changeStatus(io.tacticl.data.cloudorchestrator.entity.SessionStatus.PIPELINE_ACTIVE);
    }

    /** @deprecated Use {@link #changeStatus} + set detectedSparkType directly (also deprecated).
     *  Legacy arg order: (sparkType, proposalSummary). */
    @Deprecated
    public void markProposing(String sparkType, String proposalSummary) {
        if (sparkType != null) {
            this.detectedSparkType = sparkType;
        }
        if (proposalSummary != null) {
            this.proposalText = proposalSummary;
        }
        changeStatus(io.tacticl.data.cloudorchestrator.entity.SessionStatus.PROPOSING);
    }

    /** @deprecated Use {@link #changeStatus} with COMPLETED. */
    @Deprecated
    public void markCompleted() {
        changeStatus(io.tacticl.data.cloudorchestrator.entity.SessionStatus.COMPLETED);
    }

    public void focusOn(String pipelineId) {
        this.focusedPipelineId = pipelineId;
        this.updatedAt = Instant.now();
    }

    public void clearFocus() {
        this.focusedPipelineId = null;
        this.updatedAt = Instant.now();
    }

    public void setActivePersona(String personaId) {
        this.activePersonaId = personaId;
        this.updatedAt = Instant.now();
    }

    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
        this.updatedAt = Instant.now();
    }

    // ---------- Getters ----------

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getWorkflowId() { return workflowId; }
    public String getTitle() { return title; }
    public io.tacticl.data.cloudorchestrator.entity.SessionStatus getStatus() { return status; }
    public SessionMode getMode() { return mode; }
    public List<String> getSessionStartedSparkIds() { return sessionStartedSparkIds; }
    public String getActivePersonaId() { return activePersonaId; }
    public String getFocusedPipelineId() { return focusedPipelineId; }
    public CostBreakdown getCostAccumulator() { return costAccumulator; }
    public double getCostCeilingUsd() { return costCeilingUsd; }
    public List<Turn> getTurns() { return turns; }
    public String getProjectId() { return projectId; }
    public String getInitiatorSource() { return initiatorSource; }
    public String getRepoUrl() { return repoUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    /** @deprecated Use {@link #getTurns()}. */
    @Deprecated public List<ConversationMessage> getMessages() { return messages; }
    /** @deprecated Use {@link #getSessionStartedSparkIds()}. */
    @Deprecated public String getSparkId() { return sparkId; }
    /** @deprecated No replacement — classification moved out of the session. */
    @Deprecated public String getDetectedSparkType() { return detectedSparkType; }
    /** @deprecated Replaced by structured proposal Turn / artifacts. */
    @Deprecated public String getProposalText() { return proposalText; }

    // ---------- Setters ----------

    public void setId(String id) { this.id = id; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }
    public void setTitle(String title) { this.title = title; }
    public void setStatus(io.tacticl.data.cloudorchestrator.entity.SessionStatus status) { this.status = status; }
    public void setMode(SessionMode mode) { this.mode = mode; }
    public void setSessionStartedSparkIds(List<String> sessionStartedSparkIds) { this.sessionStartedSparkIds = sessionStartedSparkIds; }
    public void setActivePersonaId(String activePersonaId) { this.activePersonaId = activePersonaId; }
    public void setFocusedPipelineId(String focusedPipelineId) { this.focusedPipelineId = focusedPipelineId; }
    public void setCostAccumulator(CostBreakdown costAccumulator) { this.costAccumulator = costAccumulator; }
    public void setCostCeilingUsd(double costCeilingUsd) { this.costCeilingUsd = costCeilingUsd; }
    public void setTurns(List<Turn> turns) { this.turns = turns; }
    public void setInitiatorSource(String initiatorSource) { this.initiatorSource = initiatorSource; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    /** @deprecated Use {@link #appendTurn(Turn)}. */
    @Deprecated
    public void setMessages(List<ConversationMessage> messages) { this.messages = messages; }
    /** @deprecated Use {@link #recordStartedSpark(String)}. */
    @Deprecated
    public void setSparkId(String sparkId) { this.sparkId = sparkId; }
    /** @deprecated. */
    @Deprecated
    public void setDetectedSparkType(String detectedSparkType) { this.detectedSparkType = detectedSparkType; }
    /** @deprecated. */
    @Deprecated
    public void setProposalText(String proposalText) { this.proposalText = proposalText; }
}
