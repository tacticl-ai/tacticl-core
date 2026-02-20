package io.strategiz.social.service.agent.controller;

import io.strategiz.framework.authorization.annotation.AuthUser;
import io.strategiz.framework.authorization.annotation.RequireAuth;
import io.strategiz.framework.authorization.context.AuthenticatedUser;
import io.strategiz.framework.llmrouter.LlmRouter;
import io.strategiz.social.business.agent.service.AskService;
import io.strategiz.social.business.agent.service.UserProvisioningService;
import io.strategiz.social.business.agent.service.VoiceAgentService;
import io.strategiz.social.data.entity.ActionConfirmation;
import io.strategiz.social.data.entity.AgentAuditLog;
import io.strategiz.social.data.entity.Ask;
import io.strategiz.social.data.entity.AskState;
import io.strategiz.social.data.entity.SocialIntegration;
import io.strategiz.social.data.repository.ActionConfirmationRepository;
import io.strategiz.social.data.repository.AgentAuditLogRepository;
import io.strategiz.social.data.repository.SocialIntegrationRepository;
import io.strategiz.social.service.agent.dto.ActivityResponse;
import io.strategiz.social.service.agent.dto.AgentCommandRequest;
import io.strategiz.social.service.agent.dto.AgentCommandResponse;
import io.strategiz.social.service.agent.dto.AuditLogResponse;
import io.strategiz.social.service.agent.dto.ConfirmActionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for the voice agent. */
@RestController
@RequestMapping("/api/agent")
@Tag(name = "Voice Agent", description = "Personal AI agent that remotes into your devices as workers")
public class AgentController {

	private static final Logger log = LoggerFactory.getLogger(AgentController.class);

	private final VoiceAgentService voiceAgentService;

	private final LlmRouter llmRouter;

	private final AgentAuditLogRepository auditLogRepository;

	private final ActionConfirmationRepository confirmationRepository;

	private final SocialIntegrationRepository integrationRepository;

	private final UserProvisioningService userProvisioningService;

	private final AskService askService;

	public AgentController(VoiceAgentService voiceAgentService, LlmRouter llmRouter,
			AgentAuditLogRepository auditLogRepository, ActionConfirmationRepository confirmationRepository,
			SocialIntegrationRepository integrationRepository, UserProvisioningService userProvisioningService,
			AskService askService) {
		this.voiceAgentService = voiceAgentService;
		this.llmRouter = llmRouter;
		this.auditLogRepository = auditLogRepository;
		this.confirmationRepository = confirmationRepository;
		this.integrationRepository = integrationRepository;
		this.userProvisioningService = userProvisioningService;
		this.askService = askService;
	}

	@PostMapping("/command")
	@RequireAuth
	@Operation(summary = "Execute a voice command", description = "Send a text command to the AI agent for processing")
	public ResponseEntity<AgentCommandResponse> executeCommand(@Valid @RequestBody AgentCommandRequest request,
			@AuthUser AuthenticatedUser user) {
		log.info("Agent command from user {}: {}", user.getUserId(),
				request.getText().length() > 100 ? request.getText().substring(0, 100) + "..." : request.getText());

		// Ensure user record exists in Firestore
		userProvisioningService.ensureUserExists(user.getUserId());

		String sessionId = request.getSessionId() != null ? request.getSessionId() : UUID.randomUUID().toString();
		String timezone = request.getTimezone() != null ? request.getTimezone() : "UTC";

		// Get connected platforms for system prompt context
		List<SocialIntegration> integrations = integrationRepository.findByUserId(user.getUserId());
		List<String> connectedPlatforms = integrations.stream()
			.filter(i -> !i.isDisabled())
			.map(i -> i.getPlatform().getDisplayName())
			.toList();

		VoiceAgentService.AgentResult result = voiceAgentService.execute(request.getText(), user.getUserId(), sessionId,
				connectedPlatforms, timezone, request.getModel());

		AgentCommandResponse response = new AgentCommandResponse(result.getResponseText(), result.getToolsInvoked(),
				result.isSuccess(), result.getModel());

		return ResponseEntity.ok(response);
	}

	@PostMapping("/confirm/{confirmationId}")
	@RequireAuth
	@Operation(summary = "Approve or deny a pending action",
			description = "Confirm or reject a Tier 1/2 action that requires user approval")
	public ResponseEntity<String> confirmAction(@PathVariable String confirmationId,
			@Valid @RequestBody ConfirmActionRequest request, @AuthUser AuthenticatedUser user) {
		log.info("Confirmation {} from user {}: {}", confirmationId, user.getUserId(),
				request.getApproved() ? "APPROVED" : "DENIED");

		Optional<ActionConfirmation> confirmation = confirmationRepository.findById(confirmationId);
		if (confirmation.isEmpty()) {
			return ResponseEntity.notFound().build();
		}

		ActionConfirmation action = confirmation.get();
		if (!action.getUserId().equals(user.getUserId())) {
			return ResponseEntity.status(403).body("Not authorized to confirm this action");
		}

		if (action.getState() != ActionConfirmation.ConfirmationState.PENDING) {
			return ResponseEntity.badRequest().body("Action is no longer pending: " + action.getState());
		}

		action.setState(request.getApproved() ? ActionConfirmation.ConfirmationState.APPROVED
				: ActionConfirmation.ConfirmationState.DENIED);
		confirmationRepository.save(action, action.getId());

		return ResponseEntity.ok(request.getApproved() ? "Action approved" : "Action denied");
	}

	@GetMapping("/models")
	@RequireAuth
	@Operation(summary = "List available LLM models")
	public ResponseEntity<?> getAvailableModels() {
		return ResponseEntity.ok(llmRouter.getAvailableModels());
	}

	@GetMapping("/history")
	@RequireAuth
	@Operation(summary = "Get command history", description = "Retrieve recent agent command audit logs")
	public ResponseEntity<List<AuditLogResponse>> getHistory(@AuthUser AuthenticatedUser user) {
		List<AgentAuditLog> logs = auditLogRepository.findRecentByUserId(user.getUserId(), 50);

		List<AuditLogResponse> response = logs.stream().map(logEntry -> {
			AuditLogResponse dto = new AuditLogResponse();
			dto.setId(logEntry.getId());
			dto.setCommandText(logEntry.getCommandText());
			dto.setResponseText(logEntry.getResponseText());
			dto.setToolsInvoked(logEntry.getToolsInvoked());
			dto.setSuccess(logEntry.isSuccess());
			dto.setExecutionTimeMs(logEntry.getExecutionTimeMs());
			dto.setCreatedAt(logEntry.getCreatedAt());
			return dto;
		}).toList();

		return ResponseEntity.ok(response);
	}

	@GetMapping("/activity")
	@RequireAuth
	@Operation(summary = "Get activity dashboard data",
			description = "Returns active and recent asks with tasks and commands")
	public ResponseEntity<ActivityResponse> getActivity(@AuthUser AuthenticatedUser user) {
		List<Ask> active = askService.getActiveAsks(user.getUserId());
		List<Ask> recent = askService.getRecentAsks(user.getUserId(), 10);

		ActivityResponse response = new ActivityResponse();
		response.setActiveAsks(active.stream()
			.map(a -> askService.getAskDetail(a.getId(), user.getUserId()).orElse(Map.of()))
			.toList());
		response.setRecentAsks(recent.stream()
			.filter(a -> a.getState() != AskState.PENDING && a.getState() != AskState.RUNNING)
			.map(a -> askService.getAskDetail(a.getId(), user.getUserId()).orElse(Map.of()))
			.limit(10)
			.toList());
		return ResponseEntity.ok(response);
	}

	@GetMapping("/asks/{askId}")
	@RequireAuth
	@Operation(summary = "Get ask detail")
	public ResponseEntity<Map<String, Object>> getAskDetail(@PathVariable String askId,
			@AuthUser AuthenticatedUser user) {
		return askService.getAskDetail(askId, user.getUserId())
			.map(ResponseEntity::ok)
			.orElse(ResponseEntity.notFound().build());
	}

	@PostMapping("/asks/{askId}/cancel")
	@RequireAuth
	@Operation(summary = "Cancel an ask")
	public ResponseEntity<Void> cancelAsk(@PathVariable String askId, @AuthUser AuthenticatedUser user) {
		boolean cancelled = askService.cancelAsk(askId, user.getUserId());
		return cancelled ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
	}

}
