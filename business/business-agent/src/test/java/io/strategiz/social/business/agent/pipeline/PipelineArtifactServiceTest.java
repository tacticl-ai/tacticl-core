package io.strategiz.social.business.agent.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineArtifact;
import io.strategiz.social.data.repository.PipelineArtifactRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PipelineArtifactServiceTest {

	@Mock
	private PipelineArtifactRepository artifactRepository;

	@InjectMocks
	private PipelineArtifactService artifactService;

	@Test
	void store_savesArtifactAndReturnsId() {
		PipelineArtifact savedArtifact = new PipelineArtifact();
		savedArtifact.setId("artifact-123");
		when(artifactRepository.save(any(PipelineArtifact.class))).thenReturn(savedArtifact);

		String artifactId = artifactService.store(
			"pipeline-run-1", PdlcRole.PM, "spark-abc",
			"requirements", Map.of("summary", "Build a login feature"));

		assertNotNull(artifactId);
		assertFalse(artifactId.isBlank());

		ArgumentCaptor<PipelineArtifact> captor = ArgumentCaptor.forClass(PipelineArtifact.class);
		verify(artifactRepository).save(captor.capture());

		PipelineArtifact captured = captor.getValue();
		assertEquals("pipeline-run-1", captured.getPipelineRunId());
		assertEquals(PdlcRole.PM, captured.getRole());
		assertEquals("spark-abc", captured.getSparkId());
		assertEquals("requirements", captured.getArtifactType());
		assertNotNull(captured.getCreatedAt());
	}

	@Test
	void getArtifact_returnsArtifactById() {
		PipelineArtifact artifact = new PipelineArtifact();
		artifact.setId("artifact-123");
		artifact.setRole(PdlcRole.ARCHITECT);
		when(artifactRepository.findById("artifact-123")).thenReturn(Optional.of(artifact));

		Optional<PipelineArtifact> result = artifactService.getArtifact("artifact-123");

		assertTrue(result.isPresent());
		assertEquals("artifact-123", result.get().getId());
		assertEquals(PdlcRole.ARCHITECT, result.get().getRole());
	}

	@Test
	void getArtifactsForPipeline_returnsAllArtifactsForRun() {
		PipelineArtifact a1 = new PipelineArtifact();
		a1.setRole(PdlcRole.PM);
		PipelineArtifact a2 = new PipelineArtifact();
		a2.setRole(PdlcRole.ARCHITECT);
		when(artifactRepository.findByPipelineRunId("run-1")).thenReturn(List.of(a1, a2));

		List<PipelineArtifact> results = artifactService.getArtifactsForPipeline("run-1");

		assertEquals(2, results.size());
	}

	@Test
	void getUpstreamArtifacts_returnsOnlyArtifactsBeforeCurrentRole() {
		List<PdlcRole> stageOrder = List.of(PdlcRole.PM, PdlcRole.ARCHITECT, PdlcRole.IMPLEMENTER, PdlcRole.REVIEWER);

		PipelineArtifact pmArtifact = new PipelineArtifact();
		pmArtifact.setRole(PdlcRole.PM);

		PipelineArtifact architectArtifact = new PipelineArtifact();
		architectArtifact.setRole(PdlcRole.ARCHITECT);

		PipelineArtifact engineerArtifact = new PipelineArtifact();
		engineerArtifact.setRole(PdlcRole.IMPLEMENTER);

		when(artifactRepository.findByPipelineRunId("run-1"))
			.thenReturn(List.of(pmArtifact, architectArtifact, engineerArtifact));

		Map<PdlcRole, PipelineArtifact> upstream =
			artifactService.getUpstreamArtifacts("run-1", PdlcRole.REVIEWER, stageOrder);

		assertEquals(3, upstream.size());
		assertTrue(upstream.containsKey(PdlcRole.PM));
		assertTrue(upstream.containsKey(PdlcRole.ARCHITECT));
		assertTrue(upstream.containsKey(PdlcRole.IMPLEMENTER));
		assertFalse(upstream.containsKey(PdlcRole.REVIEWER));
	}

	@Test
	void getUpstreamArtifacts_returnsEmptyMapForFirstRole() {
		List<PdlcRole> stageOrder = List.of(PdlcRole.PM, PdlcRole.ARCHITECT, PdlcRole.IMPLEMENTER);

		Map<PdlcRole, PipelineArtifact> upstream =
			artifactService.getUpstreamArtifacts("run-1", PdlcRole.PM, stageOrder);

		assertTrue(upstream.isEmpty());
	}

	@Test
	void buildArtifactSummary_formatsTextCorrectly() {
		PipelineArtifact pmArtifact = new PipelineArtifact();
		pmArtifact.setRole(PdlcRole.PM);
		pmArtifact.setArtifactType("requirements");
		pmArtifact.setContent(Map.of("summary", "User must be able to log in with email and password."));

		PipelineArtifact architectArtifact = new PipelineArtifact();
		architectArtifact.setRole(PdlcRole.ARCHITECT);
		architectArtifact.setArtifactType("design");
		architectArtifact.setContent(Map.of("summary", "JWT auth with refresh tokens stored in HttpOnly cookies."));

		Map<PdlcRole, PipelineArtifact> artifacts = new java.util.LinkedHashMap<>();
		artifacts.put(PdlcRole.PM, pmArtifact);
		artifacts.put(PdlcRole.ARCHITECT, architectArtifact);

		String summary = artifactService.buildArtifactSummary(artifacts);

		assertTrue(summary.contains("## Previous Role Outputs"));
		assertTrue(summary.contains("### PM (Requirements)"));
		assertTrue(summary.contains("User must be able to log in with email and password."));
		assertTrue(summary.contains("### ARCHITECT (Design)"));
		assertTrue(summary.contains("JWT auth with refresh tokens stored in HttpOnly cookies."));
	}

	@Test
	void buildArtifactSummary_returnsEmptyStringForEmptyArtifacts() {
		String summary = artifactService.buildArtifactSummary(Map.of());

		assertEquals("", summary);
	}

}
