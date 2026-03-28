package io.strategiz.social.service.agent.controller;

import io.cidadel.business.ai.engine.AiStepEngineConfig;
import io.cidadel.framework.authorization.annotation.AuthUser;
import io.cidadel.framework.authorization.annotation.RequireAuth;
import io.cidadel.framework.authorization.annotation.RequireScope;
import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.strategiz.social.business.agent.ai.AiRoleOverrideService;
import io.strategiz.social.business.agent.ai.AiSdlcStepDefaults;
import io.strategiz.social.data.entity.AiRoleOverride;
import io.strategiz.social.service.agent.dto.AiEngineOverrideRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin console endpoints for reading SDLC step defaults and managing PDLC role-level AI engine overrides. */
@RestController
@RequestMapping("/v1/console/ai-engine-routing")
@Tag(name = "Console AI Engine Routing", description = "Admin console — AI engine routing config for SDLC steps and PDLC roles")
public class ConsoleAiEngineRoutingController {

	private static final Logger log = LoggerFactory.getLogger(ConsoleAiEngineRoutingController.class);

	private final AiSdlcStepDefaults aiSdlcStepDefaults;

	private final AiRoleOverrideService aiRoleOverrideService;

	public ConsoleAiEngineRoutingController(AiSdlcStepDefaults aiSdlcStepDefaults,
			AiRoleOverrideService aiRoleOverrideService) {
		this.aiSdlcStepDefaults = aiSdlcStepDefaults;
		this.aiRoleOverrideService = aiRoleOverrideService;
	}

	// --- Step-level endpoints (read-only) ---

	@GetMapping("/steps")
	@RequireAuth
	@RequireScope("admin")
	@Operation(summary = "List all SDLC step engine configs",
			description = "Returns the default AI engine and model for every SDLC step. Read-only.")
	public ResponseEntity<Map<String, AiStepEngineConfig>> getAllSteps(@AuthUser AuthenticatedUser user) {
		log.debug("List SDLC step defaults requested by user={}", user.getUserId());
		return ResponseEntity.ok(aiSdlcStepDefaults.getAllDefaults());
	}

	@GetMapping("/steps/{stepName}")
	@RequireAuth
	@RequireScope("admin")
	@Operation(summary = "Get one SDLC step engine config",
			description = "Returns the default AI engine config for the given SDLC step name. Read-only.")
	public ResponseEntity<AiStepEngineConfig> getStep(@PathVariable String stepName,
			@AuthUser AuthenticatedUser user) {
		log.debug("Get SDLC step default stepName={} requested by user={}", stepName, user.getUserId());
		Optional<AiStepEngineConfig> config = aiSdlcStepDefaults.getDefault(stepName);
		return config.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
	}

	// --- Role-level endpoints (full CRUD) ---

	@GetMapping("/roles")
	@RequireAuth
	@RequireScope("admin")
	@Operation(summary = "List all role-level AI engine overrides",
			description = "Returns all active admin-configured AI engine overrides for PDLC roles.")
	public ResponseEntity<List<AiRoleOverride>> getAllRoleOverrides(@AuthUser AuthenticatedUser user) {
		log.debug("List role overrides requested by user={}", user.getUserId());
		return ResponseEntity.ok(aiRoleOverrideService.getAllOverrides());
	}

	@GetMapping("/roles/{roleName}")
	@RequireAuth
	@RequireScope("admin")
	@Operation(summary = "Get one role-level AI engine override",
			description = "Returns the AI engine override for the given PDLC role name, if set.")
	public ResponseEntity<AiRoleOverride> getRoleOverride(@PathVariable String roleName,
			@AuthUser AuthenticatedUser user) {
		log.debug("Get role override roleName={} requested by user={}", roleName, user.getUserId());
		Optional<AiRoleOverride> override = aiRoleOverrideService.getOverride(roleName);
		return override.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
	}

	@PutMapping("/roles/{roleName}")
	@RequireAuth
	@RequireScope("admin")
	@Operation(summary = "Set a role-level AI engine override",
			description = "Creates or updates the AI engine override for the given PDLC role name.")
	public ResponseEntity<AiRoleOverride> setRoleOverride(@PathVariable String roleName,
			@Valid @RequestBody AiEngineOverrideRequest request, @AuthUser AuthenticatedUser user) {
		log.info("Set role override roleName={} engineId={} model={} by user={}",
				roleName, request.getEngineId(), request.getModel(), user.getUserId());
		AiRoleOverride saved = aiRoleOverrideService.setOverride(roleName, request.getEngineId(),
				request.getModel(), user.getUserId());
		return ResponseEntity.ok(saved);
	}

	@DeleteMapping("/roles/{roleName}")
	@RequireAuth
	@RequireScope("admin")
	@Operation(summary = "Remove a role-level AI engine override",
			description = "Deletes the AI engine override for the given PDLC role name, reverting to product defaults.")
	public ResponseEntity<Void> deleteRoleOverride(@PathVariable String roleName,
			@AuthUser AuthenticatedUser user) {
		log.info("Delete role override roleName={} by user={}", roleName, user.getUserId());
		aiRoleOverrideService.deleteOverride(roleName, user.getUserId());
		return ResponseEntity.noContent().build();
	}

}
