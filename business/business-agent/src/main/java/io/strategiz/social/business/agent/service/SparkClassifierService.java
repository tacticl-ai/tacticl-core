package io.strategiz.social.business.agent.service;

import io.cidadel.business.ai.engine.AiEngineRouterService;
import io.cidadel.framework.ai.engine.model.AiEngineRequest;
import io.cidadel.framework.ai.engine.model.AiEngineResult;
import io.strategiz.social.data.entity.AiSdlcStep;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Classifies spark type using the AI engine routing layer. Resolves the appropriate engine
 * and model for the {@link AiSdlcStep#SPARK_CLASSIFICATION} step, with automatic fallback.
 */
@Service
public class SparkClassifierService {

	private static final Logger log = LoggerFactory.getLogger(SparkClassifierService.class);

	private static final Set<String> VALID_TYPES = Set.of("code", "social", "research", "devops", "creative", "data");

	private static final String DEFAULT_TYPE = "code";

	private final AiEngineRouterService aiEngineRouterService;

	public SparkClassifierService(AiEngineRouterService aiEngineRouterService) {
		this.aiEngineRouterService = aiEngineRouterService;
	}

	/**
	 * Classify the spark type based on its title and description.
	 * @param title the spark title
	 * @param description the spark description
	 * @return one of: code, social, research, devops, creative, data
	 */
	public String classifySparkType(String title, String description) {
		try {
			AiEngineRequest request = new AiEngineRequest();
			request.setPrompt(buildClassificationPrompt(title, description));

			AiEngineResult result = aiEngineRouterService.executeStep(
					AiSdlcStep.SPARK_CLASSIFICATION.name(), request);

			if (!result.isSuccess()) {
				log.warn("[CLASSIFIER] Engine returned error: {}, defaulting to '{}'",
						result.getError(), DEFAULT_TYPE);
				return DEFAULT_TYPE;
			}

			return parseClassification(result.getContent());
		}
		catch (Exception e) {
			log.error("[CLASSIFIER] Classification failed for title='{}': {}", title, e.getMessage(), e);
			return DEFAULT_TYPE;
		}
	}

	private String buildClassificationPrompt(String title, String description) {
		return "Classify the following task into exactly ONE category. "
				+ "Respond with ONLY the category name, nothing else.\n\n"
				+ "Categories: code, social, research, devops, creative, data\n\n" + "Title: " + title + "\n"
				+ "Description: " + (description != null ? description : "") + "\n\n" + "Category:";
	}

	private String parseClassification(String response) {
		String cleaned = response.trim().toLowerCase();

		// Direct match
		if (VALID_TYPES.contains(cleaned)) {
			return cleaned;
		}

		// Fuzzy fallback: check if response contains a valid type
		for (String validType : VALID_TYPES) {
			if (cleaned.contains(validType)) {
				log.debug("[CLASSIFIER] Fuzzy matched '{}' from response '{}'", validType, cleaned);
				return validType;
			}
		}

		log.warn("[CLASSIFIER] Could not parse '{}', defaulting to '{}'", cleaned, DEFAULT_TYPE);
		return DEFAULT_TYPE;
	}

}
