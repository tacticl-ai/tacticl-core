# AI Engine → Spark Orchestrator Integration — Design Spec

**Date**: 2026-03-20
**Status**: Approved
**Scope**: tacticl-core — wire AiEngineRouterService into all AI call paths

---

## Principle

**Every AI call goes through `AiEngineRouterService`.** No direct `LlmRouter` or `LlmProvider` calls anywhere. The router resolves the engine per SDLC step, the engine handles execution internally.

---

## Architecture

```
Caller (Controller, Service, etc.)
  → AiEngineRouterService.executeStep(sdlcStep, request)
    → AiEngineRegistry.getEngine(engineId)
      ├── AgenticApiAiEngine   → internal tool loop (LlmProvider + ToolRegistry)
      ├── ClaudeCodeAiEngine   → CLI subprocess (autonomous)
      └── CodexAiEngine        → CLI subprocess (autonomous)
```

`VoiceAgentService` becomes thin — resolves SDLC step, calls `engine.execute()`, tracks spark lifecycle. The agent loop (tool_use cycle) moves into `AgenticApiAiEngine`.

---

## Integration Points (3 files to change)

### 1. SparkClassifierService

**Current**: Calls `LlmRouter.generateContent()` with classification prompt.

**New**: Calls `AiEngineRouterService.executeStep("SPARK_CLASSIFICATION", request)`.

The request contains the classification prompt. The router resolves to the cheapest API engine (default: anthropic-api with Haiku). Result content is parsed for the spark type string.

### 2. VoiceAgentService

**Current**: Runs a multi-turn agent loop calling `LlmRouter.generateWithTools()`, executing tools via `ToolRegistry`, looping up to 5 rounds.

**New**:
1. Map spark type → SDLC step (via `AiSparkTypeStepMapper`)
2. Resolve engine via `AiEngineRouterService.resolve(step)`
3. Get engine from `AiEngineRegistry`
4. Call `engine.execute(request)` — single call, engine handles everything
5. Track spark lifecycle (markRunning, markCompleted)

The agent loop moves INTO `AgenticApiAiEngine`. VoiceAgentService no longer manages the loop.

### 3. AgentController

**Current**: Creates spark, then branches on ExecutionPreference (DEVICE_FIRST, CLOUD_ONLY, CLOUD_FIRST). Cloud path calls `VoiceAgentService.execute()`.

**Change**: Minimal — VoiceAgentService API signature stays the same. Controller doesn't need to know about engines.

---

## New Classes

### `AgenticApiAiEngine` (tacticl-specific, in `business-agent`)

**Package**: `io.strategiz.social.business.agent.ai`

An `AiEngine` implementation that wraps an `LlmProvider` + `ToolRegistry` and runs the multi-turn agent loop internally.

```java
public class AgenticApiAiEngine implements AiEngine {

    private final LlmProvider provider;
    private final ToolRegistry toolRegistry;
    private final AgentSystemPrompt systemPromptBuilder;
    private final String engineId;       // e.g. "anthropic-agentic"
    private final String displayName;
    private final int maxRounds;         // default 5

    @Override
    public AiEngineResult execute(AiEngineRequest request) {
        // 1. Build system prompt (from request.systemPrompt or systemPromptBuilder)
        // 2. Initialize messages with user prompt
        // 3. Agent loop (up to maxRounds):
        //    a) Call provider.generateWithTools(messages, model, tools, systemPrompt)
        //    b) If tool_use → execute tools via toolRegistry → add results → continue
        //    c) If end_turn → break
        // 4. Return AiEngineResult with final content, token counts, events
    }

    // Capabilities: TEXT_GENERATION, TOOL_USE, STREAMING
    // Cost tier: from constructor
    // isAvailable: true (API always available if provider is registered)
}
```

**Key difference from `ApiAiEngineAdapter`** (cidadel):
- `ApiAiEngineAdapter` is single-turn (one LLM call)
- `AgenticApiAiEngine` runs the full agent loop with tool execution
- `AgenticApiAiEngine` is tacticl-specific because it depends on `ToolRegistry` and `AgentSkill`

**Registration**: One bean per LLM provider that supports tool_use:
- `anthropic-agentic` (wraps AnthropicDirectClient)
- `openai-agentic` (wraps OpenAiDirectClient)
- `grok-agentic` (wraps GrokDirectClient)

These REPLACE the current `ApiAiEngineAdapter` beans from `AiEngineAdapterConfig`. The simple adapters stay registered for non-agentic steps (classification, commit messages).

### `AiSparkTypeStepMapper` (tacticl-specific, in `business-agent`)

Maps spark type (from classification) to the primary SDLC step for execution.

```java
public final class AiSparkTypeStepMapper {

    public static AiSdlcStep mapToStep(String sparkType) {
        return switch (sparkType != null ? sparkType.toLowerCase() : "code") {
            case "code"     -> AiSdlcStep.CODE_GENERATION;
            case "social"   -> AiSdlcStep.SOCIAL_CONTENT;
            case "research" -> AiSdlcStep.WEB_RESEARCH;
            case "devops"   -> AiSdlcStep.DEPLOYMENT_SCRIPT;
            case "creative" -> AiSdlcStep.CREATIVE_WRITING;
            case "data"     -> AiSdlcStep.CODE_ANALYSIS;
            default         -> AiSdlcStep.CODE_GENERATION;
        };
    }
}
```

---

## Updated AiSdlcStepDefaults

The defaults need to route agentic steps to `*-agentic` engine IDs (not `*-api`):

| Step | Engine ID | Model |
|---|---|---|
| SPARK_CLASSIFICATION | `anthropic-api` | claude-haiku-4-5 |
| TASK_DECOMPOSITION | `anthropic-api` | claude-sonnet-4-5 |
| CODE_GENERATION | `claude-code-cli` | claude-opus-4-6 |
| CODE_REVIEW | `anthropic-agentic` | claude-sonnet-4-5 |
| CODE_REFACTORING | `claude-code-cli` | claude-sonnet-4-5 |
| BUG_DIAGNOSIS | `claude-code-cli` | claude-sonnet-4-5 |
| BUG_FIX | `claude-code-cli` | claude-opus-4-6 |
| TEST_GENERATION | `claude-code-cli` | claude-sonnet-4-5 |
| TEST_EXECUTION | `claude-code-cli` | claude-sonnet-4-5 |
| PR_DESCRIPTION | `anthropic-api` | claude-sonnet-4-5 |
| DOCUMENTATION | `anthropic-api` | claude-sonnet-4-5 |
| COMMIT_MESSAGE | `anthropic-api` | claude-haiku-4-5 |
| WEB_RESEARCH | `codex-cli` | gpt-5.4 |
| CODE_ANALYSIS | `anthropic-agentic` | claude-sonnet-4-5 |
| SOCIAL_CONTENT | `anthropic-agentic` | claude-sonnet-4-5 |
| CREATIVE_WRITING | `anthropic-agentic` | claude-sonnet-4-5 |
| IMAGE_ANALYSIS | `anthropic-api` | claude-sonnet-4-5 |
| DEPLOYMENT_SCRIPT | `claude-code-cli` | claude-sonnet-4-5 |
| MONITORING_ANALYSIS | `anthropic-agentic` | claude-sonnet-4-5 |

Steps that need tool execution (code review, social content, creative writing, monitoring) use `*-agentic`. Steps that are pure text generation (classification, PR description, commit message) use `*-api`.

---

## Refactored VoiceAgentService

```java
@Service
public class VoiceAgentService extends BaseService {

    private final AiEngineRouterService aiEngineRouterService;
    private final AiEngineRegistry aiEngineRegistry;
    private final SparkService sparkService;
    private final AgentSystemPrompt systemPromptBuilder;
    private final AgentAuditLogRepository auditLogRepository;

    public AgentResult execute(String sparkId, String commandText, String userId,
                               String sessionId, List<String> connectedPlatforms,
                               String timezone, String modelOverride) {

        sparkService.markRunning(sparkId);

        // 1. Get spark to determine type
        Spark spark = sparkService.getSpark(sparkId, userId);

        // 2. Map spark type → SDLC step
        AiSdlcStep step = AiSparkTypeStepMapper.mapToStep(spark.getType());

        // 3. Resolve engine config
        AiStepEngineConfig stepConfig = aiEngineRouterService.resolve(step.name());

        // 4. Get engine
        AiEngine engine = aiEngineRegistry.getEngine(stepConfig.getEngineId())
                .orElseThrow(() -> new AiEngineUnavailableException(step.name(), stepConfig.getEngineId(), "Engine not available"));

        // 5. Build request
        AiEngineRequest request = new AiEngineRequest();
        request.setPrompt(commandText);
        request.setSystemPrompt(systemPromptBuilder.buildSystemPrompt(userId, connectedPlatforms, timezone));
        request.setModel(modelOverride != null ? modelOverride : stepConfig.getModel());
        request.setMetadata(Map.of("sparkId", sparkId, "userId", userId, "sessionId", sessionId));

        // 6. Execute
        AiEngineResult result = engine.execute(request);

        // 7. Track completion
        sparkService.markCloudCompleted(sparkId, result.getTotalTokens(), result.getModel());

        // 8. Audit log
        logAudit(userId, commandText, result);

        // 9. Return
        return new AgentResult(result.getContent(), List.of(), result.getModel(), result.isSuccess());
    }
}
```

---

## Refactored SparkClassifierService

```java
@Service
public class SparkClassifierService extends BaseService {

    private final AiEngineRouterService aiEngineRouterService;
    private final AiEngineRegistry aiEngineRegistry;

    public String classifySparkType(String title, String description) {
        AiStepEngineConfig config = aiEngineRouterService.resolve(AiSdlcStep.SPARK_CLASSIFICATION.name());
        AiEngine engine = aiEngineRegistry.getEngine(config.getEngineId()).orElseThrow();

        AiEngineRequest request = new AiEngineRequest();
        request.setPrompt(buildClassificationPrompt(title, description));
        request.setModel(config.getModel());

        AiEngineResult result = engine.execute(request);
        return parseClassification(result.getContent());
    }
}
```

---

## What Gets Removed

- `LlmRouter` injection from VoiceAgentService — replaced by `AiEngineRouterService` + `AiEngineRegistry`
- `LlmRouter` injection from SparkClassifierService — replaced similarly
- `AgentModelConfig` usage in VoiceAgentService — model comes from `AiStepEngineConfig`
- Direct tool execution loop in VoiceAgentService — moves into `AgenticApiAiEngine`

`LlmRouter` itself stays in cidadel-core as an internal detail. `ApiAiEngineAdapter` uses it. But no tacticl code calls it directly anymore.

---

## Testing Strategy

| Component | Test |
|---|---|
| `AgenticApiAiEngine` | Mock LlmProvider + ToolRegistry, verify agent loop (tool_use → execute → loop → end_turn) |
| `AiSparkTypeStepMapper` | All 6 spark types + null/unknown → correct SDLC steps |
| `VoiceAgentService` (refactored) | Mock AiEngineRouterService + AiEngineRegistry, verify spark lifecycle calls |
| `SparkClassifierService` (refactored) | Mock engine, verify classification prompt and parsing |
| `AiSdlcStepDefaults` (updated) | Verify agentic steps use `*-agentic` engines |
| Integration | E2E with real engine beans, verify full flow |
