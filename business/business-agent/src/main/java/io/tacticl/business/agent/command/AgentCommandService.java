package io.tacticl.business.agent.command;

import io.cidadel.client.anthropic.AnthropicDirectClient;
import io.cidadel.client.base.llm.model.LlmMessage;
import io.cidadel.client.base.llm.model.LlmResponse;
import io.tacticl.business.pipeline.router.PdlcRouter;
import io.tacticl.business.sparks.service.SparkClassifierService;
import io.tacticl.business.sparks.service.SparkService;
import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.data.sparks.entity.Spark;
import io.tacticl.data.sparks.entity.SparkInitiatorSource;
import io.tacticl.data.sparks.entity.SparkRoute;
import io.tacticl.data.sparks.entity.SparkType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Single orchestration core for agent commands. Both the HTTP controller
 * (mobile/web) and Telegram in-process callers funnel through this bean so
 * spark creation, classification, pipeline routing, and the simple-cloud
 * fallback live in exactly one place.
 *
 * <p>Replaces the body of {@code AgentController.executeCommand} (now a thin
 * adapter) and the duplicated routing logic in
 * {@code TelegramSparkInitiator}.
 */
@Service
public class AgentCommandService {

    private static final Logger log = LoggerFactory.getLogger(AgentCommandService.class);

    private static final String DEFAULT_MODEL = "claude-sonnet-4-6";
    // Effectively uncapped during pre-production rollout — mirrored by ConversationService.
    private static final double DEFAULT_COST_CEILING_USD = 10_000.0;
    private static final String SYSTEM_PROMPT = """
            You are Tacticl, a personal AI assistant that can remote into devices and automate tasks.
            You help users with research, code, social media, content creation, data analysis, and more.
            Be concise, helpful, and action-oriented. When you can help directly, do so.
            """;

    private final SparkService sparks;
    private final SparkClassifierService classifier;
    private final AnthropicDirectClient anthropic;
    private final PdlcRouter pdlcRouter;

    public AgentCommandService(SparkService sparks,
                                SparkClassifierService classifier,
                                AnthropicDirectClient anthropic,
                                PdlcRouter pdlcRouter) {
        this.sparks = sparks;
        this.classifier = classifier;
        this.anthropic = anthropic;
        this.pdlcRouter = pdlcRouter;
    }

    public AgentCommandResult execute(AgentCommand cmd) {
        SparkInitiatorSource source = cmd.initiatorSource();
        Spark spark = sparks.create(cmd.userId(), cmd.text(), source, cmd.userId(), cmd.projectId());
        SparkType type = classifier.classify(cmd.text());
        spark = sparks.classify(spark.getId(), cmd.userId(), type);

        if (type == SparkType.CODE || type == SparkType.DEVOPS) {
            double ceiling = cmd.costCeilingUsd() != null ? cmd.costCeilingUsd() : DEFAULT_COST_CEILING_USD;
            Optional<PipelineRun> run = pdlcRouter.route(cmd.userId(), spark.getId(), cmd.text(),
                    cmd.repoUrl(), type, List.of(), null, ceiling);
            if (run.isPresent()) {
                sparks.markExecuting(spark.getId(), cmd.userId(), SparkRoute.CLOUD, null);
                log.info("[AgentCommand] Spark {} routed to PDLC run {}", spark.getId(), run.get().getId());
                return AgentCommandResult.pipeline(spark.getId(), run.get().getId(), "FULL_PDLC");
            }
            log.warn("[AgentCommand] PDLC disabled for code/devops spark {} type {}; failing explicitly",
                    spark.getId(), type);
            sparks.markFailed(spark.getId(), cmd.userId());
            return AgentCommandResult.pipelineDisabled(spark.getId());
        }

        spark = sparks.markExecuting(spark.getId(), cmd.userId(), SparkRoute.CLOUD, null);
        String model = cmd.model() != null ? cmd.model() : DEFAULT_MODEL;
        try {
            // generateContent(prompt, history, model) — fold the system prompt into a
            // synthesized user→assistant turn at the head of history (no native system field).
            List<LlmMessage> history = List.of(
                    LlmMessage.user("System instructions:\n" + SYSTEM_PROMPT),
                    LlmMessage.assistant("Understood. I will follow those instructions."));
            LlmResponse llm = anthropic.generateContent(cmd.text(), history, model);
            String text = llm != null && llm.getContent() != null ? llm.getContent() : "I processed your request.";
            int tokens = llm != null && llm.getTotalTokens() != null ? llm.getTotalTokens() : 0;
            sparks.markCompleted(spark.getId(), cmd.userId(), tokens, model);
            return AgentCommandResult.cloudCompleted(spark.getId(), text, model, tokens);
        } catch (Exception e) {
            log.error("[AgentCommand] Spark {} failed: {}", spark.getId(), e.getMessage(), e);
            sparks.markFailed(spark.getId(), cmd.userId());
            return AgentCommandResult.cloudFailed(spark.getId(),
                    "I couldn't process that right now. Please try again.");
        }
    }
}
