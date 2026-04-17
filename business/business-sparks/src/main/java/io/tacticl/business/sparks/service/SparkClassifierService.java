package io.tacticl.business.sparks.service;

import io.cidadel.client.anthropic.AnthropicDirectClient;
import io.cidadel.client.base.llm.model.LlmMessage;
import io.cidadel.client.base.llm.model.LlmResponse;
import io.tacticl.data.sparks.entity.SparkType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Classifies a free-form spark input into one of the {@link SparkType} categories
 * using Claude Haiku 4.5. Defaults to {@link SparkType#RESEARCH} on any error or
 * unrecognized response.
 */
@Service
public class SparkClassifierService {

    private static final Logger log = LoggerFactory.getLogger(SparkClassifierService.class);

    private static final String CLASSIFIER_MODEL = "claude-haiku-4-5-20251001";

    private static final String SYSTEM_PROMPT = """
            You are a task classifier. Given a user request, respond with exactly one word indicating the type: CODE, DEVOPS, RESEARCH, CREATIVE, or DATA.
            Respond with only the word, nothing else.
            CODE = software development, APIs, scripts.
            DEVOPS = infrastructure, deployments, CI/CD.
            RESEARCH = information gathering, summaries, analysis.
            CREATIVE = writing, design, content creation.
            DATA = data processing, analysis, transformations.
            """;

    private final AnthropicDirectClient anthropicClient;

    public SparkClassifierService(AnthropicDirectClient anthropicClient) {
        this.anthropicClient = anthropicClient;
    }

    /**
     * Classify the given spark input text. Returns {@link SparkType#RESEARCH}
     * if the underlying classifier call fails or returns an unrecognized value.
     */
    public SparkType classify(String input) {
        try {
            List<LlmMessage> messages = List.of(LlmMessage.user(input));
            LlmResponse response = anthropicClient.generateContent(CLASSIFIER_MODEL, messages, SYSTEM_PROMPT);

            String content = response != null ? response.getContent() : null;
            if (content == null || content.isBlank()) {
                log.warn("SparkClassifier received empty response, defaulting to RESEARCH");
                return SparkType.RESEARCH;
            }

            String token = content.trim().toUpperCase();
            try {
                return SparkType.valueOf(token);
            } catch (IllegalArgumentException ex) {
                log.warn("SparkClassifier received unknown type '{}', defaulting to RESEARCH", token);
                return SparkType.RESEARCH;
            }
        } catch (Exception ex) {
            log.warn("SparkClassifier call failed, defaulting to RESEARCH: {}", ex.getMessage());
            return SparkType.RESEARCH;
        }
    }
}
