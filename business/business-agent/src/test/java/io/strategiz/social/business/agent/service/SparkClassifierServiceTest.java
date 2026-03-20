package io.strategiz.social.business.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.cidadel.business.ai.engine.AiEngineRouterService;
import io.cidadel.business.ai.engine.AiEngineUnavailableException;
import io.cidadel.framework.ai.engine.model.AiEngineRequest;
import io.cidadel.framework.ai.engine.model.AiEngineResult;
import io.strategiz.social.data.entity.AiSdlcStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SparkClassifierServiceTest {

	@Mock
	private AiEngineRouterService aiEngineRouterService;

	@InjectMocks
	private SparkClassifierService sparkClassifierService;

	@Test
	void classifySparkType_validResponse_returnsType() {
		AiEngineResult result = AiEngineResult.success("social", "api-anthropic", "claude-haiku-4");
		when(aiEngineRouterService.executeStep(
				eq(AiSdlcStep.SPARK_CLASSIFICATION.name()), any(AiEngineRequest.class)))
				.thenReturn(result);

		String type = sparkClassifierService.classifySparkType("Post to Twitter", "Share an update on Twitter");

		assertEquals("social", type);
	}

	@Test
	void classifySparkType_validResponseWithWhitespace_returnsCleanedType() {
		AiEngineResult result = AiEngineResult.success("  Research  ", "api-anthropic", "claude-haiku-4");
		when(aiEngineRouterService.executeStep(
				eq(AiSdlcStep.SPARK_CLASSIFICATION.name()), any(AiEngineRequest.class)))
				.thenReturn(result);

		String type = sparkClassifierService.classifySparkType("Find info", "Research some topic");

		assertEquals("research", type);
	}

	@Test
	void classifySparkType_fuzzyMatch_extractsType() {
		AiEngineResult result = AiEngineResult.success(
				"I think the category is devops", "api-anthropic", "claude-haiku-4");
		when(aiEngineRouterService.executeStep(
				eq(AiSdlcStep.SPARK_CLASSIFICATION.name()), any(AiEngineRequest.class)))
				.thenReturn(result);

		String type = sparkClassifierService.classifySparkType("Deploy app", "Deploy to production");

		assertEquals("devops", type);
	}

	@Test
	void classifySparkType_engineReturnsError_defaultsToCode() {
		AiEngineResult result = AiEngineResult.error("rate limit exceeded", "api-anthropic");
		when(aiEngineRouterService.executeStep(
				eq(AiSdlcStep.SPARK_CLASSIFICATION.name()), any(AiEngineRequest.class)))
				.thenReturn(result);

		String type = sparkClassifierService.classifySparkType("Fix bug", "Fix the login bug");

		assertEquals("code", type);
	}

	@Test
	void classifySparkType_engineThrowsException_defaultsToCode() {
		when(aiEngineRouterService.executeStep(
				eq(AiSdlcStep.SPARK_CLASSIFICATION.name()), any(AiEngineRequest.class)))
				.thenThrow(new AiEngineUnavailableException("SPARK_CLASSIFICATION",
						"No engine configured for step"));

		String type = sparkClassifierService.classifySparkType("Do something", "A task");

		assertEquals("code", type);
	}

	@Test
	void classifySparkType_unparseableResponse_defaultsToCode() {
		AiEngineResult result = AiEngineResult.success(
				"I'm not sure what category this is", "api-anthropic", "claude-haiku-4");
		when(aiEngineRouterService.executeStep(
				eq(AiSdlcStep.SPARK_CLASSIFICATION.name()), any(AiEngineRequest.class)))
				.thenReturn(result);

		String type = sparkClassifierService.classifySparkType("Something", "Unknown task");

		assertEquals("code", type);
	}

}
