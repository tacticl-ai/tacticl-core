package io.tacticl.data.cloudorchestrator.entity;

/**
 * Cumulative session cost in USD, split by provider (per SAD §9.1).
 *
 * <p>Embedded in {@code ConversationSession.costAccumulator}.
 */
public class CostBreakdown {

    private double llmUsd;
    private double sttUsd;
    private double ttsUsd;

    public CostBreakdown() {
        this.llmUsd = 0.0;
        this.sttUsd = 0.0;
        this.ttsUsd = 0.0;
    }

    public CostBreakdown(double llmUsd, double sttUsd, double ttsUsd) {
        this.llmUsd = llmUsd;
        this.sttUsd = sttUsd;
        this.ttsUsd = ttsUsd;
    }

    public void addLlm(double usd) { this.llmUsd += usd; }
    public void addStt(double usd) { this.sttUsd += usd; }
    public void addTts(double usd) { this.ttsUsd += usd; }

    public double totalUsd() { return llmUsd + sttUsd + ttsUsd; }

    public double getLlmUsd() { return llmUsd; }
    public double getSttUsd() { return sttUsd; }
    public double getTtsUsd() { return ttsUsd; }

    public void setLlmUsd(double llmUsd) { this.llmUsd = llmUsd; }
    public void setSttUsd(double sttUsd) { this.sttUsd = sttUsd; }
    public void setTtsUsd(double ttsUsd) { this.ttsUsd = ttsUsd; }
}
