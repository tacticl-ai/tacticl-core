package io.tacticl.client.whisper.client;

import io.cidadel.framework.exception.CidadelException;
import io.github.bucket4j.Bucket;
import io.tacticl.client.whisper.config.WhisperConfig;
import io.tacticl.client.whisper.exception.WhisperErrorDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Client for the OpenAI Whisper transcription API.
 *
 * <p>Posts audio bytes as a multipart/form-data request to
 * {@code POST /v1/audio/transcriptions} and returns the {@code text} field
 * from the JSON response.
 */
public class WhisperClient {

    private static final Logger logger = LoggerFactory.getLogger(WhisperClient.class);

    private static final String MODULE_NAME = "client-whisper";

    private static final String TRANSCRIPTIONS_PATH = "/v1/audio/transcriptions";

    private final WhisperConfig config;

    private final Bucket rateLimiter;

    private final RestClient restClient;

    private final JsonMapper objectMapper;

    public WhisperClient(WhisperConfig config, Bucket rateLimiter) {
        this(config, rateLimiter, RestClient.builder().baseUrl(config.getBaseUrl()));
    }

    /**
     * Test-friendly constructor: accepts a pre-configured {@link RestClient.Builder}
     * so tests can bind a {@code MockRestServiceServer} to it before the client is built.
     */
    public WhisperClient(WhisperConfig config, Bucket rateLimiter, RestClient.Builder builder) {
        this.config = config;
        this.rateLimiter = rateLimiter;
        this.objectMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
        this.restClient = builder
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    /**
     * Transcribe the given audio bytes via Whisper.
     *
     * @param audio raw audio bytes
     * @param filename original filename (extension hints at format, e.g. {@code audio.m4a})
     * @param contentType MIME type of the audio (e.g. {@code audio/mp4})
     * @return the transcribed text from the {@code text} JSON field
     */
    public String transcribe(byte[] audio, String filename, String contentType) {
        checkRateLimit();

        if (audio == null || audio.length == 0) {
            throw new CidadelException(WhisperErrorDetails.INVALID_AUDIO, MODULE_NAME);
        }

        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentType(MediaType.parseMediaType(contentType));
            ByteArrayResource fileResource = new ByteArrayResource(audio) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };
            org.springframework.http.HttpEntity<ByteArrayResource> filePart =
                new org.springframework.http.HttpEntity<>(fileResource, fileHeaders);
            body.add("file", filePart);
            body.add("model", config.getModel());

            String responseBody = restClient.post()
                .uri(TRANSCRIPTIONS_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.getApiKey())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                throw new CidadelException(WhisperErrorDetails.TRANSCRIPTION_FAILED, MODULE_NAME,
                    "empty response body");
            }

            JsonNode node = objectMapper.readTree(responseBody);
            JsonNode textNode = node.get("text");
            if (textNode == null || textNode.isNull()) {
                throw new CidadelException(WhisperErrorDetails.TRANSCRIPTION_FAILED, MODULE_NAME,
                    "response missing 'text' field");
            }
            return textNode.asString();
        }
        catch (CidadelException e) {
            throw e;
        }
        catch (HttpClientErrorException.Unauthorized e) {
            logger.error("Whisper API rejected key (401): {}", e.getMessage());
            throw new CidadelException(WhisperErrorDetails.UNAUTHORIZED, MODULE_NAME);
        }
        catch (HttpClientErrorException.TooManyRequests e) {
            logger.warn("Whisper API rate-limited upstream (429): {}", e.getMessage());
            throw new CidadelException(WhisperErrorDetails.RATE_LIMIT_EXCEEDED, MODULE_NAME);
        }
        catch (HttpServerErrorException e) {
            logger.error("Whisper API upstream error ({}): {}", e.getStatusCode(), e.getMessage());
            throw new CidadelException(WhisperErrorDetails.TRANSCRIPTION_FAILED, MODULE_NAME,
                "upstream " + e.getStatusCode());
        }
        catch (Exception e) {
            logger.error("Whisper transcription failed for filename {}: {}", filename, e.getMessage());
            throw new CidadelException(WhisperErrorDetails.TRANSCRIPTION_FAILED, MODULE_NAME, e.getMessage());
        }
    }

    private void checkRateLimit() {
        if (!rateLimiter.tryConsume(1)) {
            throw new CidadelException(WhisperErrorDetails.RATE_LIMIT_EXCEEDED, MODULE_NAME);
        }
    }

}
