package io.tacticl.data.cloudorchestrator.entity;

/**
 * LLM token accounting for one {@link Turn} (per SAD §9.5).
 */
public class TokenUsage {

    private int inputTokens;
    private int outputTokens;
    private String model;

    protected TokenUsage() {}

    public TokenUsage(int inputTokens, int outputTokens, String model) {
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.model = model;
    }

    public int getInputTokens() { return inputTokens; }
    public int getOutputTokens() { return outputTokens; }
    public String getModel() { return model; }

    public void setInputTokens(int inputTokens) { this.inputTokens = inputTokens; }
    public void setOutputTokens(int outputTokens) { this.outputTokens = outputTokens; }
    public void setModel(String model) { this.model = model; }
}
