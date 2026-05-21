# Temporal / Workflow Specialist Critique

**Date:** 2026-05-20
**Persona:** Senior workflow-orchestration architect (production Temporal at scale)
**Topic:** Pressure-test the Temporal-based architecture proposed for Tacticl

## 1. Workflow shape: split it. One Spark = two workflows.

A single `SparkWorkflow` spanning intake and pipeline is the seductive wrong answer. The cadences are incompatible in ways that hurt operationally, not just aesthetically:

- **Intake is human-paced and may idle for days.** A user starts a Spark on Telegram, gets distracted, comes back Tuesday. That workflow sits in `Workflow.await` consuming a slot in your sticky cache and bloating your visible "Running" count.
- **Pipeline is system-paced and bursty.** When intake completes, you want pipeline scheduling decisions (cost ceiling, role overrides, playbook selection) to be a clean transactional boundary, not a `phase = PIPELINE_RUNNING` reassignment buried inside a long-running workflow.
- **History size diverges dramatically.** Intake at 30 turns × ~6 events/turn (signal received, activity scheduled/started/completed, marker, side-effect) ≈ 180 events. Pipeline at 12 roles × heartbeating long activities × retries × checkpoint signals ≈ 800–1500 events. Combined you blow past 4000 events on a bad day. Temporal's *soft* limit is 10K events / 50MB; you start getting warnings at 2K. Stripe's payments-orchestrator team learned this — they split into phase-scoped workflows precisely to keep histories debuggable.

**Recommendation:** `IntakeWorkflow` → on `finalize_spec`, call `Workflow.newExternalWorkflowStub` or simply have the final activity start `PipelineWorkflow` via the client. Parent/child if you want cancellation propagation; sibling-with-handoff if you don't. The PM-as-pipeline-role idea is fine *conceptually* — just don't conflate "PM is the first role" with "they share a workflow instance."

`ContinueAsNew` for intake should fire at ~500 events or ~200KB history. Don't wait for the limit; replay cost on worker restart is linear in history.

## 2. Activities: your retry policy will be where you bleed money

`generateNextTurn` is the dangerous one. A few specifics:

- **StartToClose: 90s.** Not 30s. Claude tool-use turns with large contexts genuinely take 45–60s p99.
- **Retry policy**: `initialInterval=2s, backoffCoefficient=2.0, maximumInterval=30s, maximumAttempts=4`. Mark `RateLimitedException`, `AuthException` as non-retryable via `doNotRetry`. The Anthropic 429 with `retry-after` header — your arbiter must translate that into an `ApplicationFailure` with a specific type and let Temporal's retry handle it, **not** sleep inside the activity. Sleeping inside an activity is the #1 way solo founders melt their worker pool.
- **No heartbeats on short activities.** Heartbeats below 10s are pure overhead. Heartbeat threshold should be ~1/3 of `heartbeatTimeout`.

`runPersonaContainer`:

- **heartbeatTimeout: 60s. StartToClose: 4h.** Heartbeat every 15s from the arbiter side. **Critical:** include the container ID in the heartbeat *details* — when the activity fails and Temporal retries, the next attempt reads heartbeat details and *adopts* the existing container instead of spawning a new one. Without this, a worker restart spawns a duplicate container that runs to completion eating $40 of Claude credit. Netflix's content-pipeline team published a great writeup of this exact pattern.
- **Cleanup on cancellation**: register a `Workflow.newCancellationScope` and have the activity respond to `ActivityExecutionContext.isCancelRequested()` by `docker kill`-ing the container. Otherwise your "cancel Spark" button orphans containers.
- **4-level dependency chain (workflow → activity → arbiter gRPC → docker)**: the first thing to fail will be the arbiter, and the symptom will be "activity hangs." Make sure arbiter gRPC has aggressive client-side deadlines (matching your activity StartToClose minus a buffer) and that gRPC keepalives are configured. Without that, a half-open TCP connection between platform-apps containers will hang for kernel-TCP-timeout (default 2h+) before Temporal notices.

## 3. Signals: you have a real race

5 Telegram messages in 2 seconds is not hypothetical, it's Tuesday. Your code does:

```java
var msg = Workflow.await(() -> !signalQueue.isEmpty());
msg = signalQueue.poll();
// ... 30s generateNextTurn ...
```

While `generateNextTurn` is running, signals queue. That's actually correct — Temporal serializes signal delivery. But your UX is now: user sends "actually make it Go not Python" *while* the LLM is generating the Python response. You either:

1. **Drain the queue after each turn** (batch all pending messages into next turn's context). This is what I'd do. Concatenate with timestamps.
2. **Cancel in-flight turn on new signal.** Possible via cancellation scope, but the activity completion still bills you for tokens. Net loss.

Do (1). It's also what Inngest's chat reference architecture does.

For queries: a `snapshot()` query at 2s × 100 Sparks = 50 QPS to the frontend. Temporal frontend handles tens of thousands of QPS but **query handlers re-execute workflow code on the worker that owns the sticky cache**. If your snapshot does anything non-trivial, you're burning worker CPU. Push snapshot data to Mongo on every state change instead and have the dashboard query Mongo. The query method should only exist as a debugging hatch.

Adding queries retroactively is safe — queries don't go in history. Adding **signal handlers** retroactively is the gotcha (workflow won't know about old signals if you rename them).

## 4. Versioning: this is where you'll lose a weekend

`Workflow.getVersion("step-name", DEFAULT_VERSION, 1)` discipline:

- **Any change to the order of activities, the set of activities called, or branching logic** inside the workflow needs a version gate. Renaming a local variable: fine. Adding an `if` that calls a new activity: version gate.
- **System prompts and tool schemas are activity inputs, not workflow code.** Store them in Mongo keyed by version, have the activity fetch them. Then prompt changes don't require workflow versioning at all. This is the single most important separation.
- **Keep old workflow code for `workflowExecutionRetentionPeriod` + your longest expected workflow runtime.** If you set retention to 30 days and intake can sit idle for 7 days, keep code paths for ~40 days minimum.

## 5. Failure modes that will actually page you

- **temporal-postgres dies**: you lose everything not in a completed Postgres transaction. Workflows resume from last persisted event on recovery. **Backups: pg_basebackup nightly + WAL archiving to MinIO.** Without WAL archiving you lose up to 24h of workflow progress. Solo founders skip this. Don't.
- **platform-apps restart**: workflows mid-activity get `ActivityWorkerShutdown` failure → retry on next worker. Workflows awaiting signal: zero impact, they're just history. This is Temporal's superpower.
- **temporal-server OOM**: workflows resume from history on restart. No data loss assuming Postgres survived. But your `auto-setup` image is *not* production — it runs schema migrations on startup. Move to the regular `temporalio/server` image with explicit schema management before you have real users.
- **Hung container with live heartbeat**: this is the worst case and your current design has no answer. Add a **business-level deadline** in workflow code: `Workflow.newTimer(Duration.ofHours(2))` raced against the activity. If timer wins, cancel activity. Heartbeats prove liveness, not progress.

## 6. Worker capacity

Defaults are wrong for you. Set `maxConcurrentActivityExecutionSize=50`, `maxConcurrentWorkflowTaskExecutionSize=20`, `maxConcurrentLocalActivityExecutionSize=20`. 100 concurrent Sparks with 50 idle in intake is trivially fine — idle workflows consume zero worker resources, only sticky cache slots (default 600, raise to 2000).

Second replica: works out of the box. Temporal handles worker identity. The gotcha is **shared resources behind activities** — if both replicas hit MongoDB connection pool limits, you'll see `ScheduleToStart` timeouts that look like Temporal problems but aren't.

## 7. Solo-founder reality

You will lose a full weekend to one of: (a) non-deterministic workflow code (`System.currentTimeMillis` instead of `Workflow.currentTimeMillis`), (b) versioning a workflow without `getVersion`, (c) accidentally blocking the workflow thread with a Mongo call you forgot to wrap in an activity.

**Day-1 insurance:** (1) The `tctl` / `temporal` CLI aliased and documented in your runbook. (2) A `replay-test` in CI that loads a recorded history JSON and re-runs workflow code against it — catches non-determinism before deploy. (3) Structured logging from workflow code via `Workflow.getLogger()` (not SLF4J directly) so logs survive replay correctly.

## 8. Heretical alternatives

- **Restate** is genuinely interesting here — durable execution with a much simpler mental model, single binary, journal-based. If you were greenfield with no Java investment I'd push hard. But your team is Java + Spring and Temporal's Java SDK is the most mature thing in the ecosystem.
- **Inngest** would be wrong — it's for event-driven workflows, not long-running stateful conversations.
- **Hand-rolled with Mongo + outbox**: viable for intake (it's just a state machine with signals), insane for the 12-role pipeline. Don't mix.
- **Temporal is right for the pipeline. Temporal is overkill for intake.** A heretical-but-defensible split: intake as a plain Spring stateful service backed by Mongo with optimistic locking, pipeline as Temporal. Cleaner blast radius. The cost is two mental models.

---

**If I were the founder, here's the one thing I would change about this plan before writing a line of code:** split the workflow in two. `IntakeWorkflow` owns the conversation until `finalize_spec` is emitted, then starts a separate `PipelineWorkflow` with the frozen spec as input. The two workflows share a `sparkId` for correlation in Mongo, but their Temporal histories, retention policies, versioning cadence, and failure domains stay independent. You get cleaner replays, smaller histories, easier debugging at 3 AM, and — most importantly — the freedom to rewrite the intake plane (which will change weekly) without touching the pipeline plane (which must stay stable). Every team I've seen merge these two phases has un-merged them within six months. Skip the round trip.
