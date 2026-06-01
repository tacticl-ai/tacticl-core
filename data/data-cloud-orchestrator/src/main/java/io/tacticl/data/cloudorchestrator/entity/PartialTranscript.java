package io.tacticl.data.cloudorchestrator.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * One interim/final transcript chunk from Deepgram (per SAD §9.5).
 *
 * <p>Stored on voice {@link Turn}s of role {@code "user"} for replay/debugging.
 */
public class PartialTranscript {

    private String text;
    private double confidence;
    @JsonProperty("isFinal") private boolean isFinal;
    private Instant timestamp;

    protected PartialTranscript() {}

    public PartialTranscript(String text, double confidence, boolean isFinal, Instant timestamp) {
        this.text = text;
        this.confidence = confidence;
        this.isFinal = isFinal;
        this.timestamp = timestamp;
    }

    public String getText() { return text; }
    public double getConfidence() { return confidence; }
    @JsonProperty("isFinal") public boolean isFinal() { return isFinal; }
    public Instant getTimestamp() { return timestamp; }

    public void setText(String text) { this.text = text; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    @JsonProperty("isFinal") public void setFinal(boolean isFinal) { this.isFinal = isFinal; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
