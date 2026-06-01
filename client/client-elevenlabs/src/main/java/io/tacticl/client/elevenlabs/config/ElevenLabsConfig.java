package io.tacticl.client.elevenlabs.config;

/** Configuration for the ElevenLabs streaming TTS API. */
public class ElevenLabsConfig {

    private String apiKey;

    private String apiBaseUrl = "wss://api.elevenlabs.io";

    private String model = "eleven_turbo_v2";

    /**
     * Default voice id used when a session config does not specify one.
     *
     * <p>TODO(qa-config): replace placeholder with the real Adam voice id in
     * QA / prod application properties (see SAD §5.3 "single voice across all
     * personas v1"). The literal value here is a stub so the bean still binds
     * cleanly in environments that have not yet provisioned the voice id.
     */
    private String defaultVoiceId = "adam-stub-voice-id";

    private String defaultOutputFormat = "mp3_44100_128";

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty();
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getDefaultVoiceId() {
        return defaultVoiceId;
    }

    public void setDefaultVoiceId(String defaultVoiceId) {
        this.defaultVoiceId = defaultVoiceId;
    }

    public String getDefaultOutputFormat() {
        return defaultOutputFormat;
    }

    public void setDefaultOutputFormat(String defaultOutputFormat) {
        this.defaultOutputFormat = defaultOutputFormat;
    }

}
