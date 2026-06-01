package io.tacticl.data.cloudorchestrator.entity;

/**
 * Per-persona ElevenLabs voice config (per SAD §4.1).
 *
 * <p>Embedded in {@link Persona}. {@code null} for PDLC personas — they don't speak
 * directly; the chat layer narrates pipeline events through Product Manager's
 * {@code summarize_pipeline_progress} skill.
 */
public class VoicePreset {

    private String providerVoiceId;   // ElevenLabs voice id
    private String style;             // e.g. "calm", "energetic", "serious"
    private double stability;         // 0.0 - 1.0
    private double similarityBoost;   // 0.0 - 1.0

    protected VoicePreset() {}

    public VoicePreset(String providerVoiceId, String style, double stability, double similarityBoost) {
        this.providerVoiceId = providerVoiceId;
        this.style = style;
        this.stability = stability;
        this.similarityBoost = similarityBoost;
    }

    public String getProviderVoiceId() { return providerVoiceId; }
    public String getStyle() { return style; }
    public double getStability() { return stability; }
    public double getSimilarityBoost() { return similarityBoost; }

    public void setProviderVoiceId(String providerVoiceId) { this.providerVoiceId = providerVoiceId; }
    public void setStyle(String style) { this.style = style; }
    public void setStability(double stability) { this.stability = stability; }
    public void setSimilarityBoost(double similarityBoost) { this.similarityBoost = similarityBoost; }
}
