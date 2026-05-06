package io.tacticl.client.whisper;

import io.cidadel.framework.exception.CidadelException;
import io.github.bucket4j.Bucket;
import io.tacticl.client.whisper.client.WhisperClient;
import io.tacticl.client.whisper.config.WhisperConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

class WhisperClientTest {

    private static final String BASE_URL = "https://api.openai.com";
    private static final String API_KEY = "sk-test-key";

    private WhisperConfig config;
    private Bucket bucket;
    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private WhisperClient client;

    @BeforeEach
    void setUp() {
        config = new WhisperConfig();
        config.setApiKey(API_KEY);
        config.setBaseUrl(BASE_URL);
        config.setModel("whisper-1");
        bucket = mock(Bucket.class);
        when(bucket.tryConsume(1)).thenReturn(true);

        builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new WhisperClient(config, bucket, builder);
    }

    @Test
    void transcribeReturnsTextFromResponse() {
        server.expect(requestTo(BASE_URL + "/v1/audio/transcriptions"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + API_KEY))
            .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
            .andRespond(withSuccess(
                "{\"text\":\"hello world\"}",
                MediaType.APPLICATION_JSON));

        String result = client.transcribe(new byte[]{1, 2, 3, 4}, "audio.m4a", "audio/mp4");

        assertEquals("hello world", result);
        server.verify();
    }

    @Test
    void transcribeIncludesModelAndFileMultipartParts() {
        server.expect(requestTo(BASE_URL + "/v1/audio/transcriptions"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
            .andExpect(req -> {
                String body = req.getBody().toString();
                // multipart bodies include the part names + filename + model value
                assertTrue(body.contains("name=\"model\""), "missing model part");
                assertTrue(body.contains("whisper-1"), "missing model value");
                assertTrue(body.contains("name=\"file\""), "missing file part");
                assertTrue(body.contains("filename=\"audio.m4a\""), "missing filename");
            })
            .andRespond(withSuccess("{\"text\":\"ok\"}", MediaType.APPLICATION_JSON));

        client.transcribe(new byte[]{9, 9, 9}, "audio.m4a", "audio/mp4");

        server.verify();
    }

    @Test
    void transcribeWrapsServerErrorAsCidadelException() {
        server.expect(requestTo(BASE_URL + "/v1/audio/transcriptions"))
            .andRespond(withServerError());

        assertThrows(CidadelException.class,
            () -> client.transcribe(new byte[]{1}, "a.m4a", "audio/mp4"));
    }

    @Test
    void transcribeWrapsUnauthorizedAsCidadelException() {
        server.expect(requestTo(BASE_URL + "/v1/audio/transcriptions"))
            .andRespond(withUnauthorizedRequest());

        assertThrows(CidadelException.class,
            () -> client.transcribe(new byte[]{1}, "a.m4a", "audio/mp4"));
    }

    @Test
    void transcribeRateLimitedThrows() {
        Bucket fullBucket = mock(Bucket.class);
        when(fullBucket.tryConsume(1)).thenReturn(false);
        WhisperClient limited = new WhisperClient(config, fullBucket, builder);

        CidadelException ex = assertThrows(CidadelException.class,
            () -> limited.transcribe(new byte[]{1}, "a.m4a", "audio/mp4"));
        assertEquals("whisper-rate-limit-exceeded", ex.getErrorDetails().getPropertyKey());
    }

}
