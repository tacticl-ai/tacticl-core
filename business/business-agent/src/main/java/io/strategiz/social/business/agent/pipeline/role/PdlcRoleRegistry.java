package io.strategiz.social.business.agent.pipeline.role;

import io.strategiz.social.data.entity.PdlcRole;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Spring-managed registry that auto-discovers all {@link PdlcRoleSkill} beans and provides
 * lookup by {@link PdlcRole}. One skill per role is expected; if duplicates are registered
 * the last one wins and a warning is logged.
 */
@Service
public class PdlcRoleRegistry {

	private static final Logger log = LoggerFactory.getLogger(PdlcRoleRegistry.class);

	private final Map<PdlcRole, PdlcRoleSkill> skills;

	public PdlcRoleRegistry(List<PdlcRoleSkill> skillList) {
		Map<PdlcRole, PdlcRoleSkill> map = new EnumMap<>(PdlcRole.class);
		for (PdlcRoleSkill skill : skillList) {
			PdlcRoleSkill previous = map.put(skill.getRole(), skill);
			if (previous != null) {
				log.warn("Duplicate PdlcRoleSkill for role {}: replacing {} with {}",
						skill.getRole(),
						previous.getClass().getSimpleName(),
						skill.getClass().getSimpleName());
			}
			else {
				log.info("Registered PdlcRoleSkill: {} -> {}", skill.getRole(), skill.getClass().getSimpleName());
			}
		}
		this.skills = Collections.unmodifiableMap(map);
		log.info("PdlcRoleRegistry initialized with {} role skills", skills.size());
	}

	/**
	 * Look up the skill for a given PDLC role.
	 *
	 * @param role the role to look up
	 * @return the skill, or empty if no implementation is registered
	 */
	public Optional<PdlcRoleSkill> getRole(PdlcRole role) {
		return Optional.ofNullable(skills.get(role));
	}

	/**
	 * Returns all registered role skills.
	 */
	public Collection<PdlcRoleSkill> getAllRoles() {
		return skills.values();
	}

	/**
	 * Returns {@code true} if a skill is registered for the given role.
	 */
	public boolean hasRole(PdlcRole role) {
		return skills.containsKey(role);
	}

}
