package io.strategiz.social.business.agent.pipeline.role;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.strategiz.social.data.entity.PdlcRole;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PdlcRoleRegistryTest {

	// --- helpers ---

	private PdlcRoleSkill stubRole(PdlcRole role) {
		return new PdlcRoleSkill() {
			@Override public PdlcRole getRole() { return role; }
			@Override public String getSystemPrompt() { return ""; }
			@Override public List<String> getAvailableTools() { return List.of(); }
			@Override public String getAiSdlcStepName() { return ""; }
			@Override public SuccessCriteria getSuccessCriteria() { return new SuccessCriteria("", ""); }
			@Override public RoleResult execute(RoleContext ctx) { return null; }
		};
	}

	// --- getRole ---

	@Test
	void getRole_returnsRegisteredSkill() {
		PdlcRoleSkill pmSkill = stubRole(PdlcRole.PM);
		PdlcRoleRegistry registry = new PdlcRoleRegistry(List.of(pmSkill));

		Optional<PdlcRoleSkill> result = registry.getRole(PdlcRole.PM);

		assertTrue(result.isPresent());
		assertSame(pmSkill, result.get());
	}

	@Test
	void getRole_returnsEmptyForUnregisteredRole() {
		PdlcRoleRegistry registry = new PdlcRoleRegistry(List.of(stubRole(PdlcRole.PM)));

		Optional<PdlcRoleSkill> result = registry.getRole(PdlcRole.ARCHITECT);

		assertTrue(result.isEmpty());
	}

	// --- getAllRoles ---

	@Test
	void getAllRoles_returnsAllRegisteredSkills() {
		PdlcRoleSkill pm = stubRole(PdlcRole.PM);
		PdlcRoleSkill researcher = stubRole(PdlcRole.RESEARCHER);
		PdlcRoleSkill architect = stubRole(PdlcRole.ARCHITECT);

		PdlcRoleRegistry registry = new PdlcRoleRegistry(List.of(pm, researcher, architect));

		Collection<PdlcRoleSkill> all = registry.getAllRoles();

		assertEquals(3, all.size());
		assertTrue(all.contains(pm));
		assertTrue(all.contains(researcher));
		assertTrue(all.contains(architect));
	}

	@Test
	void getAllRoles_returnsEmptyWhenNoneRegistered() {
		PdlcRoleRegistry registry = new PdlcRoleRegistry(List.of());

		assertTrue(registry.getAllRoles().isEmpty());
	}

	// --- hasRole ---

	@Test
	void hasRole_returnsTrueForRegisteredRole() {
		PdlcRoleRegistry registry = new PdlcRoleRegistry(List.of(stubRole(PdlcRole.TESTER)));

		assertTrue(registry.hasRole(PdlcRole.TESTER));
	}

	@Test
	void hasRole_returnsFalseForUnregisteredRole() {
		PdlcRoleRegistry registry = new PdlcRoleRegistry(List.of(stubRole(PdlcRole.TESTER)));

		assertFalse(registry.hasRole(PdlcRole.DEVOPS));
	}

	// --- duplicate handling ---

	@Test
	void duplicateRoleSkill_lastOneWins() {
		PdlcRoleSkill first = stubRole(PdlcRole.PM);
		PdlcRoleSkill second = stubRole(PdlcRole.PM);

		PdlcRoleRegistry registry = new PdlcRoleRegistry(List.of(first, second));

		// Only one PM should remain
		assertEquals(1, registry.getAllRoles().size());
		assertSame(second, registry.getRole(PdlcRole.PM).orElseThrow());
	}

	// --- multiple distinct roles ---

	@Test
	void allTwelveRolesCanBeRegistered() {
		List<PdlcRoleSkill> all = List.of(
				stubRole(PdlcRole.PM),
				stubRole(PdlcRole.RESEARCHER),
				stubRole(PdlcRole.ARCHITECT),
				stubRole(PdlcRole.DESIGNER),
				stubRole(PdlcRole.PLANNER),
				stubRole(PdlcRole.IMPLEMENTER),
				stubRole(PdlcRole.REVIEWER),
				stubRole(PdlcRole.TESTER),
				stubRole(PdlcRole.SECURITY_ANALYST),
				stubRole(PdlcRole.TECHNICAL_WRITER),
				stubRole(PdlcRole.DEVOPS),
				stubRole(PdlcRole.RETRO_ANALYST)
		);

		PdlcRoleRegistry registry = new PdlcRoleRegistry(all);

		assertEquals(12, registry.getAllRoles().size());
		for (PdlcRole role : PdlcRole.values()) {
			assertTrue(registry.hasRole(role), "Missing role: " + role);
		}
	}

}
