package io.strategiz.social.business.agent.pipeline;

import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineArtifact;
import io.strategiz.social.data.repository.PipelineArtifactRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Manages storing and retrieving artifacts produced by each PDLC pipeline role.
 * Provides upstream artifact access so each role can build on prior role outputs.
 */
@Service
public class PipelineArtifactService {

	private static final Logger log = LoggerFactory.getLogger(PipelineArtifactService.class);

	private final PipelineArtifactRepository artifactRepository;

	public PipelineArtifactService(PipelineArtifactRepository artifactRepository) {
		this.artifactRepository = artifactRepository;
	}

	/**
	 * Store an artifact produced by a pipeline role.
	 * @param pipelineRunId The pipeline run identifier
	 * @param role The role that produced this artifact
	 * @param sparkId The spark this pipeline is running for
	 * @param artifactType Describes the type of artifact (e.g. "requirements", "design")
	 * @param content The artifact content as a map
	 * @return The generated artifact ID
	 */
	public String store(String pipelineRunId, PdlcRole role, String sparkId,
			String artifactType, Map<String, Object> content) {
		String artifactId = UUID.randomUUID().toString();

		PipelineArtifact artifact = new PipelineArtifact();
		artifact.setId(artifactId);
		artifact.setPipelineRunId(pipelineRunId);
		artifact.setRole(role);
		artifact.setSparkId(sparkId);
		artifact.setArtifactType(artifactType);
		artifact.setContent(content);
		artifact.setCreatedAt(Instant.now());

		artifactRepository.save(artifact);

		log.debug("Stored artifact {} for pipeline {} role {}", artifactId, pipelineRunId, role);
		return artifactId;
	}

	/**
	 * Retrieve a single artifact by its ID.
	 * @param artifactId The artifact ID
	 * @return The artifact, or empty if not found
	 */
	public Optional<PipelineArtifact> getArtifact(String artifactId) {
		return artifactRepository.findById(artifactId);
	}

	/**
	 * Retrieve all artifacts produced in a pipeline run.
	 * @param pipelineRunId The pipeline run identifier
	 * @return All artifacts for this pipeline run
	 */
	public List<PipelineArtifact> getArtifactsForPipeline(String pipelineRunId) {
		return artifactRepository.findByPipelineRunId(pipelineRunId);
	}

	/**
	 * Retrieve the artifact produced by a specific role in a pipeline run.
	 * @param pipelineRunId The pipeline run identifier
	 * @param role The role whose artifact to retrieve
	 * @return The artifact for the given role, or empty if not yet produced
	 */
	public Optional<PipelineArtifact> getArtifactForRole(String pipelineRunId, PdlcRole role) {
		return artifactRepository.findByPipelineRunIdAndRole(pipelineRunId, role);
	}

	/**
	 * Retrieve all artifacts from roles that precede the current role in the stage order.
	 * This is the key method used to build RoleContext with upstream artifacts.
	 * @param pipelineRunId The pipeline run identifier
	 * @param currentRole The role currently executing
	 * @param stageOrder The ordered list of roles in this pipeline
	 * @return Map of role to artifact for all roles before currentRole in stageOrder
	 */
	public Map<PdlcRole, PipelineArtifact> getUpstreamArtifacts(String pipelineRunId,
			PdlcRole currentRole, List<PdlcRole> stageOrder) {
		int currentIndex = stageOrder.indexOf(currentRole);
		if (currentIndex <= 0) {
			return Map.of();
		}

		List<PdlcRole> upstreamRoles = new ArrayList<>(stageOrder.subList(0, currentIndex));
		List<PipelineArtifact> allArtifacts = getArtifactsForPipeline(pipelineRunId);

		Map<PdlcRole, PipelineArtifact> result = new LinkedHashMap<>();
		for (PdlcRole upstreamRole : upstreamRoles) {
			allArtifacts.stream()
				.filter(a -> upstreamRole.equals(a.getRole()))
				.findFirst()
				.ifPresent(artifact -> result.put(upstreamRole, artifact));
		}

		return result;
	}

	/**
	 * Build a text summary of upstream artifacts for injection into a role's system prompt.
	 * @param artifacts Map of role to artifact from upstream roles
	 * @return Formatted text summary of upstream outputs
	 */
	public String buildArtifactSummary(Map<PdlcRole, PipelineArtifact> artifacts) {
		if (artifacts.isEmpty()) {
			return "";
		}

		StringBuilder sb = new StringBuilder("## Previous Role Outputs\n");
		for (Map.Entry<PdlcRole, PipelineArtifact> entry : artifacts.entrySet()) {
			PdlcRole role = entry.getKey();
			PipelineArtifact artifact = entry.getValue();

			sb.append("\n### ").append(role.name());
			if (artifact.getArtifactType() != null && !artifact.getArtifactType().isBlank()) {
				sb.append(" (").append(capitalize(artifact.getArtifactType())).append(")");
			}
			sb.append("\n");

			Map<String, Object> content = artifact.getContent();
			if (content != null && !content.isEmpty()) {
				Object summary = content.get("summary");
				if (summary != null) {
					sb.append(summary);
				}
				else {
					content.forEach((key, value) -> sb.append("- **").append(key).append("**: ").append(value).append("\n"));
				}
			}
			sb.append("\n");
		}

		return sb.toString().stripTrailing();
	}

	private String capitalize(String value) {
		if (value == null || value.isEmpty()) {
			return value;
		}
		return Character.toUpperCase(value.charAt(0)) + value.substring(1);
	}

}
