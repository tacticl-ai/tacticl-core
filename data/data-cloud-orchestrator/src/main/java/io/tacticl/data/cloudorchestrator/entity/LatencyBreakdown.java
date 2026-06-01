package io.tacticl.data.cloudorchestrator.entity;

/**
 * Per-turn latency budget breakdown (per SAD §5.4 / §9.5).
 *
 * <ul>
 *   <li>{@code endpointMs} — Deepgram speech_final detection</li>
 *   <li>{@code routeMs} — PersonaRouter.route() duration (pure function)</li>
 *   <li>{@code llmFirstTokenMs} — Anthropic time-to-first-token</li>
 *   <li>{@code ttsFirstChunkMs} — ElevenLabs time-to-first-audio-chunk</li>
 *   <li>{@code totalMs} — wall-clock turn duration</li>
 * </ul>
 */
public class LatencyBreakdown {

    private int endpointMs;
    private int routeMs;
    private int llmFirstTokenMs;
    private int ttsFirstChunkMs;
    private int totalMs;

    protected LatencyBreakdown() {}

    public LatencyBreakdown(int endpointMs, int routeMs, int llmFirstTokenMs,
                            int ttsFirstChunkMs, int totalMs) {
        this.endpointMs = endpointMs;
        this.routeMs = routeMs;
        this.llmFirstTokenMs = llmFirstTokenMs;
        this.ttsFirstChunkMs = ttsFirstChunkMs;
        this.totalMs = totalMs;
    }

    public int getEndpointMs() { return endpointMs; }
    public int getRouteMs() { return routeMs; }
    public int getLlmFirstTokenMs() { return llmFirstTokenMs; }
    public int getTtsFirstChunkMs() { return ttsFirstChunkMs; }
    public int getTotalMs() { return totalMs; }

    public void setEndpointMs(int endpointMs) { this.endpointMs = endpointMs; }
    public void setRouteMs(int routeMs) { this.routeMs = routeMs; }
    public void setLlmFirstTokenMs(int llmFirstTokenMs) { this.llmFirstTokenMs = llmFirstTokenMs; }
    public void setTtsFirstChunkMs(int ttsFirstChunkMs) { this.ttsFirstChunkMs = ttsFirstChunkMs; }
    public void setTotalMs(int totalMs) { this.totalMs = totalMs; }
}
