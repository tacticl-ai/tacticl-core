package io.strategiz.social.business.agent.pipeline.role;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.cidadel.business.ai.engine.AiEngineRouterService;
import io.strategiz.social.data.entity.AiSdlcStep;
import io.strategiz.social.data.entity.PdlcRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PmRoleSkillTest {

	@Mock
	private AiEngineRouterService engineRouterService;

	@Test
	void getRole_returnsPM() {
		PmRoleSkill skill = new PmRoleSkill(engineRouterService);

		assertEquals(PdlcRole.PM, skill.getRole());
	}

	@Test
	void getAiSdlcStepName_returnsRequirementsGathering() {
		PmRoleSkill skill = new PmRoleSkill(engineRouterService);

		assertEquals(AiSdlcStep.REQUIREMENTS_GATHERING.name(), skill.getAiSdlcStepName());
	}

	@Test
	void getAvailableTools_containsSearchAndBrowse() {
		PmRoleSkill skill = new PmRoleSkill(engineRouterService);

		assertTrue(skill.getAvailableTools().contains("search_web"));
		assertTrue(skill.getAvailableTools().contains("browse_web"));
		assertEquals(2, skill.getAvailableTools().size());
	}

	@Test
	void getSystemPrompt_containsRequirementsKeyword() {
		PmRoleSkill skill = new PmRoleSkill(engineRouterService);

		String prompt = skill.getSystemPrompt();

		assertNotNull(prompt);
		assertFalse(prompt.isBlank());
		assertTrue(prompt.toLowerCase().contains("requirements"),
				"System prompt should mention requirements");
	}

	@Test
	void getSystemPrompt_containsAcceptanceCriteria() {
		PmRoleSkill skill = new PmRoleSkill(engineRouterService);

		assertTrue(skill.getSystemPrompt().toLowerCase().contains("acceptance criteria"),
				"System prompt should mention acceptance criteria");
	}

	@Test
	void getSystemPrompt_containsScopeAndMetrics() {
		PmRoleSkill skill = new PmRoleSkill(engineRouterService);

		String prompt = skill.getSystemPrompt().toLowerCase();
		assertTrue(prompt.contains("scope"), "System prompt should mention scope");
		assertTrue(prompt.contains("success metrics") || prompt.contains("metrics"),
				"System prompt should mention success metrics");
	}

	@Test
	void getSuccessCriteria_isNotNull() {
		PmRoleSkill skill = new PmRoleSkill(engineRouterService);

		SuccessCriteria criteria = skill.getSuccessCriteria();

		assertNotNull(criteria);
		assertNotNull(criteria.description());
		assertNotNull(criteria.requiredArtifactType());
		assertEquals("REQUIREMENTS", criteria.requiredArtifactType());
	}

	@Test
	void getSystemPrompt_isSubstantial() {
		PmRoleSkill skill = new PmRoleSkill(engineRouterService);

		// System prompt should be substantial (10+ lines as specified in task)
		String prompt = skill.getSystemPrompt();
		long lineCount = prompt.lines().count();
		assertTrue(lineCount >= 10,
				"System prompt should be at least 10 lines, was " + lineCount);
	}

}
