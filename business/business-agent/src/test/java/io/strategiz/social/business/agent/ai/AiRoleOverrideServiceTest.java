package io.strategiz.social.business.agent.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.strategiz.social.data.entity.AiRoleOverride;
import io.strategiz.social.data.repository.AiRoleOverrideRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiRoleOverrideServiceTest {

	@Mock
	private AiRoleOverrideRepository repository;

	private AiRoleOverrideService service;

	@BeforeEach
	void setUp() {
		service = new AiRoleOverrideService(repository);
	}

	// ── getOverride ──────────────────────────────────────────────────────────

	@Test
	void getOverride_delegatesToFindById() {
		AiRoleOverride override = buildOverride("IMPLEMENTER", "anthropic-agentic", "claude-opus-4-6", "admin-1");
		when(repository.findById("IMPLEMENTER")).thenReturn(Optional.of(override));

		Optional<AiRoleOverride> result = service.getOverride("IMPLEMENTER");

		assertTrue(result.isPresent());
		assertEquals("IMPLEMENTER", result.get().getRole());
		verify(repository).findById("IMPLEMENTER");
	}

	@Test
	void getOverride_returnsEmptyWhenNotFound() {
		when(repository.findById("UNKNOWN_ROLE")).thenReturn(Optional.empty());

		Optional<AiRoleOverride> result = service.getOverride("UNKNOWN_ROLE");

		assertTrue(result.isEmpty());
	}

	// ── getAllOverrides ───────────────────────────────────────────────────────

	@Test
	void getAllOverrides_delegatesToFindAllActive() {
		AiRoleOverride override1 = buildOverride("IMPLEMENTER", "anthropic-agentic", "claude-opus-4-6", "admin-1");
		AiRoleOverride override2 = buildOverride("TESTER", "openai-agentic", "gpt-4o", "admin-2");
		when(repository.findAllActive()).thenReturn(List.of(override1, override2));

		List<AiRoleOverride> results = service.getAllOverrides();

		assertEquals(2, results.size());
		verify(repository).findAllActive();
	}

	@Test
	void getAllOverrides_returnsEmptyListWhenNone() {
		when(repository.findAllActive()).thenReturn(List.of());

		List<AiRoleOverride> results = service.getAllOverrides();

		assertTrue(results.isEmpty());
	}

	// ── setOverride ──────────────────────────────────────────────────────────

	@Test
	void setOverride_setsAllFieldsAndSaves() {
		AiRoleOverride saved = buildOverride("IMPLEMENTER", "anthropic-agentic", "claude-opus-4-6", "admin-1");
		when(repository.save(any(AiRoleOverride.class), eq("admin-1"))).thenReturn(saved);

		AiRoleOverride result = service.setOverride("IMPLEMENTER", "anthropic-agentic", "claude-opus-4-6", "admin-1");

		assertNotNull(result);
		assertEquals("IMPLEMENTER", result.getRole());
		assertEquals("anthropic-agentic", result.getEngineId());
		assertEquals("claude-opus-4-6", result.getModel());
		assertEquals("admin-1", result.getUpdatedBy());
	}

	@Test
	void setOverride_savesEntityWithCorrectId() {
		AiRoleOverride saved = buildOverride("REVIEWER", "openai-agentic", "gpt-4o", "admin-2");
		when(repository.save(any(AiRoleOverride.class), eq("admin-2"))).thenReturn(saved);

		service.setOverride("REVIEWER", "openai-agentic", "gpt-4o", "admin-2");

		ArgumentCaptor<AiRoleOverride> captor = ArgumentCaptor.forClass(AiRoleOverride.class);
		verify(repository).save(captor.capture(), eq("admin-2"));

		AiRoleOverride captured = captor.getValue();
		assertEquals("REVIEWER", captured.getId());
		assertEquals("REVIEWER", captured.getRole());
		assertEquals("openai-agentic", captured.getEngineId());
		assertEquals("gpt-4o", captured.getModel());
		assertEquals("admin-2", captured.getUpdatedBy());
	}

	// ── deleteOverride ───────────────────────────────────────────────────────

	@Test
	void deleteOverride_delegatesToRepositoryDelete() {
		service.deleteOverride("IMPLEMENTER", "admin-1");

		verify(repository).delete(eq("IMPLEMENTER"), eq("admin-1"));
	}

	@Test
	void deleteOverride_passesRoleNameAsId() {
		service.deleteOverride("PM", "admin-2");

		ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> byCaptor = ArgumentCaptor.forClass(String.class);
		verify(repository).delete(idCaptor.capture(), byCaptor.capture());

		assertEquals("PM", idCaptor.getValue());
	}

	// ── helpers ──────────────────────────────────────────────────────────────

	private AiRoleOverride buildOverride(String roleName, String engineId, String model, String updatedBy) {
		AiRoleOverride override = new AiRoleOverride();
		override.setId(roleName);
		override.setRole(roleName);
		override.setEngineId(engineId);
		override.setModel(model);
		override.setUpdatedBy(updatedBy);
		return override;
	}

}
