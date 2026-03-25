package io.strategiz.social.business.agent.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.strategiz.social.data.entity.Checkpoint;
import io.strategiz.social.data.entity.CheckpointDecision;
import io.strategiz.social.data.entity.CheckpointType;
import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineCheckpointConfig;
import io.strategiz.social.data.entity.PipelineEventType;
import io.strategiz.social.data.entity.PipelineRun;
import io.strategiz.social.data.entity.PipelineStatus;
import io.strategiz.social.data.entity.UserConfig;
import io.strategiz.social.data.repository.CheckpointRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CheckpointServiceTest {

	@Mock
	private CheckpointRepository checkpointRepository;

	@Mock
	private PipelineEventEmitter pipelineEventEmitter;

	@Mock
	private PipelineStateManager pipelineStateManager;

	private CheckpointService checkpointService;

	@BeforeEach
	void setUp() {
		checkpointService = new CheckpointService(checkpointRepository, pipelineEventEmitter,
				pipelineStateManager);
	}

	// --- helpers ---

	private PipelineRun run(String id) {
		PipelineRun run = new PipelineRun();
		run.setId(id);
		run.setSparkId("spark-" + id);
		run.setUserId("user-001");
		run.setStatus(PipelineStatus.EXECUTING);
		return run;
	}

	private Checkpoint resolvedCheckpoint(String id, String pipelineRunId, CheckpointDecision decision) {
		Checkpoint cp = new Checkpoint();
		cp.setId(id);
		cp.setSparkId("spark-run-001");
		cp.setPipelineRunId(pipelineRunId);
		cp.setPdlcRole(PdlcRole.PM);
		cp.setCheckpointType(CheckpointType.PIPELINE_STAGE);
		cp.setUserDecision(decision);
		return cp;
	}

	// --- createPipelineCheckpoint ---

	@Test
	void createPipelineCheckpoint_savesEntityWithAllFields() {
		PipelineRun run = run("run-001");

		Checkpoint result = checkpointService.createPipelineCheckpoint(
				run, PdlcRole.ARCHITECT, CheckpointType.PIPELINE_STAGE,
				"Review architecture", "Please review before coding begins.",
				List.of("APPROVED", "REJECTED"));

		ArgumentCaptor<Checkpoint> captor = forClass(Checkpoint.class);
		verify(checkpointRepository).save(captor.capture());
		Checkpoint saved = captor.getValue();

		assertNotNull(saved.getId());
		assertEquals("run-001", saved.getPipelineRunId());
		assertEquals("spark-run-001", saved.getSparkId());
		assertEquals(PdlcRole.ARCHITECT, saved.getPdlcRole());
		assertEquals(CheckpointType.PIPELINE_STAGE, saved.getCheckpointType());
		assertEquals("Review architecture", saved.getTitle());
		assertEquals(2, saved.getOptions().size());
		// Returned object is the same instance
		assertEquals(saved.getId(), result.getId());
	}

	@Test
	void createPipelineCheckpoint_marksRunAsCheckpoint() {
		PipelineRun run = run("run-002");

		checkpointService.createPipelineCheckpoint(run, PdlcRole.PM, CheckpointType.PIPELINE_STAGE,
				"Review requirements", "Sign off before implementation.", List.of("APPROVED"));

		verify(pipelineStateManager).markCheckpoint("run-002");
	}

	@Test
	void createPipelineCheckpoint_emitsCheckpointRequestedEvent() {
		PipelineRun run = run("run-003");

		checkpointService.createPipelineCheckpoint(run, PdlcRole.DEVOPS, CheckpointType.PIPELINE_STAGE,
				"Approve deploy", "Confirm before deployment.", List.of("APPROVED", "REJECTED"));

		verify(pipelineEventEmitter).emitEvent(
				eq(run),
				eq(PipelineEventType.CHECKPOINT_REQUESTED),
				eq(PdlcRole.DEVOPS),
				any());
	}

	// --- resolveCheckpoint ---

	@Test
	void resolveCheckpoint_updatesDecisionAndTimestamp() {
		PipelineRun run = run("run-004");
		Checkpoint cp = new Checkpoint();
		cp.setId("cp-001");
		cp.setSparkId("spark-run-004");
		cp.setPipelineRunId("run-004");
		cp.setPdlcRole(PdlcRole.REVIEWER);

		when(checkpointRepository.findById("cp-001")).thenReturn(Optional.of(cp));
		when(pipelineStateManager.getRun("run-004")).thenReturn(Optional.of(run));

		checkpointService.resolveCheckpoint("cp-001", "user-001", "APPROVED", "Looks good");

		ArgumentCaptor<Checkpoint> captor = forClass(Checkpoint.class);
		verify(checkpointRepository).save(captor.capture());
		Checkpoint saved = captor.getValue();

		assertEquals(CheckpointDecision.APPROVED, saved.getUserDecision());
		assertEquals("Looks good", saved.getUserFeedback());
		assertNotNull(saved.getDecidedAt());
	}

	@Test
	void resolveCheckpoint_marksRunAsExecuting() {
		PipelineRun run = run("run-005");
		Checkpoint cp = new Checkpoint();
		cp.setId("cp-002");
		cp.setSparkId("spark-run-005");
		cp.setPipelineRunId("run-005");

		when(checkpointRepository.findById("cp-002")).thenReturn(Optional.of(cp));
		when(pipelineStateManager.getRun("run-005")).thenReturn(Optional.of(run));

		checkpointService.resolveCheckpoint("cp-002", "user-001", "REJECTED", null);

		verify(pipelineStateManager).markExecuting("run-005");
	}

	@Test
	void resolveCheckpoint_emitsCheckpointResolvedEvent() {
		PipelineRun run = run("run-006");
		Checkpoint cp = new Checkpoint();
		cp.setId("cp-003");
		cp.setSparkId("spark-run-006");
		cp.setPipelineRunId("run-006");
		cp.setPdlcRole(PdlcRole.TESTER);

		when(checkpointRepository.findById("cp-003")).thenReturn(Optional.of(cp));
		when(pipelineStateManager.getRun("run-006")).thenReturn(Optional.of(run));

		checkpointService.resolveCheckpoint("cp-003", "user-001", "MODIFIED", "Please rework tests");

		verify(pipelineEventEmitter).emitEvent(
				eq(run),
				eq(PipelineEventType.CHECKPOINT_RESOLVED),
				eq(PdlcRole.TESTER),
				any());
	}

	@Test
	void resolveCheckpoint_throwsWhenNotFound() {
		when(checkpointRepository.findById("missing")).thenReturn(Optional.empty());

		assertThrows(IllegalArgumentException.class,
				() -> checkpointService.resolveCheckpoint("missing", "user-001", "APPROVED", null));
	}

	@Test
	void resolveCheckpoint_throwsWhenAlreadyResolved() {
		Checkpoint cp = resolvedCheckpoint("cp-004", "run-007", CheckpointDecision.APPROVED);
		when(checkpointRepository.findById("cp-004")).thenReturn(Optional.of(cp));

		assertThrows(IllegalStateException.class,
				() -> checkpointService.resolveCheckpoint("cp-004", "user-001", "REJECTED", null));
	}

	// --- shouldCheckpoint ---

	@Test
	void shouldCheckpoint_returnsFalseWhenUserConfigIsNull() {
		PipelineRun run = run("run-008");
		// No playbook default, no user config — should return false
		assertFalse(checkpointService.shouldCheckpoint(run, PdlcRole.IMPLEMENTER, true, null));
	}

	@Test
	void shouldCheckpoint_returnsFalseWhenAutoApproveAllIsSet() {
		PipelineRun run = run("run-009");
		UserConfig config = new UserConfig();
		PipelineCheckpointConfig cpConfig = new PipelineCheckpointConfig();
		cpConfig.setAutoApproveAll(true);
		config.setPipelineCheckpoints(cpConfig);

		assertFalse(checkpointService.shouldCheckpoint(run, PdlcRole.PM, false, config));
		assertFalse(checkpointService.shouldCheckpoint(run, PdlcRole.ARCHITECT, true, config));
	}

	@Test
	void shouldCheckpoint_honorsPmApproveRequirementsFlagAfterRole() {
		PipelineRun run = run("run-010");
		UserConfig config = new UserConfig();
		PipelineCheckpointConfig cpConfig = new PipelineCheckpointConfig();
		cpConfig.setApproveRequirements(true);
		config.setPipelineCheckpoints(cpConfig);

		// after PM role (beforeRole=false) — should require checkpoint
		assertTrue(checkpointService.shouldCheckpoint(run, PdlcRole.PM, false, config));
		// before PM role (beforeRole=true) — should NOT trigger on this flag
		assertFalse(checkpointService.shouldCheckpoint(run, PdlcRole.PM, true, config));
	}

	@Test
	void shouldCheckpoint_honorsArchitectApproveArchitectureFlagAfterRole() {
		PipelineRun run = run("run-011");
		UserConfig config = new UserConfig();
		PipelineCheckpointConfig cpConfig = new PipelineCheckpointConfig();
		cpConfig.setApproveArchitecture(true);
		config.setPipelineCheckpoints(cpConfig);

		assertTrue(checkpointService.shouldCheckpoint(run, PdlcRole.ARCHITECT, false, config));
		assertFalse(checkpointService.shouldCheckpoint(run, PdlcRole.ARCHITECT, true, config));
	}

	@Test
	void shouldCheckpoint_honorsDevopsApproveBeforeDeployFlagBeforeRole() {
		PipelineRun run = run("run-012");
		UserConfig config = new UserConfig();
		PipelineCheckpointConfig cpConfig = new PipelineCheckpointConfig();
		cpConfig.setApproveBeforeDeploy(true);
		config.setPipelineCheckpoints(cpConfig);

		// before DEVOPS (beforeRole=true) — should require checkpoint
		assertTrue(checkpointService.shouldCheckpoint(run, PdlcRole.DEVOPS, true, config));
		// after DEVOPS (beforeRole=false) — should NOT trigger on this flag
		assertFalse(checkpointService.shouldCheckpoint(run, PdlcRole.DEVOPS, false, config));
	}

	@Test
	void shouldCheckpoint_perRoleOverrideTakesPrecedenceOverConvenienceFlags() {
		PipelineRun run = run("run-013");
		UserConfig config = new UserConfig();
		PipelineCheckpointConfig cpConfig = new PipelineCheckpointConfig();
		// Convenience flag says checkpoint after PM...
		cpConfig.setApproveRequirements(true);
		// ...but per-role override turns it off
		PipelineCheckpointConfig.CheckpointRule override = new PipelineCheckpointConfig.CheckpointRule();
		override.setBeforeRole(false);
		override.setAfterRole(false);
		cpConfig.getRoleCheckpoints().put(PdlcRole.PM.name(), override);
		config.setPipelineCheckpoints(cpConfig);

		// Override says no checkpoint after PM, should return false despite convenience flag
		assertFalse(checkpointService.shouldCheckpoint(run, PdlcRole.PM, false, config));
	}

	@Test
	void shouldCheckpoint_perRoleOverrideCanEnableBeforeAndAfter() {
		PipelineRun run = run("run-014");
		UserConfig config = new UserConfig();
		PipelineCheckpointConfig cpConfig = new PipelineCheckpointConfig();
		PipelineCheckpointConfig.CheckpointRule override = new PipelineCheckpointConfig.CheckpointRule();
		override.setBeforeRole(true);
		override.setAfterRole(true);
		cpConfig.getRoleCheckpoints().put(PdlcRole.IMPLEMENTER.name(), override);
		config.setPipelineCheckpoints(cpConfig);

		assertTrue(checkpointService.shouldCheckpoint(run, PdlcRole.IMPLEMENTER, true, config));
		assertTrue(checkpointService.shouldCheckpoint(run, PdlcRole.IMPLEMENTER, false, config));
	}

	// --- getPendingCheckpoint ---

	@Test
	void getPendingCheckpoint_returnsEmptyWhenAllResolved() {
		Checkpoint resolved = resolvedCheckpoint("cp-005", "run-015", CheckpointDecision.APPROVED);
		when(checkpointRepository.findByPipelineRunId("run-015")).thenReturn(List.of(resolved));

		Optional<Checkpoint> result = checkpointService.getPendingCheckpoint("run-015");

		assertTrue(result.isEmpty());
	}

	@Test
	void getPendingCheckpoint_returnsPendingCheckpointWhenPresent() {
		Checkpoint pending = new Checkpoint();
		pending.setId("cp-006");
		pending.setPipelineRunId("run-016");
		// userDecision is null — not yet resolved

		when(checkpointRepository.findByPipelineRunId("run-016")).thenReturn(List.of(pending));

		Optional<Checkpoint> result = checkpointService.getPendingCheckpoint("run-016");

		assertTrue(result.isPresent());
		assertEquals("cp-006", result.get().getId());
	}

	@Test
	void getPendingCheckpoint_returnsEmptyWhenNoneExist() {
		when(checkpointRepository.findByPipelineRunId("run-017")).thenReturn(List.of());

		Optional<Checkpoint> result = checkpointService.getPendingCheckpoint("run-017");

		assertTrue(result.isEmpty());
		verify(checkpointRepository, never()).save(any());
	}

}
