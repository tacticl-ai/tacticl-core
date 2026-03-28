package io.strategiz.social.service.agent.controller;

import io.cidadel.framework.authorization.annotation.AuthUser;
import io.cidadel.framework.authorization.annotation.RequireAuth;
import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.cidadel.framework.llmrouter.LlmRouter;
import io.strategiz.social.business.agent.pipeline.PdlcClassification;
import io.strategiz.social.business.agent.pipeline.PdlcClassifierService;
import io.strategiz.social.business.agent.pipeline.PdlcPipelineOrchestrator;
import io.strategiz.social.business.agent.pipeline.PipelineStateManager;
import io.strategiz.social.business.agent.pipeline.PlaybookConfig;
import io.strategiz.social.business.agent.pipeline.PlaybookRegistry;
import io.strategiz.social.business.agent.pipeline.RoleSkipParser;
import io.strategiz.social.business.agent.service.DeviceRoutingService;
import io.strategiz.social.business.agent.service.SparkClassifierService;
import io.strategiz.social.business.agent.service.SparkService;
import io.strategiz.social.business.agent.service.TranscriptionService;
import io.strategiz.social.business.agent.service.UserProvisioningService;
import io.strategiz.social.business.agent.service.CloudOrchestratorService;
import io.strategiz.social.business.agent.service.UserConfigService;
import io.strategiz.social.data.entity.ActionConfirmation;
import io.strategiz.social.data.entity.AgentAuditLog;
import io.strategiz.social.data.entity.DeviceRegistration;
import io.strategiz.social.data.entity.ExecutionPreference;
import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineTier;
import io.strategiz.social.data.entity.SocialIntegration;
import io.strategiz.social.data.entity.Spark;
import io.strategiz.social.data.entity.UserConfig;
import io.strategiz.social.data.entity.PipelineRun;
import io.strategiz.social.data.repository.ActionConfirmationRepository;
import io.strategiz.social.data.repository.AgentAuditLogRepository;
import io.strategiz.social.data.repository.SocialIntegrationRepository;
import io.strategiz.social.service.agent.dto.AgentAction;
import io.strategiz.social.service.agent.dto.AgentCommandRequest;
import io.strategiz.social.service.agent.dto.AgentCommandResponse;
import io.strategiz.social.service.agent.dto.AuditLogResponse;
import io.strategiz.social.service.agent.dto.ConfirmActionRequest;
import io.strategiz.social.service.agent.dto.TranscribeResponse;
import io.strategiz.social.service.agent.service.SetupActionDetector;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

/** REST controller for the cloud agent orchestrator. */
@RestController
@RequestMapping("/v1/agent")
@Tag(name = "Agent", description = "Personal AI agent that remotes into your devices as workers")
public class AgentController {

	private static final Logger log = LoggerFactory.getLogger(AgentController.class);

	private final CloudOrchestratorService cloudOrchestrator;

	private final LlmRouter llmRouter;

	private final AgentAuditLogRepository auditLogRepository;

	private final ActionConfirmationRepository confirmationRepository;

	private final SocialIntegrationRepository integrationRepository;

	private final UserProvisioningService userProvisioningService;

	private final TranscriptionService transcriptionService;

	private final SparkService sparkService;

	private final DeviceRoutingService deviceRoutingService;

	private final SetupActionDetector setupActionDetector;

	private final UserConfigService userConfigService;

	private final SparkClassifierService sparkClassifierService;

	private final PdlcClassifierService pdlcClassifierService;

	private final PdlcPipelineOrchestrator pdlcPipelineOrchestrator;

	private final PipelineStateManager pipelineStateManager;

	private final PlaybookRegistry playbookRegistry;

	public AgentController(CloudOrchestratorService cloudOrchestrator, LlmRouter llmRouter,
			AgentAuditLogRepository auditLogRepository, ActionConfirmationRepository confirmationRepository,
			SocialIntegrationRepository integrationRepository, UserProvisioningService userProvisioningService,
			TranscriptionService transcriptionService, SparkService sparkService,
			DeviceRoutingService deviceRoutingService, SetupActionDetector setupActionDetector,
			UserConfigService userConfigService, SparkClassifierService sparkClassifierService,
			PdlcClassifierService pdlcClassifierService, PdlcPipelineOrchestrator pdlcPipelineOrchestrator,
			PipelineStateManager pipelineStateManager, PlaybookRegistry playbookRegistry) {
		this.cloudOrchestrator = cloudOrchestrator;
		this.llmRouter = llmRouter;
		this.auditLogRepository = auditLogRepository;
		this.confirmationRepository = confirmationRepository;
		this.integrationRepository = integrationRepository;
		this.userProvisioningService = userProvisioningService;
		this.transcriptionService = transcriptionService;
		this.sparkService = sparkService;
		this.deviceRoutingService = deviceRoutingService;
		this.setupActionDetector = setupActionDetector;
		this.userConfigService = userConfigService;
		this.sparkClassifierService = sparkClassifierService;
		this.pdlcClassifierService = pdlcClassifierService;
		this.pdlcPipelineOrchestrator = pdlcPipelineOrchestrator;
		this.pipelineStateManager = pipelineStateManager;
		this.playbookRegistry = playbookRegistry;
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

		// Stage 1: Resolve spark type — use request hint if provided, otherwise classify
		String sparkType = request.getSparkType();
		if (sparkType == null || sparkType.isBlank()) {
			sparkType = sparkClassifierService.classifySparkType(title, commandText);
			log.debug("Classified spark type='{}' for title='{}'", sparkType, title);
		}

		Spark spark = sparkService.createSpark(user.getUserId(), title, commandText, sparkType, null,
				null, null, null);

		// Route based on user's execution preference (smart defaults)
		UserConfig config = userConfigService.getConfig(user.getUserId());
		ExecutionPreference pref;
		if (config.getExecutionPreference() != null) {
			pref = config.getExecutionPreference();
		}
		else {
			// Smart default: cloud if no devices registered, device-first if devices exist
			boolean hasDevices = deviceRoutingService.hasRegisteredDevices(user.getUserId());
			pref = hasDevices ? ExecutionPreference.DEVICE_FIRST : ExecutionPreference.CLOUD_ONLY;
		}

		switch (pref) {
			case CLOUD_ONLY:
				// Never try device, go straight to cloud (with browser skills if enabled)
				log.info("Routing spark={} to cloud (CLOUD_ONLY preference)", spark.getId());
				return ResponseEntity.ok(executeInCloudOrPipeline(request, user.getUserId(), spark, title, commandText, sparkType));

			case CLOUD_FIRST:
				// Try cloud first — agent has browser skills and cloud tools
				log.info("Routing spark={} to cloud first (CLOUD_FIRST preference)", spark.getId());
				return ResponseEntity.ok(executeInCloudOrPipeline(request, user.getUserId(), spark, title, commandText, sparkType));

			case DEVICE_FIRST:
			default:
				// Existing behavior: try device first, fall back to cloud
				if (deviceRoutingService.hasOnlineDevice(user.getUserId())) {
					Optional<AgentCommandResponse> delegated = delegateToDevice(spark, user.getUserId());
					if (delegated.isPresent()) {
						return ResponseEntity.ok(delegated.get());
					}
					// If routing failed (no device matched after preferences), fall through
				}
				// Fall back to cloud (now with browser skills available)
				return ResponseEntity.ok(executeInCloudOrPipeline(request, user.getUserId(), spark, title, commandText, sparkType));
		}
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

	/**
	 * Runs Stage 2 PDLC depth classification and routes to either the pipeline
	 * (PLAYBOOK / FULL_PDLC) or the synchronous CloudOrchestratorService (SIMPLE).
	 *
	 * <p>Pipeline routing:
	 * <ol>
	 *   <li>Classify depth via {@link PdlcClassifierService} (only runs for code/devops types).</li>
	 *   <li>If the request carries a user-specified {@code playbook} override, re-resolve the
	 *       {@link PlaybookConfig} from the registry and use the overridden classification.</li>
	 *   <li>SIMPLE → existing {@link CloudOrchestratorService} synchronous path.</li>
	 *   <li>PLAYBOOK / FULL_PDLC → create a {@link PipelineRun} and dispatch asynchronously
	 *       to {@link PdlcPipelineOrchestrator}; return an async PIPELINE response immediately.</li>
	 * </ol>
	 * </p>
	 */
	private AgentCommandResponse executeInCloudOrPipeline(AgentCommandRequest request, String userId,
			Spark spark, String title, String commandText, String sparkType) {

		// Stage 2: PDLC depth classification
		PdlcClassification classification = pdlcClassifierService.classifyDepth(title, commandText, sparkType);

		// User playbook override takes precedence over classifier result
		String playbookOverride = request.getPlaybook();
		if (playbookOverride != null && !playbookOverride.isBlank()) {
			Optional<PlaybookConfig> overrideConfig = playbookRegistry.getPlaybook(playbookOverride.toUpperCase());
			if (overrideConfig.isPresent()) {
				PlaybookConfig pb = overrideConfig.get();
				log.info("User override: playbook='{}' tier={} for spark={}", pb.name(), pb.tier(), spark.getId());
				// Re-build classification using the override playbook's tier and roles
				classification = new PdlcClassification(
						pb.tier(),
						pb.name(),
						1.0, // user-specified → maximum confidence
						classification.activatedRoles().isEmpty()
								? pb.stages().stream().map(s -> s.role()).toList()
								: classification.activatedRoles(),
						classification.skippedRoles(),
						classification.dimensionScores(),
						"User-specified playbook override: " + pb.name());
			}
			else {
				log.warn("Unknown playbook override '{}' for spark={}, ignoring", playbookOverride, spark.getId());
			}
		}

		// Merge role skip sources: NL keywords + API field
		Set<PdlcRole> nlSkipRoles = RoleSkipParser.parse(commandText);
		Set<PdlcRole> apiSkipRoles = parseApiSkipRoles(request.getSkipRoles());
		Set<PdlcRole> effectiveSkipRoles = new HashSet<>(nlSkipRoles);
		effectiveSkipRoles.addAll(apiSkipRoles);

		if (!effectiveSkipRoles.isEmpty() && classification.tier() != PipelineTier.SIMPLE) {
			List<PdlcRole> filteredRoles = new ArrayList<>(classification.activatedRoles());
			filteredRoles.removeAll(effectiveSkipRoles);

			// Union original skippedRoles with user-skipped roles (preserve classifier context)
			List<PdlcRole> newSkippedRoles = new ArrayList<>(
					classification.skippedRoles() != null ? classification.skippedRoles() : List.of());
			for (PdlcRole skip : effectiveSkipRoles) {
				if (!newSkippedRoles.contains(skip)) {
					newSkippedRoles.add(skip);
				}
			}

			classification = new PdlcClassification(
					classification.tier(),
					classification.playbook(),
					classification.confidence(),
					filteredRoles,
					newSkippedRoles,
					classification.dimensionScores(),
					classification.reasoning() + " [User skipped: " + effectiveSkipRoles + "]");
			log.info("[PIPELINE] User skipped roles {} for spark={}", effectiveSkipRoles, spark.getId());
		}

		// Soft guardrail: warn if skipping required roles (Task 9)
		List<String> requiredSkipped = List.of();
		if (!effectiveSkipRoles.isEmpty()) {
			Optional<PlaybookConfig> pbCheck = playbookRegistry.getPlaybook(
					classification.playbook() != null ? classification.playbook() : "");
			if (pbCheck.isPresent()) {
				requiredSkipped = pbCheck.get().stages().stream()
						.filter(stage -> stage.required() && effectiveSkipRoles.contains(stage.role()))
						.map(stage -> stage.role().name())
						.toList();
				if (!requiredSkipped.isEmpty()) {
					log.warn("[PIPELINE] User is skipping required roles {} for spark={}",
							requiredSkipped, spark.getId());
				}
			}
		}

		// Route based on classification tier
		if (classification.tier() == PipelineTier.SIMPLE) {
			// Existing synchronous CloudOrchestratorService path
			AgentCommandResponse response = executeInCloud(request, userId, spark);
			response.setPipelineTier(PipelineTier.SIMPLE.name());
			response.setExecutionMode("SYNC");
			return response;
		}

		// PLAYBOOK or FULL_PDLC — look up the PlaybookConfig and dispatch async
		Optional<PlaybookConfig> playbookConfigOpt = playbookRegistry.getPlaybook(classification.playbook());
		if (playbookConfigOpt.isEmpty()) {
			log.warn("[PIPELINE] Playbook '{}' not found in registry for spark={}, falling back to SIMPLE",
					classification.playbook(), spark.getId());
			AgentCommandResponse response = executeInCloud(request, userId, spark);
			response.setPipelineTier(PipelineTier.SIMPLE.name());
			response.setExecutionMode("SYNC");
			return response;
		}

		PlaybookConfig playbookConfig = playbookConfigOpt.get();
		log.info("[PIPELINE] Dispatching spark={} to pipeline tier={} playbook={} confidence={}",
				spark.getId(), classification.tier(), classification.playbook(), classification.confidence());

		PipelineRun pipelineRun = pipelineStateManager.createRun(
				spark.getId(), userId, playbookConfig, classification);

		// Set required-skip info BEFORE async dispatch to avoid race condition (Task 9)
		if (!requiredSkipped.isEmpty()) {
			pipelineStateManager.updateSkippedRequiredRoles(pipelineRun.getId(), requiredSkipped);
		}

		// Dispatch async — returns immediately; orchestrator runs on pdlcPipelineExecutor thread pool
		pdlcPipelineOrchestrator.executePipeline(pipelineRun.getId());

		List<String> activatedRoleNames = pipelineRun.getActivatedRoles() != null
				? pipelineRun.getActivatedRoles().stream().map(Enum::name).toList()
				: List.of();

		return AgentCommandResponse.pipeline(
				spark.getId(),
				pipelineRun.getId(),
				classification.tier().name(),
				classification.playbook(),
				activatedRoleNames);
	}

	/** Execute command in the cloud via CloudOrchestratorService, tracking with the given spark. */
	private AgentCommandResponse executeInCloud(AgentCommandRequest request, String userId, Spark spark) {
		String sessionId = request.getSessionId() != null ? request.getSessionId() : UUID.randomUUID().toString();
		String timezone = request.getTimezone() != null ? request.getTimezone() : "UTC";

		// Get connected platforms for system prompt context
		List<SocialIntegration> integrations = integrationRepository.findAllByUserId(userId);
		List<String> connectedPlatforms = integrations.stream()
			.filter(i -> !i.isDisabled())
			.map(i -> i.getPlatform().getDisplayName())
			.toList();

		// CloudOrchestratorService handles markRunning/markCompleted/markFailed internally
		CloudOrchestratorService.AgentResult result = cloudOrchestrator.execute(spark.getId(), request.getText(), userId,
				sessionId, connectedPlatforms, timezone, request.getModel());

		AgentCommandResponse response = new AgentCommandResponse(result.getResponseText(), result.getToolsInvoked(),
				result.isSuccess(), result.getModel());
		response.setSparkId(spark.getId());

		// Detect setup actions based on response text and user state
		List<AgentAction> actions = setupActionDetector.detect(result.getResponseText(), userId);
		if (!actions.isEmpty()) {
			response.setActions(actions);
		}

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
		confirmationRepository.save(action, user.getUserId());

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
			dto.setCreatedAt(logEntry.getCreatedDate() != null ? logEntry.getCreatedDate().toDate().toInstant() : null);
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

	/** Parses role names from the API {@code skipRoles} field into a {@link Set} of {@link PdlcRole}s. */
	private Set<PdlcRole> parseApiSkipRoles(List<String> skipRoles) {
		if (skipRoles == null || skipRoles.isEmpty()) return Set.of();
		EnumSet<PdlcRole> roles = EnumSet.noneOf(PdlcRole.class);
		for (String name : skipRoles) {
			try {
				roles.add(PdlcRole.valueOf(name.toUpperCase().trim()));
			}
			catch (IllegalArgumentException ignored) {
				log.warn("Unknown skip role: {}", name);
			}
		}
		return roles;
	}

}
