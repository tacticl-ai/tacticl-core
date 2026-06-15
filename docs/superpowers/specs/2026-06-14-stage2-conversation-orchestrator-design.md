# Stage 2 — Persistent Claude Code Conversation Orchestrator (Design)

Source: 28-agent ultracode design workflow (2026-06-14): map existing → 5 candidate architectures →
adversarial critique (×3 lenses each) → synthesis. This is the recommended, code-grounded design.

## Vision
The conversation orchestrator **is a persistent Claude Code session** (one per conversation) that
feels like talking to Claude Code: durable context, **repo/context-aware** (it LOOKS before it acts),
gathers + clarifies requirements, proposes, **aligns**, and only then **dispatches** the PDLC build.
Reachable from Discord text (now), web, and voice (later) — one conversation, any channel. OAuth-only.
Replaces the one-shot `/pdlc` trigger + the per-turn `ConversationRunner` LLM tool-loop.

## Headline finding
The persistent primitive **already exists**: `cidadel-ai-arbiter/.../orchestrator/orchestrator-session.ts`
is a real held Claude Code SDK session (today for shell escalation). The installed SDK (`@anthropic-ai/claude-code` 1.0.128)
already exposes `canUseTool`, `mcpServers`, `includePartialMessages`/`stream_event`, `createSdkMcpServer`,
`resume`, per-query `env`, `interrupt()`. So the brain is a **generalization of existing code on the installed SDK** — no version bump for v1.

## Recommended architecture
- **Brain location:** in-process in the arbiter Node process (Option B), per-conversation generalization
  of `OrchestratorSession`. NOT docker-exec NdjsonSession (verified `--print` one-shot, no `--resume`/MCP),
  NOT the Java `client-claude-code` (no MCP/gate).
- **Session model:** one held `query()` per conversation in a `ConversationSessionRegistry`
  (`Map<conversationId, session>`); the held query owns durable context (no per-turn history re-send).
  **Do NOT set a finite `maxTurns`** (it's the whole conversation's lifetime budget, and `error_max_turns`
  is currently masked as success) — bound each turn with a wall-clock watchdog + `Query.interrupt()` + a
  tool-call counter. One query in flight per session (single-flight); inbound turns serialized via a
  per-conversation FIFO with coalescing.
- **Durability split:** brain context = held process + on-disk Claude transcript + captured
  `claudeSessionId` (resume on restart; on resume-miss re-prime from last-N transcript + persisted
  `alignmentState`, and **tell the user context was compacted** — fail loud). Builds = independent Temporal
  `pdlc-{runId}` that **outlive the conversation**. NO Temporal workflow for the conversation itself.
- **Wire unchanged:** keep the `ConverseTurn` proto verbatim; swap only the arbiter handler
  (`grpc-conversation-service.ts`) from `ConversationRunner` → a new `SessionBackedConverseHandler` behind a
  shared `ConverseHandler` interface (runner kept as a flagged fallback). Promote `session_id` → the routing
  key (`conversationId`), bound to one `tacticlUserId` in a NEW arbiter Mongo `conversation_sessions`
  collection (reject turns whose `user_id` ≠ owner — tenant safety). Raise/remove the hardcoded 60s gRPC
  deadline for the brain path.
- **The brain can LOOK:** `cwd` = a cloned, token-stripped working copy (reuse `WorkspaceAssembler`) +
  allowlisted built-in `Read/Grep/Glob/WebSearch`. Custom in-process SDK MCP tools: `run_status`,
  `create_repo` (reuse `RepoProvisioner`), `start_pipeline` (terminal). `permissionMode: 'default'` (NOT
  bypass), **disallow `Edit/Write/Bash`** — the brain LOOKS, the PDLC builders BUILD.
- **Alignment gate (not forgeable):** an in-process `canUseTool` callback denies `start_pipeline`/`create_repo`
  until server-tracked `alignmentState` (set by an **explicit human channel-turn confirmation**, persisted,
  bound to the specific proposed spark/repo) is true — not a model-supplied arg, not prompt-only.
- **Dispatch (reuse proven Stage-1 path):** `start_pipeline` is TERMINAL → tacticl-core re-enters
  `EXPLICIT_TRIGGER → requireAdmin → PdlcV2Service.submitPipeline → Temporal` (zero new dispatch code).
  Add a `decideExecutor(playbook)==='temporal'` pre-assert (no silent fall-to-legacy) + a **dispatch-ack
  synthetic turn** so the brain learns the real outcome (never lies "I started your build" on an admin denial).
- **Channel routing fix (tacticl-core):** replace the single `ConversationTurnHandler` bean (collision risk —
  `getIfAvailable()` throws on 2) with a `RoutingConversationTurnHandler` (`@Primary`) selecting a per-channel
  delegate by `RunOrigin.channel()`; each delegate reuses the same `ConversationEngine`, differing only in its
  reply-rendering `ConversationSink`.
- **Discord text (the one new transport):** a new `client-discord-gateway` (JDA/Discord4j WS) on
  `MESSAGE_CREATE` scoped to @mentions/replies (privileged **Message Content Intent**) → `CONVERSATION_TURN`
  → existing ingress. Outbound (Discord has no token stream): `createChannelMessage` placeholder + throttled
  `editChannelMessage` append (2000-char roll, edit rate-limit, typing keepalive). **Stopgap:** a `/chat <text>`
  slash command on the existing signed webhook (no privileged intent) — same brain/seam, only the receive shape differs.
- **Voice (Stage 3, additive):** same session; `token` deltas → ElevenLabs, `tool_use` → sphere, barge-in →
  `interrupt()`. The `includePartialMessages` streaming bridge is built once for Discord and inherited by voice.

## Build plan
- **Phase 0 — preconditions:** re-auth/reseed Anthropic Max OAuth (brain 401s at boot if dead); decide the
  conversation OAuth account; confirm `PDLC_TEMPORAL_ENABLED` + playbook in `PDLC_TEMPORAL_PIPELINES`; pin/test
  the SDK 1.0.128 stream/tool JSONL shapes.
- **Phase 1 — arbiter brain core (TS):** `ConversationBrainSession` (MCP + canUseTool + streaming, default
  perm mode, allow/deny lists, cwd=clone, OAuth via per-query `options.env` NOT global env, no maxTurns +
  watchdog/interrupt); `ConversationSessionRegistry` (FIFO, idle-evict, resume+re-prime); streaming bridge;
  in-process MCP (`start_pipeline`/`run_status`/`create_repo`); alignment gate; boot prompt
  (gather→clarify→propose→align→dispatch); `SessionBackedConverseHandler` behind a flag. Prove on the existing
  voice/web `CONVERSATION_TURN` path first.
- **Phase 2 — tacticl-core wiring (Java, minimal):** raise the 60s deadline; `RoutingConversationTurnHandler`
  (@Primary) + per-channel delegates; dispatch-ack synthetic turn; `history[]` becomes seed-only.
- **Phase 3 — Discord text (ship target):** `client-discord-gateway` + `DiscordConversationSink` (progressive
  edit) — or the `/chat` stopgap.
- **Phase 4 — voice (additive).** **Phase 5 — hardening:** `ORCHESTRATOR_MAX_LIVE` + idle-evict sized to the
  swapless host, per-conversation token/wall-clock budgets, real cost accounting, egress lockdown, transcript
  redaction, conv-* naming firewall vs the PDLC reaper.

## Guardrails (security)
Untrusted input = DATA never instructions · alignment gate (server-tracked, not forgeable) · `requireAdmin`
is the hard dispatch boundary (a tricked brain still can't build for a non-admin) · tool scoping (default perm
mode, no Edit/Write/Bash, token-stripped clone) · OAuth token via per-query env (no cross-conversation bleep),
outside cwd, never in transcripts, egress lockdown (brain has WebSearch) · short-lived/scoped GitHub PAT only at
the dispatch boundary (never into the brain) · `decideExecutor==='temporal'` (no silent legacy) · resume-loss =
fail loud · dedup on messageId + turn_id + REJECT_DUPLICATE.

## Top risks
Dead OAuth grant (brain 401s at boot — hard go-live gate) · depleted Sonnet quota → Haiku default weakens the
propose/clarify that IS the differentiator · RAM/OOM on the swapless host (held process per conversation vs PDLC
builders) · resume unproven across frequent arbiter redeploys · voice first-token latency vs p50<1200ms · Discord
gateway reopens the "no gateway" decision + privileged intent + new WS failure domain · streaming bridge is
net-new + load-bearing.

## Decisions (locked 2026-06-14)
1. **Discord receive = FULL GATEWAY bot now** (free-form @mention chat; needs Message Content Intent + a new WS bot). Not the `/chat` stopgap.
2. **Brain account = SHARE the primary Max account** (no new account). Caveat accepted: Sonnet quota depleted → brain runs on Haiku until quota recovers, and competes with the user's own Claude usage. Revisit (dedicated account) if proposals feel shallow.
3. **Conversation = SHARED + collaborative** (multiple linked users contribute to ONE project effort), NOT per-user. v1 = **per-channel** shared conversation; evolve to **per-thread = per-effort** (each thread → one conversation → one pipeline) for concurrent efforts. `requireLinked` to contribute, `requireAdmin` to dispatch; turns are user-attributed.
4. Cross-channel identity: per-channel for v1 (defer unified Discord+voice session).
5. Knowledge RAG: session-level (boot) for v1.
6. `ORCHESTRATOR_MAX_LIVE` + idle-TTL: measure per-session RAM on the host before sizing.

## Open decisions (for the user) — see chat
1. Brain OAuth account: share primary vs dedicated conversation account.
2. Discord receive: full gateway (free-form @mention chat) vs `/chat` slash stopgap.
3. Conversation scope: per-channel vs per-(channel,user).
4. Cross-channel identity: one unified session across Discord/voice vs separate per channel.
5. Model under depleted Sonnet: ship on Haiku now vs restore Sonnet/dedicated account.
6. Knowledge RAG: session-level boot-only (recommended v1) vs per-turn Qdrant query.
7. `ORCHESTRATOR_MAX_LIVE` + idle-TTL: need a measured per-session RAM budget on the host.
