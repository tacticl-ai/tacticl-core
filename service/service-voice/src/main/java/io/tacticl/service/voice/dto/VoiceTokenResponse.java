package io.tacticl.service.voice.dto;

/**
 * Response of {@code POST /v1/voice/token}. Mirrors the {@code VoiceTokenResponse}
 * shape in {@code tacticl-web/src/voice/protocol.ts} exactly:
 *
 * <ul>
 *   <li>{@code token} — the short-lived opaque voice session token the browser
 *       appends as {@code ?token=…} when opening the WS.</li>
 *   <li>{@code wsUrl} — optional absolute {@code wss://…} override; {@code null}
 *       means the browser uses its own {@code VITE_VOICE_WS_URL}.</li>
 *   <li>{@code expiresIn} — seconds-to-live, for proactive re-fetch on reconnect.</li>
 * </ul>
 */
public record VoiceTokenResponse(String token, String wsUrl, long expiresIn) {
}
