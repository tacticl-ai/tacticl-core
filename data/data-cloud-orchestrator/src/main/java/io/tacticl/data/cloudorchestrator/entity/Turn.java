package io.tacticl.data.cloudorchestrator.entity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One conversation turn embedded in {@code ConversationSession.turns}
 * (per SAD §9.5). Append-only; existing turns are never edited.
 *
 * <p>{@code role} is {@code "user"} or {@code "assistant"}.
 * {@code modality} is {@code "voice"} or {@code "text"}.
 * {@code personaId} is set on assistant turns to identify which persona spoke.
 */
public class Turn {

    private String id;
    private String role;                    // "user" | "assistant"
    private String personaId;               // for assistant turns; null for user
    private String modality;                // "voice" | "text"
    private String text;
    private String audioRef;                // S3 key for input/output audio; nullable
    private List<PartialTranscript> partialTranscripts;  // for voice user turns; nullable
    private List<ToolCall> toolCalls;       // nullable
    private boolean interrupted;            // true if barge-in cut this turn
    private TokenUsage tokens;              // nullable
    private LatencyBreakdown latencyMs;     // nullable
    private Instant timestamp;

    protected Turn() {}

    public static Turn user(String text, String modality) {
        Turn t = new Turn();
        t.id = UUID.randomUUID().toString();
        t.role = "user";
        t.modality = modality;
        t.text = text;
        t.timestamp = Instant.now();
        return t;
    }

    public static Turn assistant(String personaId, String text, String modality) {
        Turn t = new Turn();
        t.id = UUID.randomUUID().toString();
        t.role = "assistant";
        t.personaId = personaId;
        t.modality = modality;
        t.text = text;
        t.timestamp = Instant.now();
        return t;
    }

    public String getId() { return id; }
    public String getRole() { return role; }
    public String getPersonaId() { return personaId; }
    public String getModality() { return modality; }
    public String getText() { return text; }
    public String getAudioRef() { return audioRef; }
    public List<PartialTranscript> getPartialTranscripts() { return partialTranscripts; }
    public List<ToolCall> getToolCalls() { return toolCalls; }
    public boolean isInterrupted() { return interrupted; }
    public TokenUsage getTokens() { return tokens; }
    public LatencyBreakdown getLatencyMs() { return latencyMs; }
    public Instant getTimestamp() { return timestamp; }

    public void setId(String id) { this.id = id; }
    public void setRole(String role) { this.role = role; }
    public void setPersonaId(String personaId) { this.personaId = personaId; }
    public void setModality(String modality) { this.modality = modality; }
    public void setText(String text) { this.text = text; }
    public void setAudioRef(String audioRef) { this.audioRef = audioRef; }
    public void setPartialTranscripts(List<PartialTranscript> partialTranscripts) { this.partialTranscripts = partialTranscripts; }
    public void setToolCalls(List<ToolCall> toolCalls) { this.toolCalls = toolCalls; }
    public void setInterrupted(boolean interrupted) { this.interrupted = interrupted; }
    public void setTokens(TokenUsage tokens) { this.tokens = tokens; }
    public void setLatencyMs(LatencyBreakdown latencyMs) { this.latencyMs = latencyMs; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
