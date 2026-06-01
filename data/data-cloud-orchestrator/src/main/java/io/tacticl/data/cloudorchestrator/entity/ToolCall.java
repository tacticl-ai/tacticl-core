package io.tacticl.data.cloudorchestrator.entity;

import tools.jackson.databind.JsonNode;

/**
 * One Anthropic tool-use invocation captured on an assistant {@link Turn}
 * (per SAD §9.5). {@code input}/{@code output} are stored as JsonNode to
 * preserve the exact wire shape regardless of the activity backing the skill.
 */
public class ToolCall {

    private String toolName;
    private JsonNode input;
    private JsonNode output;
    private long latencyMs;
    private String error;       // nullable

    protected ToolCall() {}

    public ToolCall(String toolName, JsonNode input, JsonNode output, long latencyMs, String error) {
        this.toolName = toolName;
        this.input = input;
        this.output = output;
        this.latencyMs = latencyMs;
        this.error = error;
    }

    public String getToolName() { return toolName; }
    public JsonNode getInput() { return input; }
    public JsonNode getOutput() { return output; }
    public long getLatencyMs() { return latencyMs; }
    public String getError() { return error; }

    public void setToolName(String toolName) { this.toolName = toolName; }
    public void setInput(JsonNode input) { this.input = input; }
    public void setOutput(JsonNode output) { this.output = output; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }
    public void setError(String error) { this.error = error; }
}
