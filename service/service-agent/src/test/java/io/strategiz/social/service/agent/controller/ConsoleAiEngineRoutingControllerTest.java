package io.strategiz.social.service.agent.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.cidadel.business.ai.engine.AiStepEngineConfig;
import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.strategiz.social.business.agent.ai.AiRoleOverrideService;
import io.strategiz.social.business.agent.ai.AiSdlcStepDefaults;
import io.strategiz.social.data.entity.AiRoleOverride;
import io.strategiz.social.service.agent.dto.AiEngineOverrideRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class ConsoleAiEngineRoutingControllerTest {

	private static final String USER_ID = "admin-user-1";

	private static final String STEP_NAME = "CODE_GENERATION";

	private static final String ROLE_NAME = "IMPLEMENTER";

	@Mock
	private AiSdlcStepDefaults aiSdlcStepDefaults;

	@Mock
	private AiRoleOverrideService aiRoleOverrideService;

	private ConsoleAiEngineRoutingController controller;

	@BeforeEach
	void setUp() {
		controller = new ConsoleAiEngineRoutingController(aiSdlcStepDefaults, aiRoleOverrideService);
	}

	private AuthenticatedUser auth() {
		return AuthenticatedUser.builder().userId(USER_ID).build();
	}

	private AiStepEngineConfig stepConfig(String engineId, String model) {
		return new AiStepEngineConfig(engineId, model, List.of());
	}

	private AiRoleOverride roleOverride(String roleName, String engineId, String model) {
		AiRoleOverride override = new AiRoleOverride();
		override.setId(roleName);
		override.setRole(roleName);
		override.setEngineId(engineId);
		override.setModel(model);
		override.setUpdatedBy(USER_ID);
		return override;
	}

	// --- getAllSteps ---

	@Test
	void getAllSteps_returnsAllDefaults() {
		Map<String, AiStepEngineConfig> defaults = Map.of(
				STEP_NAME, stepConfig("claude-code-cli", "claude-opus-4-6"),
				"CODE_REVIEW", stepConfig("anthropic-agentic", "claude-sonnet-4-5"));
		when(aiSdlcStepDefaults.getAllDefaults()).thenReturn(defaults);

		ResponseEntity<Map<String, AiStepEngineConfig>> response = controller.getAllSteps(auth());

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(2, response.getBody().size());
		verify(aiSdlcStepDefaults).getAllDefaults();
	}

	@Test
	void getAllSteps_returnsEmptyMapWhenNoDefaults() {
		when(aiSdlcStepDefaults.getAllDefaults()).thenReturn(Map.of());

		ResponseEntity<Map<String, AiStepEngineConfig>> response = controller.getAllSteps(auth());

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(0, response.getBody().size());
	}

	// --- getStep ---

	@Test
	void getStep_returnsConfigWhenStepExists() {
		AiStepEngineConfig config = stepConfig("claude-code-cli", "claude-opus-4-6");
		when(aiSdlcStepDefaults.getDefault(STEP_NAME)).thenReturn(Optional.of(config));

		ResponseEntity<AiStepEngineConfig> response = controller.getStep(STEP_NAME, auth());

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals("claude-code-cli", response.getBody().getEngineId());
		assertEquals("claude-opus-4-6", response.getBody().getModel());
		verify(aiSdlcStepDefaults).getDefault(STEP_NAME);
	}

	@Test
	void getStep_returnsNotFoundWhenStepMissing() {
		when(aiSdlcStepDefaults.getDefault("UNKNOWN_STEP")).thenReturn(Optional.empty());

		ResponseEntity<AiStepEngineConfig> response = controller.getStep("UNKNOWN_STEP", auth());

		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
	}

	// --- getAllRoleOverrides ---

	@Test
	void getAllRoleOverrides_returnsAllOverrides() {
		List<AiRoleOverride> overrides = List.of(
				roleOverride(ROLE_NAME, "anthropic-agentic", "claude-opus-4-6"),
				roleOverride("REVIEWER", "anthropic-api", "claude-sonnet-4-5"));
		when(aiRoleOverrideService.getAllOverrides()).thenReturn(overrides);

		ResponseEntity<List<AiRoleOverride>> response = controller.getAllRoleOverrides(auth());

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(2, response.getBody().size());
		assertEquals(ROLE_NAME, response.getBody().get(0).getRole());
		verify(aiRoleOverrideService).getAllOverrides();
	}

	@Test
	void getAllRoleOverrides_returnsEmptyListWhenNoneSet() {
		when(aiRoleOverrideService.getAllOverrides()).thenReturn(List.of());

		ResponseEntity<List<AiRoleOverride>> response = controller.getAllRoleOverrides(auth());

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(0, response.getBody().size());
	}

	// --- getRoleOverride ---

	@Test
	void getRoleOverride_returnsOverrideWhenFound() {
		AiRoleOverride override = roleOverride(ROLE_NAME, "anthropic-agentic", "claude-opus-4-6");
		when(aiRoleOverrideService.getOverride(ROLE_NAME)).thenReturn(Optional.of(override));

		ResponseEntity<AiRoleOverride> response = controller.getRoleOverride(ROLE_NAME, auth());

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(ROLE_NAME, response.getBody().getRole());
		assertEquals("anthropic-agentic", response.getBody().getEngineId());
		assertEquals("claude-opus-4-6", response.getBody().getModel());
		verify(aiRoleOverrideService).getOverride(ROLE_NAME);
	}

	@Test
	void getRoleOverride_returnsNotFoundWhenAbsent() {
		when(aiRoleOverrideService.getOverride("UNKNOWN_ROLE")).thenReturn(Optional.empty());

		ResponseEntity<AiRoleOverride> response = controller.getRoleOverride("UNKNOWN_ROLE", auth());

		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
	}

	// --- setRoleOverride ---

	@Test
	void setRoleOverride_savesAndReturnsOverride() {
		AiEngineOverrideRequest request = new AiEngineOverrideRequest();
		request.setEngineId("claude-code-cli");
		request.setModel("claude-opus-4-6");

		AiRoleOverride saved = roleOverride(ROLE_NAME, "claude-code-cli", "claude-opus-4-6");
		when(aiRoleOverrideService.setOverride(ROLE_NAME, "claude-code-cli", "claude-opus-4-6", USER_ID))
			.thenReturn(saved);

		ResponseEntity<AiRoleOverride> response = controller.setRoleOverride(ROLE_NAME, request, auth());

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(ROLE_NAME, response.getBody().getRole());
		assertEquals("claude-code-cli", response.getBody().getEngineId());
		assertEquals("claude-opus-4-6", response.getBody().getModel());
		verify(aiRoleOverrideService).setOverride(ROLE_NAME, "claude-code-cli", "claude-opus-4-6", USER_ID);
	}

	@Test
	void setRoleOverride_passesUserIdAsUpdatedBy() {
		AiEngineOverrideRequest request = new AiEngineOverrideRequest();
		request.setEngineId("anthropic-api");
		request.setModel("claude-sonnet-4-5");

		AiRoleOverride saved = roleOverride(ROLE_NAME, "anthropic-api", "claude-sonnet-4-5");
		when(aiRoleOverrideService.setOverride(ROLE_NAME, "anthropic-api", "claude-sonnet-4-5", USER_ID))
			.thenReturn(saved);

		controller.setRoleOverride(ROLE_NAME, request, auth());

		verify(aiRoleOverrideService).setOverride(ROLE_NAME, "anthropic-api", "claude-sonnet-4-5", USER_ID);
	}

	// --- deleteRoleOverride ---

	@Test
	void deleteRoleOverride_returnsNoContent() {
		ResponseEntity<Void> response = controller.deleteRoleOverride(ROLE_NAME, auth());

		assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
		verify(aiRoleOverrideService).deleteOverride(ROLE_NAME);
	}

	@Test
	void deleteRoleOverride_callsServiceWithCorrectRoleName() {
		controller.deleteRoleOverride("TESTER", auth());

		verify(aiRoleOverrideService).deleteOverride("TESTER");
	}

}
