package io.strategiz.social.service.agent.controller;

import io.strategiz.framework.authorization.annotation.AuthUser;
import io.strategiz.framework.authorization.annotation.RequireAuth;
import io.strategiz.framework.authorization.context.AuthenticatedUser;
import io.strategiz.framework.llmrouter.LlmRouter;
import io.strategiz.social.business.agent.service.DeviceRoutingService;
import io.strategiz.social.business.agent.service.SparkService;
import io.strategiz.social.business.agent.service.TranscriptionService;
import io.strategiz.social.business.agent.service.UserProvisioningService;
import io.strategiz.social.business.agent.service.VoiceAgentService;
import io.strategiz.social.data.entity.ActionConfirmation;
import io.strategiz.social.data.entity.AgentAuditLog;
import io.strategiz.social.data.entity.DeviceRegistration;
import io.strategiz.social.data.entity.SocialIntegration;
import io.strategiz.social.data.entity.Spark;
import io.strategiz.social.data.repository.ActionConfirmationRepository;
import io.strategiz.social.data.repository.AgentAuditLogRepository;
import io.strategiz.social.data.repository.SocialIntegrationRepository;
import io.strategiz.social.service.agent.dto.AgentCommandRequest;
import io.strategiz.social.service.agent.dto.AgentCommandResponse;
import io.strategiz.social.service.agent.dto.AuditLogResponse;
import io.strategiz.social.service.agent.dto.ConfirmActionRequest;
import io.strategiz.social.service.agent.dto.TranscribeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

	private final TranscriptionService transcriptionService;

	private final SparkService sparkService;

	private final DeviceRoutingService deviceRoutingService;

	public AgentController(VoiceAgentService voiceAgentService, LlmRouter llmRouter,
			AgentAuditLogRepository auditLogRepository, ActionConfirmationRepository confirmationRepository,
			SocialIntegrationRepository integrationRepository, UserProvisioningService userProvisioningService,
			TranscriptionService transcriptionService, SparkService sparkService,
			DeviceRoutingService deviceRoutingService) {
		this.voiceAgentService = voiceAgentService;
		this.llmRouter = llmRouter;
		this.auditLogRepository = auditLogRepository;
		this.confirmationRepository = confirmationRepository;
		this.integrationRepository = integrationRepository;
		this.userProvisioningService = userProvisioningService;
		this.transcriptionService = transcriptionService;
		this.sparkService = sparkService;
		this.deviceRoutingService = deviceRoutingService;
	}

	@PostMapping("/command")
	@RequireAuth
	@Operation(summary = "Execute a voice command",
			description = "Send a text command to the AI agent. Always creates a Spark first, "
					+ "then delegates to an online device if available, otherwise processes in the cloud.")
	public ResponseEntity<AgentCommandResponse> executeCommand(@Valid @RequestBody AgentCommandRequest request,
			@AuthUser AuthenticatedUser user) {
		log.info("Agent command from user {}: {}", user.getUserId(),
				request.getText().length() > 100 ? request.getText().substring(0, 100) + "..." : request.getText());

		// Ensure user record exists in Firestore
		userProvisioningService.ensureUserExists(user.getUserId());

		// Always create a Spark first
		String commandText = request.getText();
		String title = commandText.length() > 80 ? commandText.substring(0, 80) + "..." : commandText;
		Spark spark = sparkService.createSpark(user.getUserId(), title, commandText, request.getSparkType(), null,
				null, null, null);

		// Try to delegate to an online device first
		if (deviceRoutingService.hasOnlineDevice(user.getUserId())) {
			Optional<AgentCommandResponse> delegated = delegateToDevice(spark, user.getUserId());
			if (delegated.isPresent()) {
				return ResponseEntity.ok(delegated.get());
			}
			// If routing failed (no device matched after preferences), fall through to cloud
		}

		// Fallback: process in the cloud via VoiceAgentService
		return ResponseEntity.ok(executeInCloud(request, user.getUserId(), spark));
	}

	/** Delegate an already-created spark to an online device. */
	private Optional<AgentCommandResponse> delegateToDevice(Spark spark, String userId) {
		Optional<DeviceRegistration> device = sparkService.routeSpark(spark.getId(), userId);
		if (device.isPresent()) {
			String status = spark.getStatus() != null ? spark.getStatus().name() : "EXECUTING";
			log.info("Agent command delegated to device {} (spark={})", device.get().getDeviceName(), spark.getId());
			return Optional.of(AgentCommandResponse.delegated(spark.getId(), status, device.get().getDeviceName()));
		}

		log.info("Device routing returned empty for spark={}, falling back to cloud", spark.getId());
		return Optional.empty();
	}

	/** Execute command in the cloud via VoiceAgentService, tracking with the given spark. */
	private AgentCommandResponse executeInCloud(AgentCommandRequest request, String userId, Spark spark) {
		String sessionId = request.getSessionId() != null ? request.getSessionId() : UUID.randomUUID().toString();
		String timezone = request.getTimezone() != null ? request.getTimezone() : "UTC";

		// Get connected platforms for system prompt context
		List<SocialIntegration> integrations = integrationRepository.findByUserId(userId);
		List<String> connectedPlatforms = integrations.stream()
			.filter(i -> !i.isDisabled())
			.map(i -> i.getPlatform().getDisplayName())
			.toList();

		// VoiceAgentService handles markRunning/markCompleted/markFailed internally
		VoiceAgentService.AgentResult result = voiceAgentService.execute(spark.getId(), request.getText(), userId,
				sessionId, connectedPlatforms, timezone, request.getModel());

		AgentCommandResponse response = new AgentCommandResponse(result.getResponseText(), result.getToolsInvoked(),
				result.isSuccess(), result.getModel());
		response.setSparkId(spark.getId());
		return response;
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

	@PostMapping(value = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@RequireAuth
	@Operation(summary = "Transcribe audio to text",
			description = "Proxies audio to OpenAI Whisper API and returns transcribed text")
	public ResponseEntity<TranscribeResponse> transcribeAudio(@RequestParam("file") MultipartFile file,
			@AuthUser AuthenticatedUser user) {
		log.info("Transcribe request from user {}: {} ({} bytes)", user.getUserId(), file.getOriginalFilename(),
				file.getSize());

		try {
			String text = transcriptionService.transcribe(file.getBytes(), file.getOriginalFilename());
			return ResponseEntity.ok(new TranscribeResponse(text));
		}
		catch (Exception ex) {
			log.error("Transcription failed for user {}", user.getUserId(), ex);
			return ResponseEntity.internalServerError()
				.body(new TranscribeResponse("Transcription failed: " + ex.getMessage()));
		}
	}

}
