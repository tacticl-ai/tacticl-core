package io.tacticl.client.elevenlabs.client;

/**
 * Per-session ElevenLabs streaming TTS parameters.
 *
 * <p>A {@code null} {@link #voiceId} or {@link #outputFormat} signals the
 * client to fall back to the global defaults defined in
 * {@link io.tacticl.client.elevenlabs.config.ElevenLabsConfig}.
 *
 * @param voiceId ElevenLabs voice id (e.g. "adam-…"); null = use config default
 * @param stability voice stability 0.0–1.0, default 0.5
 * @param similarityBoost voice similarity boost 0.0–1.0, default 0.75
 * @param style optional style tag (may be {@code null})
 * @param outputFormat audio output format string (e.g. "mp3_44100_128"); null = use config default
 */
public record ElevenLabsSessionConfig(
        String voiceId,
        double stability,
        double similarityBoost,
        String style,
        String outputFormat) {

    public static ElevenLabsSessionConfig defaults() {
        return new ElevenLabsSessionConfig(null, 0.5, 0.75, null, null);
    }

    public static ElevenLabsSessionConfig forVoice(String voiceId) {
        return new ElevenLabsSessionConfig(voiceId, 0.5, 0.75, null, null);
    }

}
