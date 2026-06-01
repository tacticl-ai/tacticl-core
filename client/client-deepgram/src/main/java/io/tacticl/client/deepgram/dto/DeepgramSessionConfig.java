package io.tacticl.client.deepgram.dto;

/**
 * Per-session overrides for a Deepgram streaming connection.
 *
 * @param language BCP-47 language code (default {@code en-US})
 * @param keepalive if true, the session sends {@code {"type":"KeepAlive"}}
 *                  every 8s to keep the connection open during silence (SAD §5.2)
 */
public record DeepgramSessionConfig(String language, boolean keepalive) {

    public static DeepgramSessionConfig defaults() {
        return new DeepgramSessionConfig("en-US", true);
    }

}
