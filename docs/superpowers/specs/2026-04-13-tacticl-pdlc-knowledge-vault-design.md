---
name: Tacticl PDLC Knowledge Vault Design
description: Obsidian vault + Karpathy LLM Wiki pattern for PDLC agent knowledge — replaces Layers 2+3 with a git-managed, self-learning knowledge base
type: engineering-spec
status: approved
date: 2026-04-13
author: Gabriel Jimenez
related-docs:
  - 2026-04-11-tacticl-pdlc-v2-sad.md
  - 2026-04-11-tacticl-pdlc-v2-prd.md
---

# Tacticl PDLC Knowledge Vault — Design

**Date:** 2026-04-13
**Status:** Approved
**Author:** Gabriel Jimenez

---

## Problem

The current PDLC knowledge system has three weaknesses:

1. **Layer 2 (authored knowledge)** is static — engineers manually update flat markdown files, no structure, no cross-linking. Agents re-discover the same codebase conventions every run.
2. **Layer 3 (learned patterns)** is a flat `patterns.md` file assembled from MongoDB entries. No connections between learnings. Agents cannot navigate from one concept to related ones. Every learning requires human approval before agents benefit — slow cycle.
3. **No continuous self-improvement** — the system does not grow smarter between pipeline runs without manual intervention.

---

## Solution

A **git-managed Obsidian vault** (`tacticl-knowledge` repo) that replaces Layers 2+3 entirely. Structured using Karpathy's LLM Wiki pattern: the RETRO_ANALYST role maintains the vault after every pipeline run, continuously writing new knowledge, updating backlinks, and running health checks.

Two-tier autonomy: safe, factual content auto-commits immediately; higher-risk learnings queue for human approval.

---

## Vault Repository

**Repo:** `tacticl-knowledge` (new dedicated GitHub repo, Obsidian vault root)

The vault is separate from `tacticl-docs` to keep pipeline knowledge isolated from engineering documentation. The `WorkspaceAssembler` clones relevant sections of this repo into each agent workspace at assembly time — fresh on every role dispatch.

---

## Vault Structure

```
tacticl-knowledge/
├── .obsidian/                    ← Obsidian config (graph view, templates, plugins)
├── schema.md                     ← RETRO_ANALYST instructions: how to write wiki pages
├── raw/                          ← IMMUTABLE — agents read only, never write
│   ├── role-templates/           ← boot.md templates per role (source of truth)
│   ├── run-summaries/            ← RETRO_ANALYST post-run summaries (one per pipeline run)
│   └── spark-archives/           ← past spark descriptions + outcomes + playbooks used
├── wiki/
│   ├── auto/                     ← RETRO_ANALYST auto-commits (safe, factual content)
│   │   ├── conventions/          ← codebase conventions (imports, naming, structure)
│   │   ├── entities/             ← key modules, services, data models, endpoints
│   │   └── moc/                  ← Maps of Content — one per PDLC role (agent entry points)
│   └── proposed/                 ← queued for human approval (higher-risk learnings)
│       ├── patterns/             ← generalized patterns from successful runs
│       ├── decisions/            ← architectural decision records
│       └── gotchas/              ← failure patterns and how to avoid them
└── approved/                     ← human-approved learnings (moved from proposed/)
    ├── patterns/
    ├── decisions/
    └── gotchas/
```

---

## Role MOCs (Maps of Content)

Each PDLC role gets a dedicated MOC at `wiki/auto/moc/{role}-guide.md`. This is the agent's curated entry point into the vault — the first file it reads. It contains `[[wikilinks]]` to every relevant knowledge page for that role. RETRO_ANALYST updates MOC files whenever it adds pages that are relevant to a role.

**Example: `wiki/auto/moc/implementer-guide.md`**

```markdown
# IMPLEMENTER — Map of Content

## Codebase Conventions
- [[conventions/jackson-3-imports]] — always use tools.jackson.*, never com.fasterxml
- [[conventions/gradle-module-structure]] — service/business/data/client layering rules
- [[conventions/naming-patterns]] — BaseService, BaseController, BaseEntity patterns
- [[conventions/constructor-injection]] — no @Autowired on fields, constructor only
- [[conventions/optional-return]] — return Optional<T> for queries, never null

## Architecture Decisions
- [[decisions/auth-paseto]] — why PASETO over JWT, shared key with Strategiz
- [[decisions/firestore-hybrid-schema]] — nested vs flat collection rules
- [[decisions/jackson-3-migration]] — why tools.jackson.* and what changed

## Patterns That Work
- [[approved/patterns/spring-boot-tdd]] — test-first approach for Spring Boot services
- [[approved/patterns/multi-candidate-selection]] — how CRITIC evaluates candidates

## Gotchas
- [[approved/gotchas/vault-https-localhost]] — Vault uses HTTPS on localhost, not HTTP
- [[approved/gotchas/subcollection-userid-param]] — findById requires userId param
- [[approved/gotchas/junit-bom-managed]] — do NOT pin JUnit versions, Boot BOM manages them
```

Each linked page is a standalone atomic note that can be read independently.

---

## Wiki Page Format (Atomic Notes)

Every wiki page follows the schema defined in `schema.md`. RETRO_ANALYST MUST follow this format — health checks enforce it.

```markdown
---
tags: [convention|pattern|decision|gotcha|entity]
roles: [IMPLEMENTER, REVIEWER]        ← which roles this page is relevant to
auto-approved: true|false
created: YYYY-MM-DD
last-updated: YYYY-MM-DD
pipeline-run: run-abc123              ← which run surfaced this knowledge
---

# {Title}

## What
One sentence: what is this?

## Why
Why does this matter? What goes wrong if ignored?

## How
Concrete instructions. Code examples where applicable.

## Example
```java
// correct
import tools.jackson.databind.json.JsonMapper;

// wrong — will not compile with Jackson 3
import com.fasterxml.jackson.databind.ObjectMapper;
```

## Related
- [[conventions/naming-patterns]]
- [[decisions/jackson-3-migration]]
```

---

## The Schema (`schema.md`)

Karpathy-style rules RETRO_ANALYST follows when writing and maintaining the vault. This file is included in the RETRO_ANALYST's workspace at every run.

**Key rules:**
- **Atomic notes** — one concept per file, no multi-topic pages
- **Required sections** — every page must have: What, Why, How, Example, Related
- **Backlinks mandatory** — every new page must add `[[this-page]]` to all linked pages under their `## Related` section
- **AUTO eligible criteria** — all of: (a) factual/convention-based, (b) ≥3 pipeline runs exhibiting same behavior, (c) no human override on a prior version of this page
- **PROPOSE required** — any of: (a) reverses or contradicts a prior approved learning, (b) security-related pattern, (c) changes how a role approaches a core task, (d) architectural decision
- **Health check required** — after every write session, RETRO_ANALYST scans for: orphaned links (pages linked but not found), missing backlinks, pages with incomplete sections, contradictions between auto/ and approved/
- **Tone** — written for an AI agent, not a human. Explicit, unambiguous, examples over prose. No "consider" or "may want to" — only "do" and "do not".

---

## Self-Learning Loop

The RETRO_ANALYST role is extended with vault maintenance responsibilities. After every completed pipeline:

```
Pipeline COMPLETED / FAILED
    → RETRO_ANALYST container spawned
    → Reads: all Tier 2 artifacts from this run (phase reports, test results, review notes, security audit)
    → Reads: raw/run-summaries/ (prior runs for context)
    → For each learning identified:
        → Classify: AUTO or PROPOSE
        → AUTO → write/update page in wiki/auto/ → commit immediately
        → PROPOSE → write draft page in wiki/proposed/ → commit, await approval
    → Update MOC files for affected roles
    → Run health check (orphaned links, missing backlinks, contradictions)
    → Write run summary to raw/run-summaries/{runId}.md
    → Commit all changes to tacticl-knowledge via GitHub
    → (Existing) Write proposed learnings to MongoDB agent_knowledge [TRANSITIONAL — removed once vault is stable]

Next pipeline assembly:
    → WorkspaceAssembler clones tacticl-knowledge repo
    → Copies wiki/auto/moc/{role}-guide.md + all linked pages into /workspace/knowledge/wiki/
    → Copies approved/ into /workspace/knowledge/approved/
    → Agent reads MOC as first knowledge step
```

---

## Human Approval Flow

When RETRO_ANALYST writes to `wiki/proposed/`, it:
1. Creates the page with `auto-approved: false` in frontmatter
2. Commits to `tacticl-knowledge` repo on branch `proposed/{runId}-{slug}`
3. Opens a GitHub PR titled `[Knowledge] {title}` targeting `main`
4. Tacticl-core receives the GitHub webhook, creates an `agent_knowledge_review` record in Firestore
5. User reviews in Obsidian (open vault locally) OR via the PR diff
6. Approve → merge PR → page moves to `approved/` folder
7. Reject → close PR → page deleted from `proposed/`

This replaces the current MongoDB `agent_knowledge` proposed/approved flow with a Git-native review process visible in Obsidian's graph view.

---

## Architecture Changes

### New: `tacticl-knowledge` Repo

Standalone GitHub repo. Obsidian vault. Managed by RETRO_ANALYST and engineering team.

### Updated: `WorkspaceAssembler`

**Before:**
```
copy authored knowledge files → /workspace/knowledge/authored/
render MongoDB learnings     → /workspace/knowledge/learned/patterns.md
```

**After:**
```
git clone tacticl-knowledge (sparse checkout: wiki/auto/moc/{role}/ + all linked pages + approved/)
    → /workspace/knowledge/wiki/
```

Sparse checkout: only pull pages relevant to the current role (linked from its MOC). Not the entire vault.

### Updated: RETRO_ANALYST Boot Template

Extended `boot.md` for RETRO_ANALYST includes:
- Full vault maintenance instructions
- Reference to `schema.md` (must follow all rules)
- `git clone tacticl-knowledge` as first step
- Classification criteria (AUTO vs PROPOSE)
- Health check procedure
- `git push` + PR creation as final steps

### Removed: MongoDB `agent_knowledge` (Layer 3)

No longer needed for agent knowledge delivery. The vault replaces it. MongoDB keeps `pipeline_runs`, `pipeline_events`, `checkpoints` — only `agent_knowledge` collection is retired.

### Simplified: Qdrant (Layer 4)

Qdrant's role narrows: instead of indexing full role outputs from past runs, it indexes the vault's `wiki/` content (curated, smaller corpus). Agents still call `find_similar_runs()` but it now searches curated knowledge pages, not raw role outputs. This is more precise and smaller to maintain.

---

## What Agents Experience

An IMPLEMENTER workspace now contains:

```
/workspace/knowledge/wiki/
├── auto/
│   ├── moc/
│   │   └── implementer-guide.md      ← entry point: agent reads this first
│   ├── conventions/
│   │   ├── jackson-3-imports.md
│   │   ├── gradle-module-structure.md
│   │   └── naming-patterns.md
│   └── entities/
│       ├── spark-entity.md
│       └── pipeline-run-entity.md
└── approved/
    ├── patterns/
    │   └── spring-boot-tdd.md
    └── gotchas/
        ├── vault-https-localhost.md
        └── subcollection-userid-param.md
```

The agent reads `implementer-guide.md` first (the MOC), follows `[[wikilinks]]` to pages it needs, and has rich, connected, up-to-date knowledge — without having to re-discover it from the codebase.

---

## Success Criteria

- Every pipeline run produces at least 1 vault commit (run summary + any learnings)
- Wiki grows at a rate of ≥3 auto-committed pages per 10 pipeline runs
- Agents stop re-discovering the same codebase conventions (measurable: fewer "wrong import" rework iterations)
- RETRO_ANALYST health checks report 0 orphaned links within 5 runs of vault initialization
- Human approval queue (proposed/) stays under 10 pending pages at any time

---

## Sources

- [Karpathy LLM Wiki Gist](https://gist.github.com/karpathy/442a6bf555914893e9891c11519de94f)
- [VentureBeat: Karpathy LLM Knowledge Base](https://venturebeat.com/data/karpathy-shares-llm-knowledge-base-architecture-that-bypasses-rag-with-an)
- [obsidian-wiki: Framework for AI agents (Karpathy pattern)](https://github.com/Ar9av/obsidian-wiki)
- [LLM Wiki v2 — extending Karpathy's pattern](https://gist.github.com/rohitg00/2067ab416f7bbe447c1977edaaa681e2)
