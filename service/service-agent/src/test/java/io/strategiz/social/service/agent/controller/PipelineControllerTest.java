package io.strategiz.social.service.agent.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.strategiz.social.business.agent.pipeline.CheckpointService;
import io.strategiz.social.business.agent.pipeline.PipelineArtifactService;
import io.strategiz.social.business.agent.pipeline.PlaybookConfig;
import io.strategiz.social.business.agent.pipeline.PlaybookRegistry;
import io.strategiz.social.business.agent.pipeline.PlaybookStage;
import io.strategiz.social.data.entity.Checkpoint;
import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineArtifact;
import io.strategiz.social.data.entity.PipelineEvent;
import io.strategiz.social.data.entity.PipelineEventType;
import io.strategiz.social.data.entity.PipelineRun;
import io.strategiz.social.data.entity.PipelineStatus;
import io.strategiz.social.data.entity.PipelineTier;
import io.strategiz.social.data.entity.Spark;
import io.strategiz.social.data.repository.PipelineEventRepository;
import io.strategiz.social.data.repository.PipelineRunRepository;
import io.strategiz.social.data.repository.SparkRepository;
import io.strategiz.social.service.agent.dto.CheckpointResolutionRequest;
import io.strategiz.social.service.agent.dto.PipelineEventResponse;
import io.strategiz.social.service.agent.dto.PipelineRunResponse;
import io.strategiz.social.service.agent.dto.PlaybookResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class PipelineControllerTest {

	private static final String USER_ID = "user-123";

	private static final String SPARK_ID = "spark-abc";

	private static final String RUN_ID = "run-xyz";

	@Mock
	private PipelineRunRepository pipelineRunRepository;

	@Mock
	private PipelineEventRepository pipelineEventRepository;

	@Mock
	private PipelineArtifactService pipelineArtifactService;

	@Mock
	private PlaybookRegistry playbookRegistry;

	@Mock
	private CheckpointService checkpointService;

	@Mock
	private SparkRepository sparkRepository;

	@InjectMocks
	private PipelineController controller;

	// --- helpers ---

	private AuthenticatedUser auth(String userId) {
		return AuthenticatedUser.builder().userId(userId).build();
	}

	private PipelineRun buildRun() {
		PipelineRun run = new PipelineRun();
		run.setId(RUN_ID);
		run.setSparkId(SPARK_ID);
		run.setUserId(USER_ID);
		run.setPlaybook("BUG_FIX");
		run.setPipelineTier(PipelineTier.PLAYBOOK);
		run.setStatus(PipelineStatus.EXECUTING);
		run.setActivatedRoles(List.of(PdlcRole.RESEARCHER, PdlcRole.IMPLEMENTER));
		run.setCurrentRole(PdlcRole.RESEARCHER);
		run.setStartedAt(Instant.now());
		return run;
	}

	private Spark buildSpark(String userId) {
		Spark spark = new Spark();
		spark.setId(SPARK_ID);
		spark.setUserId(userId);
		return spark;
	}

	// --- GET /v1/sparks/{sparkId}/pipeline ---

	@Test
	void getPipeline_found_returnsRunResponse() {
		PipelineRun run = buildRun();
		when(pipelineRunRepository.findBySparkId(SPARK_ID)).thenReturn(Optional.of(run));

		ResponseEntity<PipelineRunResponse> response = controller.getPipeline(SPARK_ID, auth(USER_ID));

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(RUN_ID, response.getBody().getId());
		assertEquals(SPARK_ID, response.getBody().getSparkId());
		assertEquals("BUG_FIX", response.getBody().getPlaybook());
		assertEquals(PipelineStatus.EXECUTING, response.getBody().getStatus());
		assertEquals(PdlcRole.RESEARCHER, response.getBody().getCurrentRole());
	}

	@Test
	void getPipeline_notFound_returns404() {
		when(pipelineRunRepository.findBySparkId(SPARK_ID)).thenReturn(Optional.empty());

		ResponseEntity<PipelineRunResponse> response = controller.getPipeline(SPARK_ID, auth(USER_ID));

		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
	}

	@Test
	void getPipeline_wrongUser_returns403() {
		PipelineRun run = buildRun();
		when(pipelineRunRepository.findBySparkId(SPARK_ID)).thenReturn(Optional.of(run));

		ResponseEntity<PipelineRunResponse> response = controller.getPipeline(SPARK_ID, auth("other-user"));

		assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
	}

	// --- GET /v1/sparks/{sparkId}/pipeline/events ---

	@Test
	void getPipelineEvents_found_returnsEvents() {
		PipelineRun run = buildRun();
		when(pipelineRunRepository.findBySparkId(SPARK_ID)).thenReturn(Optional.of(run));

		PipelineEvent event = new PipelineEvent();
		event.setId("event-1");
		event.setEventType(PipelineEventType.ROLE_STARTED);
		event.setRole(PdlcRole.RESEARCHER);
		event.setTimestamp(Instant.now());
		when(pipelineEventRepository.findByPipelineRunId(RUN_ID)).thenReturn(List.of(event));

		ResponseEntity<List<PipelineEventResponse>> response = controller.getPipelineEvents(SPARK_ID, null, null,
				auth(USER_ID));

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(1, response.getBody().size());
		assertEquals("event-1", response.getBody().get(0).getId());
		assertEquals(PipelineEventType.ROLE_STARTED, response.getBody().get(0).getEventType());
		assertEquals(PdlcRole.RESEARCHER, response.getBody().get(0).getRole());
	}

	@Test
	void getPipelineEvents_notFound_returns404() {
		when(pipelineRunRepository.findBySparkId(SPARK_ID)).thenReturn(Optional.empty());

		ResponseEntity<List<PipelineEventResponse>> response = controller.getPipelineEvents(SPARK_ID, null, null,
				auth(USER_ID));

		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
	}

	@Test
	void getPipelineEvents_wrongUser_returns403() {
		PipelineRun run = buildRun();
		when(pipelineRunRepository.findBySparkId(SPARK_ID)).thenReturn(Optional.of(run));

		ResponseEntity<List<PipelineEventResponse>> response = controller.getPipelineEvents(SPARK_ID, null, null,
				auth("other-user"));

		assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
	}

	@Test
	void getPipelineEvents_withPagination_returnsPage() {
		PipelineRun run = buildRun();
		when(pipelineRunRepository.findBySparkId(SPARK_ID)).thenReturn(Optional.of(run));

		PipelineEvent e1 = new PipelineEvent();
		e1.setId("event-1");
		PipelineEvent e2 = new PipelineEvent();
		e2.setId("event-2");
		PipelineEvent e3 = new PipelineEvent();
		e3.setId("event-3");
		when(pipelineEventRepository.findByPipelineRunId(RUN_ID)).thenReturn(List.of(e1, e2, e3));

		ResponseEntity<List<PipelineEventResponse>> response = controller.getPipelineEvents(SPARK_ID, 2, 1,
				auth(USER_ID));

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		// offset=1, limit=2 → [e2, e3]
		assertEquals(2, response.getBody().size());
		assertEquals("event-2", response.getBody().get(0).getId());
		assertEquals("event-3", response.getBody().get(1).getId());
	}

	// --- GET /v1/sparks/{sparkId}/pipeline/artifacts/{role} ---

	@Test
	void getArtifact_found_returnsContent() {
		PipelineRun run = buildRun();
		when(pipelineRunRepository.findBySparkId(SPARK_ID)).thenReturn(Optional.of(run));

		PipelineArtifact artifact = new PipelineArtifact();
		artifact.setId("artifact-1");
		artifact.setRole(PdlcRole.RESEARCHER);
		artifact.setContent(Map.of("summary", "Research findings here"));
		when(pipelineArtifactService.getArtifactForRole(RUN_ID, PdlcRole.RESEARCHER))
			.thenReturn(Optional.of(artifact));

		ResponseEntity<Map<String, Object>> response = controller.getArtifact(SPARK_ID, "RESEARCHER", auth(USER_ID));

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals("Research findings here", response.getBody().get("summary"));
	}

	@Test
	void getArtifact_notFound_returns404() {
		when(pipelineRunRepository.findBySparkId(SPARK_ID)).thenReturn(Optional.empty());

		ResponseEntity<Map<String, Object>> response = controller.getArtifact(SPARK_ID, "RESEARCHER", auth(USER_ID));

		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
	}

	@Test
	void getArtifact_artifactNotYetProduced_returns404() {
		PipelineRun run = buildRun();
		when(pipelineRunRepository.findBySparkId(SPARK_ID)).thenReturn(Optional.of(run));
		when(pipelineArtifactService.getArtifactForRole(RUN_ID, PdlcRole.IMPLEMENTER)).thenReturn(Optional.empty());

		ResponseEntity<Map<String, Object>> response = controller.getArtifact(SPARK_ID, "IMPLEMENTER", auth(USER_ID));

		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
	}

	@Test
	void getArtifact_wrongUser_returns403() {
		PipelineRun run = buildRun();
		when(pipelineRunRepository.findBySparkId(SPARK_ID)).thenReturn(Optional.of(run));

		ResponseEntity<Map<String, Object>> response = controller.getArtifact(SPARK_ID, "RESEARCHER", auth("other-user"));

		assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
	}

	@Test
	void getArtifact_invalidRole_returns400() {
		PipelineRun run = buildRun();
		when(pipelineRunRepository.findBySparkId(SPARK_ID)).thenReturn(Optional.of(run));

		ResponseEntity<Map<String, Object>> response = controller.getArtifact(SPARK_ID, "INVALID_ROLE", auth(USER_ID));

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
	}

	// --- POST /v1/sparks/{sparkId}/pipeline/checkpoint/{checkpointId} ---

	@Test
	void resolveCheckpoint_accepted_returns202() {
		Checkpoint checkpoint = new Checkpoint();
		checkpoint.setId("ckpt-1");
		checkpoint.setSparkId(SPARK_ID);
		checkpoint.setPipelineRunId(null);
		when(checkpointService.getCheckpoint("ckpt-1")).thenReturn(Optional.of(checkpoint));
		when(sparkRepository.findById(SPARK_ID)).thenReturn(Optional.of(buildSpark(USER_ID)));
		doNothing().when(checkpointService).resolveCheckpoint("ckpt-1", USER_ID, "APPROVED", null);

		CheckpointResolutionRequest request = new CheckpointResolutionRequest();
		request.setDecision("APPROVED");

		ResponseEntity<Void> response = controller.resolveCheckpoint(SPARK_ID, "ckpt-1", request, auth(USER_ID));

		assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
	}

	@Test
	void resolveCheckpoint_withFeedback_returns202() {
		Checkpoint checkpoint = new Checkpoint();
		checkpoint.setId("ckpt-1");
		checkpoint.setSparkId(SPARK_ID);
		checkpoint.setPipelineRunId(null);
		when(checkpointService.getCheckpoint("ckpt-1")).thenReturn(Optional.of(checkpoint));
		when(sparkRepository.findById(SPARK_ID)).thenReturn(Optional.of(buildSpark(USER_ID)));
		doNothing().when(checkpointService).resolveCheckpoint("ckpt-1", USER_ID, "MODIFIED",
				"Please revise the architecture section");

		CheckpointResolutionRequest request = new CheckpointResolutionRequest();
		request.setDecision("MODIFIED");
		request.setFeedback("Please revise the architecture section");

		ResponseEntity<Void> response = controller.resolveCheckpoint(SPARK_ID, "ckpt-1", request, auth(USER_ID));

		assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
	}

	@Test
	void resolveCheckpoint_wrongUser_returns403() {
		// Checkpoint has a pipeline run owned by USER_ID; attacker authenticates as "other-user"
		Checkpoint checkpoint = new Checkpoint();
		checkpoint.setId("ckpt-1");
		checkpoint.setSparkId(SPARK_ID);
		checkpoint.setPipelineRunId(RUN_ID);
		when(checkpointService.getCheckpoint("ckpt-1")).thenReturn(Optional.of(checkpoint));

		PipelineRun run = buildRun(); // userId = USER_ID
		when(pipelineRunRepository.findById(RUN_ID)).thenReturn(Optional.of(run));

		CheckpointResolutionRequest request = new CheckpointResolutionRequest();
		request.setDecision("APPROVED");

		ResponseEntity<Void> response = controller.resolveCheckpoint(SPARK_ID, "ckpt-1", request, auth("other-user"));

		assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
	}

	@Test
	void resolveCheckpoint_wrongUserViaSparkFallback_returns403() {
		// Checkpoint has no pipeline run; attacker tries to resolve another user's checkpoint
		Checkpoint checkpoint = new Checkpoint();
		checkpoint.setId("ckpt-1");
		checkpoint.setSparkId(SPARK_ID);
		checkpoint.setPipelineRunId(null);
		when(checkpointService.getCheckpoint("ckpt-1")).thenReturn(Optional.of(checkpoint));
		// Spark is owned by USER_ID; attacker is "other-user"
		when(sparkRepository.findById(SPARK_ID)).thenReturn(Optional.of(buildSpark(USER_ID)));

		CheckpointResolutionRequest request = new CheckpointResolutionRequest();
		request.setDecision("APPROVED");

		ResponseEntity<Void> response = controller.resolveCheckpoint(SPARK_ID, "ckpt-1", request, auth("other-user"));

		assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
	}

	@Test
	void resolveCheckpoint_checkpointNotFound_returns404() {
		when(checkpointService.getCheckpoint("ckpt-missing")).thenReturn(Optional.empty());

		CheckpointResolutionRequest request = new CheckpointResolutionRequest();
		request.setDecision("APPROVED");

		ResponseEntity<Void> response = controller.resolveCheckpoint(SPARK_ID, "ckpt-missing", request, auth(USER_ID));

		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
	}

	@Test
	void resolveCheckpoint_alreadyResolved_returns409() {
		Checkpoint checkpoint = new Checkpoint();
		checkpoint.setId("ckpt-1");
		checkpoint.setSparkId(SPARK_ID);
		checkpoint.setPipelineRunId(null);
		when(checkpointService.getCheckpoint("ckpt-1")).thenReturn(Optional.of(checkpoint));
		when(sparkRepository.findById(SPARK_ID)).thenReturn(Optional.of(buildSpark(USER_ID)));
		org.mockito.Mockito.doThrow(new IllegalStateException("Checkpoint already resolved"))
				.when(checkpointService).resolveCheckpoint("ckpt-1", USER_ID, "APPROVED", null);

		CheckpointResolutionRequest request = new CheckpointResolutionRequest();
		request.setDecision("APPROVED");

		ResponseEntity<Void> response = controller.resolveCheckpoint(SPARK_ID, "ckpt-1", request, auth(USER_ID));

		assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
	}

	// --- GET /v1/playbooks ---

	@Test
	void getPlaybooks_returnsAllPlaybooks() {
		PlaybookStage stage1 = new PlaybookStage(PdlcRole.RESEARCHER, true, List.of(), List.of(),
				Duration.ofMinutes(15));
		PlaybookStage stage2 = new PlaybookStage(PdlcRole.IMPLEMENTER, true, List.of(PdlcRole.RESEARCHER),
				List.of(PdlcRole.RESEARCHER), Duration.ofMinutes(45));

		PlaybookConfig config = new PlaybookConfig(
				"BUG_FIX",
				"Bug Fix",
				"Known bug requiring diagnosis and a targeted fix.",
				PipelineTier.PLAYBOOK,
				List.of(stage1, stage2),
				Map.of(),
				Map.of(),
				true
		);

		when(playbookRegistry.getAllPlaybooks()).thenReturn(List.of(config));

		ResponseEntity<List<PlaybookResponse>> response = controller.getPlaybooks(auth(USER_ID));

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(1, response.getBody().size());

		PlaybookResponse pb = response.getBody().get(0);
		assertEquals("BUG_FIX", pb.getName());
		assertEquals("Bug Fix", pb.getDisplayName());
		assertEquals(PipelineTier.PLAYBOOK, pb.getTier());
		assertEquals(List.of("RESEARCHER", "IMPLEMENTER"), pb.getStages());
		assertEquals(true, pb.isSystemPlaybook());
	}

	@Test
	void getPlaybooks_emptyRegistry_returnsEmptyList() {
		when(playbookRegistry.getAllPlaybooks()).thenReturn(List.of());

		ResponseEntity<List<PlaybookResponse>> response = controller.getPlaybooks(auth(USER_ID));

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(0, response.getBody().size());
	}

}
