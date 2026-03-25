package io.strategiz.social.business.agent.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlaybookRegistryTest {

	private PlaybookRegistry registry;

	@BeforeEach
	void setUp() {
		registry = new PlaybookRegistry();
		registry.registerDefaults();
	}

	@Test
	void allEightPlaybooksRegistered() {
		List<String> names = registry.getPlaybookNames();
		assertEquals(8, names.size());
		assertTrue(names.contains("FULL_PDLC"));
		assertTrue(names.contains("BUG_FIX"));
		assertTrue(names.contains("SMALL_FEATURE"));
		assertTrue(names.contains("REFACTOR"));
		assertTrue(names.contains("INFRA_CHANGE"));
		assertTrue(names.contains("DOCS_ONLY"));
		assertTrue(names.contains("UI_CHANGE"));
		assertTrue(names.contains("SECURITY_PATCH"));
	}

	@Test
	void fullPdlcHasElevenStages() {
		PlaybookConfig config = registry.getPlaybook("FULL_PDLC").orElseThrow();
		// Retro is a post-pipeline hook, not a stage — 11 stages total
		assertEquals(11, config.stages().size());
	}

	@Test
	void bugFixHasFourStages() {
		PlaybookConfig config = registry.getPlaybook("BUG_FIX").orElseThrow();
		assertEquals(4, config.stages().size());
	}

	@Test
	void unknownPlaybookReturnsEmpty() {
		Optional<PlaybookConfig> result = registry.getPlaybook("DOES_NOT_EXIST");
		assertTrue(result.isEmpty());
	}

	@Test
	void fullPdlcHasParallelGroupsDefined() {
		PlaybookConfig config = registry.getPlaybook("FULL_PDLC").orElseThrow();
		assertFalse(config.parallelGroups().isEmpty());

		// Tester and SecurityAnalyst should be in each other's parallel group
		List<PdlcRole> testerGroup = config.parallelGroups().get(PdlcRole.TESTER);
		assertNotNull(testerGroup);
		assertTrue(testerGroup.contains(PdlcRole.SECURITY_ANALYST));

		List<PdlcRole> securityGroup = config.parallelGroups().get(PdlcRole.SECURITY_ANALYST);
		assertNotNull(securityGroup);
		assertTrue(securityGroup.contains(PdlcRole.TESTER));

		// TechnicalWriter and DevOps should be in each other's parallel group
		List<PdlcRole> writerGroup = config.parallelGroups().get(PdlcRole.TECHNICAL_WRITER);
		assertNotNull(writerGroup);
		assertTrue(writerGroup.contains(PdlcRole.DEVOPS));

		List<PdlcRole> devopsGroup = config.parallelGroups().get(PdlcRole.DEVOPS);
		assertNotNull(devopsGroup);
		assertTrue(devopsGroup.contains(PdlcRole.TECHNICAL_WRITER));
	}

	@Test
	void eachPlaybookHasCorrectTier() {
		assertEquals(PipelineTier.FULL_PDLC, registry.getPlaybook("FULL_PDLC").orElseThrow().tier());

		assertEquals(PipelineTier.PLAYBOOK, registry.getPlaybook("BUG_FIX").orElseThrow().tier());
		assertEquals(PipelineTier.PLAYBOOK, registry.getPlaybook("SMALL_FEATURE").orElseThrow().tier());
		assertEquals(PipelineTier.PLAYBOOK, registry.getPlaybook("REFACTOR").orElseThrow().tier());
		assertEquals(PipelineTier.PLAYBOOK, registry.getPlaybook("INFRA_CHANGE").orElseThrow().tier());
		assertEquals(PipelineTier.PLAYBOOK, registry.getPlaybook("DOCS_ONLY").orElseThrow().tier());
		assertEquals(PipelineTier.PLAYBOOK, registry.getPlaybook("UI_CHANGE").orElseThrow().tier());
		assertEquals(PipelineTier.PLAYBOOK, registry.getPlaybook("SECURITY_PATCH").orElseThrow().tier());
	}

	@Test
	void allPlaybooksAreSystemPlaybooks() {
		for (PlaybookConfig config : registry.getAllPlaybooks()) {
			assertTrue(config.isSystemPlaybook(), config.name() + " should be a system playbook");
		}
	}

	@Test
	void fullPdlcHasDefaultCheckpointsDefined() {
		PlaybookConfig config = registry.getPlaybook("FULL_PDLC").orElseThrow();
		assertFalse(config.defaultCheckpoints().isEmpty());

		// After PM should be on
		PlaybookConfig.CheckpointRule pmRule = config.defaultCheckpoints().get(PdlcRole.PM);
		assertNotNull(pmRule);
		assertTrue(pmRule.afterRole());

		// After Architect should be on
		PlaybookConfig.CheckpointRule archRule = config.defaultCheckpoints().get(PdlcRole.ARCHITECT);
		assertNotNull(archRule);
		assertTrue(archRule.afterRole());

		// Security on rejection
		PlaybookConfig.CheckpointRule secRule = config.defaultCheckpoints().get(PdlcRole.SECURITY_ANALYST);
		assertNotNull(secRule);
		assertTrue(secRule.onRejection());

		// Before DevOps should be on
		PlaybookConfig.CheckpointRule devopsRule = config.defaultCheckpoints().get(PdlcRole.DEVOPS);
		assertNotNull(devopsRule);
		assertTrue(devopsRule.beforeRole());
	}

	@Test
	void getAllPlaybooksReturnsCopyOfAllRegistered() {
		List<PlaybookConfig> all = registry.getAllPlaybooks();
		assertEquals(8, all.size());
	}

	@Test
	void stageRolesMatchExpectedSequenceForBugFix() {
		PlaybookConfig config = registry.getPlaybook("BUG_FIX").orElseThrow();
		List<PdlcRole> roles = config.stages().stream().map(PlaybookStage::role).toList();
		assertEquals(List.of(
				PdlcRole.RESEARCHER,
				PdlcRole.IMPLEMENTER,
				PdlcRole.REVIEWER,
				PdlcRole.TESTER
		), roles);
	}

	@Test
	void securityPatchLeadsWithSecurityAnalyst() {
		PlaybookConfig config = registry.getPlaybook("SECURITY_PATCH").orElseThrow();
		assertEquals(PdlcRole.SECURITY_ANALYST, config.stages().get(0).role());
		assertEquals(5, config.stages().size());
	}

	@Test
	void stageTimeoutsAreNonNull() {
		for (PlaybookConfig config : registry.getAllPlaybooks()) {
			for (PlaybookStage stage : config.stages()) {
				assertNotNull(stage.timeout(),
						"Stage " + stage.role() + " in " + config.name() + " has null timeout");
				assertTrue(stage.timeout().toMinutes() > 0,
						"Stage " + stage.role() + " in " + config.name() + " has zero timeout");
			}
		}
	}

}
