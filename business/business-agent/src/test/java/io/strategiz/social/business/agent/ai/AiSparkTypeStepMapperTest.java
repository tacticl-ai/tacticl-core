package io.strategiz.social.business.agent.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.strategiz.social.data.entity.AiSdlcStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AiSparkTypeStepMapperTest {

	@ParameterizedTest
	@CsvSource({
			"code,     CODE_GENERATION",
			"social,   SOCIAL_CONTENT",
			"research, WEB_RESEARCH",
			"devops,   DEPLOYMENT_SCRIPT",
			"creative, CREATIVE_WRITING",
			"data,     CODE_ANALYSIS"
	})
	void allSparkTypesMappedCorrectly(String sparkType, String expectedStep) {
		assertEquals(AiSdlcStep.valueOf(expectedStep), AiSparkTypeStepMapper.mapToStep(sparkType));
	}

	@Test
	void nullDefaultsToCodeGeneration() {
		assertEquals(AiSdlcStep.CODE_GENERATION, AiSparkTypeStepMapper.mapToStep(null));
	}

	@ParameterizedTest
	@ValueSource(strings = {"unknown", "finance", "music", ""})
	void unknownStringDefaultsToCodeGeneration(String sparkType) {
		assertEquals(AiSdlcStep.CODE_GENERATION, AiSparkTypeStepMapper.mapToStep(sparkType));
	}

	@ParameterizedTest
	@CsvSource({
			"CODE,     CODE_GENERATION",
			"Social,   SOCIAL_CONTENT",
			"RESEARCH, WEB_RESEARCH",
			"DevOps,   DEPLOYMENT_SCRIPT",
			"CREATIVE, CREATIVE_WRITING",
			"Data,     CODE_ANALYSIS"
	})
	void caseInsensitive(String sparkType, String expectedStep) {
		assertEquals(AiSdlcStep.valueOf(expectedStep), AiSparkTypeStepMapper.mapToStep(sparkType));
	}

	@ParameterizedTest
	@CsvSource({
			"'  code  ', CODE_GENERATION",
			"' social ', SOCIAL_CONTENT",
			"'research ', WEB_RESEARCH"
	})
	void whitespaceTrimmed(String sparkType, String expectedStep) {
		assertEquals(AiSdlcStep.valueOf(expectedStep), AiSparkTypeStepMapper.mapToStep(sparkType));
	}

}
