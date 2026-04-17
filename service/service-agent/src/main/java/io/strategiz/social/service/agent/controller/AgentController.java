package io.strategiz.social.service.agent.controller;

import io.cidadel.client.anthropic.AnthropicDirectClient;
import io.cidadel.client.base.llm.model.LlmMessage;
import io.cidadel.client.base.llm.model.LlmResponse;
import io.cidadel.framework.authorization.annotation.AuthUser;
import io.cidadel.framework.authorization.annotation.RequireAuth;
import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.tacticl.business.pipeline.router.PdlcRouter;
import io.tacticl.business.sparks.service.SparkClassifierService;
import io.tacticl.business.sparks.service.SparkService;
import io.tacticl.data.sparks.entity.Spark;
import io.tacticl.data.sparks.entity.SparkRoute;
import io.tacticl.data.sparks.entity.SparkType;
import io.strategiz.social.service.agent.dto.AgentCommandRequest;
import io.strategiz.social.service.agent.dto.AgentCommandResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for the cloud agent orchestrator. */
@RestController
@RequestMapping("/v1/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private static final String AGENT_MODEL = "claude-sonnet-4-6";

    private static final String AGENT_SYSTEM_PROMPT = """
            You are Tacticl, a personal AI assistant that can remote into devices and automate tasks.
            You help users with research, code, social media, content creation, data analysis, and more.
            Be concise, helpful, and action-oriented. When you can help directly, do so.
            """;

    private final SparkService sparkService;
    private final SparkClassifierService sparkClassifierService;
    private final AnthropicDirectClient anthropicClient;
    private final PdlcRouter pdlcRouter;

    public AgentController(SparkService sparkService,
                           SparkClassifierService sparkClassifierService,
                           AnthropicDirectClient anthropicClient,
                           PdlcRouter pdlcRouter) {
        this.sparkService = sparkService;
        this.sparkClassifierService = sparkClassifierService;
        this.anthropicClient = anthropicClient;
        this.pdlcRouter = pdlcRouter;
    }

    @PostMapping("/command")
    @RequireAuth
    public ResponseEntity<AgentCommandResponse> executeCommand(
            @Valid @RequestBody AgentCommandRequest request,
            @AuthUser AuthenticatedUser user) {
        String userId = user.getUserId();
        String text = request.getText();
        log.info("Agent command from user {}: {}", userId,
                text.length() > 100 ? text.substring(0, 100) + "..." : text);

        // Create and classify spark
        Spark spark = sparkService.create(userId, text);
        SparkType type = sparkClassifierService.classify(text);
        spark = sparkService.classify(spark.getId(), userId, type);

        // Route CODE/DEVOPS sparks to PDLC v2 (arbiter gRPC) when enabled
        if (type == SparkType.CODE || type == SparkType.DEVOPS) {
            var runOpt = pdlcRouter.route(userId, spark.getId(), text, null, type, List.of(), null, 50.0);
            if (runOpt.isPresent()) {
                var run = runOpt.get();
                sparkService.markExecuting(spark.getId(), userId, SparkRoute.CLOUD, null);
                log.info("[PDLC-V2] Routed spark={} type={} to pipeline run={}", spark.getId(), type, run.getId());
                return ResponseEntity.ok(AgentCommandResponse.pipeline(
                        spark.getId(), run.getId(), "FULL_PDLC", "FULL_PDLC", List.of()));
            }
        }

        // Simple cloud path (non-PDLC types, or v2 disabled)
        spark = sparkService.markExecuting(spark.getId(), userId, SparkRoute.CLOUD, null);

        try {
            // Call Anthropic for the response
            String modelToUse = request.getModel() != null ? request.getModel() : AGENT_MODEL;
            List<LlmMessage> messages = List.of(LlmMessage.user(text));
            LlmResponse llmResponse = anthropicClient.generateContent(modelToUse, messages, AGENT_SYSTEM_PROMPT);

            String responseText = llmResponse != null && llmResponse.getContent() != null
                    ? llmResponse.getContent()
                    : "I processed your request.";

            int tokenCost = llmResponse != null && llmResponse.getTotalTokens() != null
                    ? llmResponse.getTotalTokens()
                    : 0;

            sparkService.markCompleted(spark.getId(), userId, tokenCost, modelToUse);

            AgentCommandResponse response = new AgentCommandResponse(responseText, List.of(), true, modelToUse);
            response.setSparkId(spark.getId());
            response.setSparkStatus("COMPLETED");
            response.setExecutionMode("SYNC");
            response.setPipelineTier("SIMPLE");
            return ResponseEntity.ok(response);

        } catch (Exception ex) {
            log.error("Agent command failed for user {}: {}", userId, ex.getMessage(), ex);
            sparkService.markFailed(spark.getId(), userId);

            AgentCommandResponse response = new AgentCommandResponse(
                    "I couldn't process that right now. Please try again.", List.of(), false, null);
            response.setSparkId(spark.getId());
            response.setSparkStatus("FAILED");
            return ResponseEntity.ok(response);
        }
    }

    @GetMapping("/history")
    @RequireAuth
    public ResponseEntity<List<?>> getHistory(@AuthUser AuthenticatedUser user) {
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/models")
    @RequireAuth
    public ResponseEntity<List<String>> getAvailableModels() {
        return ResponseEntity.ok(List.of(
                "claude-sonnet-4-6",
                "claude-opus-4-6",
                "claude-haiku-4-5-20251001"
        ));
    }
}
