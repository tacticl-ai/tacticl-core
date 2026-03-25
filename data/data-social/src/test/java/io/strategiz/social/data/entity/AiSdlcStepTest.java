package io.strategiz.social.data.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AiSdlcStepTest {

	@Test
	void hasTwentyFourValues() {
		assertEquals(24, AiSdlcStep.values().length);
	}

	@Test
	void descriptionsAreSet() {
		assertEquals("Classify incoming spark type", AiSdlcStep.SPARK_CLASSIFICATION.getDescription());
		assertEquals("Write new code", AiSdlcStep.CODE_GENERATION.getDescription());
		assertEquals("Compose social media posts", AiSdlcStep.SOCIAL_CONTENT.getDescription());
		assertEquals("Analyze logs, metrics, and alerts", AiSdlcStep.MONITORING_ANALYSIS.getDescription());
	}

	@Test
	void classificationAndRoutingCategoryPresent() {
		assertNotNull(AiSdlcStep.valueOf("SPARK_CLASSIFICATION"));
		assertNotNull(AiSdlcStep.valueOf("TASK_DECOMPOSITION"));
	}

	@Test
	void codeLifecycleCategoryPresent() {
		assertNotNull(AiSdlcStep.valueOf("CODE_GENERATION"));
		assertNotNull(AiSdlcStep.valueOf("CODE_REVIEW"));
		assertNotNull(AiSdlcStep.valueOf("CODE_REFACTORING"));
		assertNotNull(AiSdlcStep.valueOf("BUG_DIAGNOSIS"));
		assertNotNull(AiSdlcStep.valueOf("BUG_FIX"));
		assertNotNull(AiSdlcStep.valueOf("TEST_GENERATION"));
		assertNotNull(AiSdlcStep.valueOf("TEST_EXECUTION"));
	}

	@Test
	void contentAndCommunicationCategoryPresent() {
		assertNotNull(AiSdlcStep.valueOf("PR_DESCRIPTION"));
		assertNotNull(AiSdlcStep.valueOf("DOCUMENTATION"));
		assertNotNull(AiSdlcStep.valueOf("COMMIT_MESSAGE"));
	}

	@Test
	void researchAndAnalysisCategoryPresent() {
		assertNotNull(AiSdlcStep.valueOf("WEB_RESEARCH"));
		assertNotNull(AiSdlcStep.valueOf("CODE_ANALYSIS"));
	}

	@Test
	void socialAndCreativeCategoryPresent() {
		assertNotNull(AiSdlcStep.valueOf("SOCIAL_CONTENT"));
		assertNotNull(AiSdlcStep.valueOf("CREATIVE_WRITING"));
		assertNotNull(AiSdlcStep.valueOf("IMAGE_ANALYSIS"));
	}

	@Test
	void devOpsCategoryPresent() {
		assertNotNull(AiSdlcStep.valueOf("DEPLOYMENT_SCRIPT"));
		assertNotNull(AiSdlcStep.valueOf("MONITORING_ANALYSIS"));
	}

	@Test
	void pdlcPipelineRolesCategoryPresent() {
		assertNotNull(AiSdlcStep.valueOf("REQUIREMENTS_GATHERING"));
		assertNotNull(AiSdlcStep.valueOf("SYSTEM_DESIGN"));
		assertNotNull(AiSdlcStep.valueOf("UI_UX_DESIGN"));
		assertNotNull(AiSdlcStep.valueOf("SECURITY_REVIEW"));
		assertNotNull(AiSdlcStep.valueOf("RETROSPECTIVE"));
	}

	@Test
	void noNullDescriptions() {
		for (AiSdlcStep step : AiSdlcStep.values()) {
			assertNotNull(step.getDescription(), step.name() + " has null description");
			assertFalse(step.getDescription().isBlank(), step.name() + " has blank description");
		}
	}

}
