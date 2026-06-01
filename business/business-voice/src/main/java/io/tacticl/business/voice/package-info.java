/**
 * Voice command-center turn orchestration — the transport-neutral brain behind
 * the voice WebSocket. The socket transport + token endpoint live in
 * {@code service-voice}; this module owns:
 *
 * <ul>
 *   <li>{@link io.tacticl.business.voice.DeepgramSttBridge} — per-session STT leg
 *       over {@code client-deepgram} (PCM in → partial/final transcript out).</li>
 *   <li>{@link io.tacticl.business.voice.ElevenLabsTtsBridge} — per-session TTS leg
 *       over {@code client-elevenlabs} (text in → audio chunks out; non-blocking
 *       barge-in stop).</li>
 *   <li>{@link io.tacticl.business.voice.VoiceSessionService} — the turn loop:
 *       final transcript → classify → {@code IngressDispatchService.dispatch} →
 *       narrate; decision/barge-in/state handling.</li>
 *   <li>{@link io.tacticl.business.voice.VoiceRunUpdateChannel} — a
 *       {@code PipelineEventChannel} that narrates PDLC run updates back to the
 *       originating session (HUD + spoken transcript + checkpoint frames).</li>
 *   <li>{@link io.tacticl.business.voice.VoiceSessionRegistry} — thread-safe
 *       {@code sessionId}/{@code runId} → session index.</li>
 *   <li>{@link io.tacticl.business.voice.VoiceOutbound} — the WS sink seam
 *       {@code service-voice} implements; {@link io.tacticl.business.voice.VoiceFrames}
 *       builds the protocol's DOWN control frames.</li>
 * </ul>
 *
 * <p>Every bean is gated behind {@code tacticl.voice.enabled=true}. The wire
 * protocol is mirrored in {@code tacticl-web/src/voice/protocol.ts}.
 */
package io.tacticl.business.voice;
