package io.tacticl.business.sparks.service;

import io.cidadel.client.anthropic.AnthropicDirectClient;
import io.cidadel.client.base.llm.model.LlmMessage;
import io.cidadel.client.base.llm.model.LlmResponse;
import io.tacticl.data.sparks.entity.SparkType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SparkClassifierServiceTest {

    @Mock AnthropicDirectClient anthropicClient;
    @InjectMocks SparkClassifierService classifierService;

    @Test
    void classify_codeRequest_returnsCode() {
        LlmResponse response = new LlmResponse();
        response.setContent("CODE");
        response.setSuccess(true);
        when(anthropicClient.generateContent(anyString(), anyList(), anyString())).thenReturn(response);

        SparkType result = classifierService.classify("build me a REST API");

        assertThat(result).isEqualTo(SparkType.CODE);
    }

    @Test
    void classify_researchRequest_returnsResearch() {
        LlmResponse response = new LlmResponse();
        response.setContent("RESEARCH");
        response.setSuccess(true);
        when(anthropicClient.generateContent(anyString(), anyList(), anyString())).thenReturn(response);

        SparkType result = classifierService.classify("what is the news today");

        assertThat(result).isEqualTo(SparkType.RESEARCH);
    }

    @Test
    void classify_unknownResponse_defaultsToResearch() {
        LlmResponse response = new LlmResponse();
        response.setContent("nonsense-not-a-spark-type");
        response.setSuccess(true);
        when(anthropicClient.generateContent(anyString(), anyList(), anyString())).thenReturn(response);

        SparkType result = classifierService.classify("something ambiguous");

        assertThat(result).isEqualTo(SparkType.RESEARCH);
    }

    @Test
    void classify_clientThrows_defaultsToResearch() {
        when(anthropicClient.generateContent(anyString(), anyList(), anyString()))
                .thenThrow(new RuntimeException("API down"));

        SparkType result = classifierService.classify("anything");

        assertThat(result).isEqualTo(SparkType.RESEARCH);
    }

    @Test
    void classify_lowercaseAndWhitespace_isHandled() {
        LlmResponse response = new LlmResponse();
        response.setContent("  devops \n");
        response.setSuccess(true);
        when(anthropicClient.generateContent(anyString(), anyList(), anyString())).thenReturn(response);

        SparkType result = classifierService.classify("deploy the app");

        assertThat(result).isEqualTo(SparkType.DEVOPS);
    }

    @Test
    void classify_nullContent_defaultsToResearch() {
        LlmResponse response = new LlmResponse();
        response.setContent(null);
        response.setSuccess(true);
        when(anthropicClient.generateContent(anyString(), anyList(), anyString())).thenReturn(response);

        SparkType result = classifierService.classify("anything");

        assertThat(result).isEqualTo(SparkType.RESEARCH);
    }

    @Test
    void classify_passesUserMessageAsLlmMessage() {
        LlmResponse response = new LlmResponse();
        response.setContent("CREATIVE");
        response.setSuccess(true);
        when(anthropicClient.generateContent(anyString(), anyList(), anyString())).thenAnswer(inv -> {
            List<LlmMessage> msgs = inv.getArgument(1);
            assertThat(msgs).hasSize(1);
            assertThat(msgs.get(0).getRole()).isEqualTo("user");
            assertThat(msgs.get(0).getContent()).isEqualTo("write a poem");
            return response;
        });

        SparkType result = classifierService.classify("write a poem");

        assertThat(result).isEqualTo(SparkType.CREATIVE);
    }
}
