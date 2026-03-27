package io.strategiz.social.business.agent.ai;

import io.strategiz.social.data.entity.AiRoleOverride;
import io.strategiz.social.data.repository.AiRoleOverrideRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** CRUD service for role-level AI engine overrides used by the admin console and PDLC executor. */
@Service
public class AiRoleOverrideService {

	private static final String SYSTEM = "system";

	private final AiRoleOverrideRepository repository;

	public AiRoleOverrideService(AiRoleOverrideRepository repository) {
		this.repository = repository;
	}

	/**
	 * Find a role override by role name (document ID).
	 *
	 * @param roleName PDLC role name, e.g. "IMPLEMENTER"
	 * @return the override if it exists
	 */
	public Optional<AiRoleOverride> getOverride(String roleName) {
		return repository.findById(roleName);
	}

	/**
	 * List all active role overrides.
	 *
	 * @return active overrides, never null
	 */
	public List<AiRoleOverride> getAllOverrides() {
		return repository.findAllActive();
	}

	/**
	 * Create or update a role-level AI engine override.
	 *
	 * @param roleName  PDLC role name (used as document ID)
	 * @param engineId  engine identifier, e.g. "anthropic-agentic"
	 * @param model     model identifier, e.g. "claude-opus-4-6"
	 * @param updatedBy admin user ID performing the change
	 * @return the saved override
	 */
	public AiRoleOverride setOverride(String roleName, String engineId, String model, String updatedBy) {
		AiRoleOverride override = new AiRoleOverride();
		override.setId(roleName);
		override.setRole(roleName);
		override.setEngineId(engineId);
		override.setModel(model);
		override.setUpdatedBy(updatedBy);
		return repository.save(override, updatedBy);
	}

	/**
	 * Delete a role override (soft delete via BaseRepository).
	 *
	 * @param roleName PDLC role name (document ID)
	 */
	public void deleteOverride(String roleName) {
		repository.delete(roleName, SYSTEM);
	}

}
