# Knowledge Infrastructure: Qdrant + tacticl-knowledge Indexer Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up a Qdrant vector store with product + user namespacing, an indexer that syncs tacticl-knowledge markdown files into Qdrant on git push, and a query interface Arbiter uses at workspace assembly time to retrieve relevant knowledge chunks.

**Architecture:** Two-tier knowledge store. Tier 1 (authored static): tacticl-knowledge markdown files → indexed into Qdrant collection `tacticl_knowledge` with metadata `{product, visibility, role, source}`. Tier 2 (learned dynamic): Retro Analyst writes learnings post-run → stored in same collection with `visibility: "user"` or `"global"`. Arbiter queries at workspace assembly: top-K chunks filtered by product + role + userId.

**Tech Stack:** Qdrant (Docker / Qdrant Cloud), TypeScript (indexer in cidadel-ai-arbiter), Python or Node.js indexer script, OpenAI/Anthropic embeddings API

**Repos touched:**
- `cidadel-ai-arbiter` — Qdrant client, knowledge query at workspace assembly
- `tacticl-knowledge` — GitHub Action to trigger indexer on push

**Can run independently of Plans 1 and 2.**

---

## Chunk 1: Qdrant Setup + Schema

### Task 1: Qdrant collection schema + Docker setup

**Files:**
- Create: `packages/server/src/shell/qdrant-client.ts` (in cidadel-ai-arbiter)
- Create: `packages/server/tests/shell/qdrant-client.test.ts`

- [ ] **Step 1: Run Qdrant locally for development**

```bash
docker run -d --name qdrant \
  -p 6333:6333 -p 6334:6334 \
  -v $(pwd)/qdrant-data:/qdrant/storage \
  qdrant/qdrant:latest
```

Verify: `curl http://localhost:6333/health` → `{"status":"ok"}`

- [ ] **Step 2: Install Qdrant client**

```bash
cd packages/server && npm install @qdrant/js-client-rest
```

- [ ] **Step 3: Write failing tests**

```typescript
// packages/server/tests/shell/qdrant-client.test.ts
import { describe, it, expect, vi, beforeEach } from 'vitest';

// Mock @qdrant/js-client-rest
vi.mock('@qdrant/js-client-rest', () => ({
  QdrantClient: vi.fn().mockImplementation(() => ({
    getCollections: vi.fn().mockResolvedValue({ collections: [] }),
    createCollection: vi.fn().mockResolvedValue({}),
    upsert: vi.fn().mockResolvedValue({}),
    search: vi.fn().mockResolvedValue([
      { id: '1', score: 0.95, payload: { content: 'Use constructor injection', role: 'implementer' } }
    ]),
  }))
}));

import { KnowledgeStore } from '../../src/shell/qdrant-client.js';

describe('KnowledgeStore', () => {
  let store: KnowledgeStore;
  beforeEach(() => {
    store = new KnowledgeStore('http://localhost:6333', 'test-api-key');
  });

  it('upserts a knowledge chunk', async () => {
    await store.upsert({
      id: 'chunk-1',
      content: 'Use constructor injection',
      embedding: new Array(1536).fill(0.1),
      product: 'tacticl',
      visibility: 'global',
      role: 'implementer',
      source: 'conventions/constructor-injection.md',
    });
    // No throw = pass
  });

  it('queries and returns relevant chunks', async () => {
    const results = await store.query({
      embedding: new Array(1536).fill(0.1),
      product: 'tacticl',
      userId: 'user-123',
      role: 'implementer',
      topK: 5,
    });
    expect(results).toHaveLength(1);
    expect(results[0].content).toBe('Use constructor injection');
  });
});
```

- [ ] **Step 4: Run to confirm failure**

```bash
npm run test --workspace packages/server -- --reporter=verbose 2>&1 | grep "qdrant-client"
```

- [ ] **Step 5: Implement KnowledgeStore**

```typescript
// packages/server/src/shell/qdrant-client.ts
import { QdrantClient } from '@qdrant/js-client-rest';

const COLLECTION = 'tacticl_knowledge';
const VECTOR_SIZE = 1536; // text-embedding-3-small

export interface KnowledgeChunk {
  id: string;
  content: string;
  embedding: number[];
  product: string;
  visibility: 'global' | 'user';
  userId?: string;
  role?: string;
  source: string;
}

export interface KnowledgeQuery {
  embedding: number[];
  product: string;
  userId: string;
  role?: string;
  topK: number;
}

export interface KnowledgeResult {
  content: string;
  source: string;
  score: number;
  role?: string;
}

export class KnowledgeStore {
  private readonly client: QdrantClient;

  constructor(url: string, apiKey?: string) {
    this.client = new QdrantClient({ url, apiKey });
  }

  async ensureCollection(): Promise<void> {
    const { collections } = await this.client.getCollections();
    if (collections.some(c => c.name === COLLECTION)) return;

    await this.client.createCollection(COLLECTION, {
      vectors: { size: VECTOR_SIZE, distance: 'Cosine' },
    });
  }

  async upsert(chunk: KnowledgeChunk): Promise<void> {
    await this.client.upsert(COLLECTION, {
      points: [{
        id: chunk.id,
        vector: chunk.embedding,
        payload: {
          content: chunk.content,
          product: chunk.product,
          visibility: chunk.visibility,
          userId: chunk.userId ?? null,
          role: chunk.role ?? null,
          source: chunk.source,
        },
      }],
    });
  }

  async query(q: KnowledgeQuery): Promise<KnowledgeResult[]> {
    // Filter: product-global OR this user's private knowledge
    const filter = {
      should: [
        {
          must: [
            { key: 'product', match: { value: q.product } },
            { key: 'visibility', match: { value: 'global' } },
            ...(q.role ? [{ key: 'role', match: { value: q.role } }] : []),
          ],
        },
        {
          must: [
            { key: 'product', match: { value: q.product } },
            { key: 'visibility', match: { value: 'user' } },
            { key: 'userId', match: { value: q.userId } },
          ],
        },
      ],
    };

    const results = await this.client.search(COLLECTION, {
      vector: q.embedding,
      limit: q.topK,
      filter,
      with_payload: true,
    });

    return results.map(r => ({
      content: String(r.payload?.['content'] ?? ''),
      source: String(r.payload?.['source'] ?? ''),
      score: r.score,
      role: r.payload?.['role'] ? String(r.payload['role']) : undefined,
    }));
  }
}
```

- [ ] **Step 6: Run tests to confirm pass**

```bash
npm run test --workspace packages/server -- --reporter=verbose 2>&1 | grep "qdrant-client"
```

- [ ] **Step 7: Commit**

```bash
git add packages/server/src/shell/qdrant-client.ts packages/server/tests/shell/qdrant-client.test.ts
git commit -m "feat(arbiter): add KnowledgeStore with Qdrant — product+user namespaced vector search"
```

---

## Chunk 2: Embeddings + Indexer

### Task 2: EmbeddingClient — generate embeddings via Anthropic/OpenAI

**Files:**
- Create: `packages/server/src/shell/embedding-client.ts`
- Create: `packages/server/tests/shell/embedding-client.test.ts`

- [ ] **Step 1: Write failing tests**

```typescript
// packages/server/tests/shell/embedding-client.test.ts
import { describe, it, expect, vi } from 'vitest';

vi.mock('openai', () => ({
  default: vi.fn().mockImplementation(() => ({
    embeddings: {
      create: vi.fn().mockResolvedValue({
        data: [{ embedding: new Array(1536).fill(0.1) }]
      })
    }
  }))
}));

import { EmbeddingClient } from '../../src/shell/embedding-client.js';

describe('EmbeddingClient', () => {
  it('returns a 1536-dimension embedding vector', async () => {
    const client = new EmbeddingClient('sk-test');
    const embedding = await client.embed('Hello world');
    expect(embedding).toHaveLength(1536);
    expect(embedding[0]).toBeCloseTo(0.1);
  });

  it('batches multiple texts', async () => {
    const client = new EmbeddingClient('sk-test');
    const embeddings = await client.embedBatch(['text1', 'text2']);
    expect(embeddings).toHaveLength(2);
  });
});
```

- [ ] **Step 2: Install openai SDK if not present**

```bash
cd packages/server && npm install openai
```

- [ ] **Step 3: Implement EmbeddingClient**

```typescript
// packages/server/src/shell/embedding-client.ts
import OpenAI from 'openai';

const MODEL = 'text-embedding-3-small';
const BATCH_SIZE = 100;

export class EmbeddingClient {
  private readonly client: OpenAI;

  constructor(apiKey: string) {
    this.client = new OpenAI({ apiKey });
  }

  async embed(text: string): Promise<number[]> {
    const response = await this.client.embeddings.create({
      model: MODEL,
      input: text.slice(0, 8000), // truncate to model limit
    });
    return response.data[0].embedding;
  }

  async embedBatch(texts: string[]): Promise<number[][]> {
    const results: number[][] = [];
    for (let i = 0; i < texts.length; i += BATCH_SIZE) {
      const batch = texts.slice(i, i + BATCH_SIZE).map(t => t.slice(0, 8000));
      const response = await this.client.embeddings.create({ model: MODEL, input: batch });
      results.push(...response.data.map(d => d.embedding));
    }
    return results;
  }
}
```

- [ ] **Step 4: Run tests and commit**

```bash
npm run test --workspace packages/server -- --reporter=verbose 2>&1 | grep "embedding-client"
git add packages/server/src/shell/embedding-client.ts packages/server/tests/shell/embedding-client.test.ts
git commit -m "feat(arbiter): add EmbeddingClient using OpenAI text-embedding-3-small"
```

---

### Task 3: KnowledgeIndexer — sync tacticl-knowledge markdown → Qdrant

**Files:**
- Create: `packages/server/src/shell/knowledge-indexer.ts`
- Create: `packages/server/tests/shell/knowledge-indexer.test.ts`

- [ ] **Step 1: Write failing tests**

```typescript
// packages/server/tests/shell/knowledge-indexer.test.ts
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { KnowledgeIndexer } from '../../src/shell/knowledge-indexer.js';

const mockStore = { upsert: vi.fn().mockResolvedValue(undefined), ensureCollection: vi.fn() };
const mockEmbedder = { embed: vi.fn().mockResolvedValue(new Array(1536).fill(0.1)), embedBatch: vi.fn() };

describe('KnowledgeIndexer', () => {
  let indexer: KnowledgeIndexer;
  beforeEach(() => {
    vi.clearAllMocks();
    indexer = new KnowledgeIndexer(mockStore as never, mockEmbedder as never, 'tacticl');
  });

  it('upserts a chunk for each markdown file', async () => {
    const files = [
      { path: 'wiki/auto/conventions/constructor-injection.md', content: '# Constructor Injection\nAlways use it.', roles: ['implementer', 'reviewer'] },
    ];

    await indexer.indexFiles(files, 'global');

    expect(mockEmbedder.embed).toHaveBeenCalledWith(expect.stringContaining('Constructor Injection'));
    expect(mockStore.upsert).toHaveBeenCalledWith(expect.objectContaining({
      product: 'tacticl',
      visibility: 'global',
      source: 'wiki/auto/conventions/constructor-injection.md',
    }));
  });

  it('derives roles from frontmatter if present', async () => {
    const content = `---\nroles: [implementer, reviewer]\n---\n# Body`;
    const files = [{ path: 'wiki/auto/test.md', content, roles: [] }];
    await indexer.indexFiles(files, 'global');
    expect(mockStore.upsert).toHaveBeenCalledWith(expect.objectContaining({
      role: 'implementer', // first role
    }));
  });
});
```

- [ ] **Step 2: Implement KnowledgeIndexer**

```typescript
// packages/server/src/shell/knowledge-indexer.ts
import { createHash } from 'crypto';
import { KnowledgeStore } from './qdrant-client.js';
import { EmbeddingClient } from './embedding-client.js';

export interface IndexableFile {
  path: string;
  content: string;
  roles: string[];
}

export class KnowledgeIndexer {
  constructor(
    private readonly store: KnowledgeStore,
    private readonly embedder: EmbeddingClient,
    private readonly product: string
  ) {}

  async indexFiles(
    files: IndexableFile[],
    visibility: 'global' | 'user',
    userId?: string
  ): Promise<void> {
    await this.store.ensureCollection();

    for (const file of files) {
      const roles = this.extractRoles(file);
      const primaryRole = roles[0] ?? null;

      const embedding = await this.embedder.embed(file.content);
      const id = createHash('sha256').update(`${this.product}:${file.path}`).digest('hex');

      await this.store.upsert({
        id,
        content: file.content,
        embedding,
        product: this.product,
        visibility,
        userId,
        role: primaryRole ?? undefined,
        source: file.path,
      });
    }
  }

  private extractRoles(file: IndexableFile): string[] {
    // Parse YAML frontmatter: ---\nroles: [r1, r2]\n---
    const frontmatter = file.content.match(/^---\s*\nroles:\s*\[([^\]]+)\]/m);
    if (frontmatter) {
      return frontmatter[1].split(',').map(r => r.trim().toLowerCase());
    }
    return file.roles;
  }
}
```

- [ ] **Step 3: Run tests and commit**

```bash
npm run test --workspace packages/server -- --reporter=verbose 2>&1 | grep "knowledge-indexer"
git add packages/server/src/shell/knowledge-indexer.ts packages/server/tests/shell/knowledge-indexer.test.ts
git commit -m "feat(arbiter): add KnowledgeIndexer — syncs markdown files to Qdrant with role scoping"
```

---

## Chunk 3: GitHub Action + Arbiter Query Integration

### Task 4: GitHub Action — index tacticl-knowledge on push

**Files:**
- Create: `.github/workflows/index-knowledge.yml` (in tacticl-knowledge repo)
- Create: `scripts/index-knowledge.ts` (in cidadel-ai-arbiter)

- [ ] **Step 1: Create indexer script in cidadel-ai-arbiter**

```typescript
// scripts/index-knowledge.ts
// Standalone script: reads all .md files from tacticl-knowledge dir, indexes to Qdrant
import { readdir, readFile } from 'fs/promises';
import { join, relative } from 'path';
import { KnowledgeStore } from '../packages/server/src/shell/qdrant-client.js';
import { EmbeddingClient } from '../packages/server/src/shell/embedding-client.js';
import { KnowledgeIndexer } from '../packages/server/src/shell/knowledge-indexer.js';

const KNOWLEDGE_DIR = process.env.KNOWLEDGE_DIR ?? '/opt/tacticl/tacticl-knowledge';
const QDRANT_URL = process.env.QDRANT_URL ?? 'http://localhost:6333';
const QDRANT_API_KEY = process.env.QDRANT_API_KEY ?? '';
const OPENAI_API_KEY = process.env.OPENAI_API_KEY ?? '';
const PRODUCT = process.env.PRODUCT ?? 'tacticl';

async function* walkDir(dir: string): AsyncGenerator<string> {
  const entries = await readdir(dir, { withFileTypes: true });
  for (const entry of entries) {
    const full = join(dir, entry.name);
    if (entry.isDirectory() && !entry.name.startsWith('.')) {
      yield* walkDir(full);
    } else if (entry.isFile() && entry.name.endsWith('.md')) {
      yield full;
    }
  }
}

async function main() {
  const store = new KnowledgeStore(QDRANT_URL, QDRANT_API_KEY);
  const embedder = new EmbeddingClient(OPENAI_API_KEY);
  const indexer = new KnowledgeIndexer(store, embedder, PRODUCT);

  const files = [];
  for await (const filePath of walkDir(KNOWLEDGE_DIR)) {
    const content = await readFile(filePath, 'utf-8');
    files.push({
      path: relative(KNOWLEDGE_DIR, filePath),
      content,
      roles: [],
    });
  }

  console.log(`Indexing ${files.length} files...`);
  await indexer.indexFiles(files, 'global');
  console.log('Done.');
}

main().catch(console.error);
```

- [ ] **Step 2: Create GitHub Action in tacticl-knowledge**

```yaml
# .github/workflows/index-knowledge.yml (in tacticl-knowledge repo)
name: Index Knowledge to Qdrant

on:
  push:
    branches: [main]
    paths:
      - 'wiki/**'
      - 'approved/**'

jobs:
  index:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Clone cidadel-ai-arbiter indexer
        run: |
          git clone https://x-access-token:${{ secrets.GH_PAT }}@github.com/cidadel-platform/cidadel-ai-arbiter.git /tmp/arbiter

      - name: Install dependencies
        run: cd /tmp/arbiter && npm ci

      - name: Run indexer
        env:
          KNOWLEDGE_DIR: ${{ github.workspace }}
          QDRANT_URL: ${{ secrets.QDRANT_URL }}
          QDRANT_API_KEY: ${{ secrets.QDRANT_API_KEY }}
          OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
          PRODUCT: tacticl
        run: cd /tmp/arbiter && npx tsx scripts/index-knowledge.ts
```

- [ ] **Step 3: Add secrets to tacticl-knowledge repo**

In GitHub → tacticl-knowledge repo → Settings → Secrets:
- `QDRANT_URL` — Qdrant Cloud URL or self-hosted
- `QDRANT_API_KEY` — API key
- `OPENAI_API_KEY` — for embeddings
- `GH_PAT` — PAT with read access to cidadel-ai-arbiter

- [ ] **Step 4: Test run manually**

```bash
cd /path/to/cidadel-ai-arbiter
KNOWLEDGE_DIR=/path/to/tacticl-knowledge \
QDRANT_URL=http://localhost:6333 \
OPENAI_API_KEY=sk-... \
npx tsx scripts/index-knowledge.ts
```
Expected: "Indexing N files... Done."

- [ ] **Step 5: Commit to both repos**

```bash
# In cidadel-ai-arbiter:
git add scripts/index-knowledge.ts
git commit -m "feat(arbiter): add knowledge indexer script for tacticl-knowledge → Qdrant sync"

# In tacticl-knowledge:
git add .github/workflows/index-knowledge.yml
git commit -m "feat(knowledge): add GitHub Action to index markdown files to Qdrant on push"
```

---

### Task 5: Wire KnowledgeStore into WorkspaceAssembler

**Files:**
- Modify: `packages/server/src/shell/workspace-assembler.ts`

At workspace assembly time, Arbiter queries Qdrant for the top-K relevant chunks for this role + user, then writes them to `.agent/knowledge/`.

- [ ] **Step 1: Add knowledge query to assemble()**

In `WorkspaceAssembler`, inject `KnowledgeStore` and `EmbeddingClient`:

```typescript
constructor(
  private readonly store: KnowledgeStore,
  private readonly embedder: EmbeddingClient,
  // ... existing deps
) {}
```

In `assemble()`, after context files are written:

```typescript
// Query Qdrant for role-relevant knowledge
if (this.store && req.knowledgeNamespace) {
  const [product, ...rest] = req.knowledgeNamespace.split('/');
  const userId = rest.join('/').replace('user_', '');

  const queryEmbedding = await this.embedder.embed(req.sparkContext);
  const chunks = await this.store.query({
    embedding: queryEmbedding,
    product,
    userId,
    role: req.agentType.toLowerCase(),
    topK: 10,
  });

  // Write retrieved chunks to .agent/knowledge/retrieved.md
  if (chunks.length > 0) {
    const content = chunks
      .map(c => `## ${c.source} (score: ${c.score.toFixed(2)})\n\n${c.content}`)
      .join('\n\n---\n\n');
    await fs.writeFile(path.join(knowledgeDir, 'retrieved.md'), content);
  }
}
```

- [ ] **Step 2: Make KnowledgeStore optional (graceful degradation)**

If `QDRANT_URL` is not configured, `KnowledgeStore` is null — workspace assembly continues without retrieved knowledge. Do not throw.

- [ ] **Step 3: Build and run tests**

```bash
npm run build --workspace packages/server 2>&1 | grep -E "error TS"
npm run test --workspace packages/server 2>&1 | tail -15
```

- [ ] **Step 4: Commit**

```bash
git add packages/server/src/shell/workspace-assembler.ts
git commit -m "feat(arbiter): query Qdrant at workspace assembly — inject retrieved knowledge into .agent/knowledge/"
```

---

### Task 6: Retro Analyst write-back — learned knowledge → Qdrant

**Files:**
- Modify: `packages/server/src/shell/agent-api.ts`

When `report.sh` sends `type: "learning"` (new type), Arbiter writes it to Qdrant under the user's namespace.

- [ ] **Step 1: Add learning handler to AgentApi.onNotify callback**

In `Shell.onAgentPush()`, add:

```typescript
case 'learning': {
  const { content, visibility = 'global' } = JSON.parse(message) as
    { content: string; visibility?: 'global' | 'user' };

  const pipeline = this.tracker.getPipeline(pipelineId);
  if (pipeline && this.store) {
    const embedding = await this.embedder.embed(content);
    await this.store.upsert({
      id: randomBytes(16).toString('hex'),
      content,
      embedding,
      product: pipeline.product,
      visibility,
      userId: visibility === 'user' ? pipeline.userId : undefined,
      role: agentName.split('-')[0],
      source: `learned/${pipelineId}/${Date.now()}`,
    });
  }
  break;
}
```

- [ ] **Step 2: Update report.sh template to include learning type**

In `WorkspaceAssembler.buildReportSh()`, the existing script already supports arbitrary types. RETRO_ANALYST SKILL.md (Plan 4) will instruct it to use `bash .agent/report.sh learning '{"content":"...","visibility":"global"}'`.

No code change needed — the API already handles arbitrary `type` strings; this just documents the new `learning` type for agent use.

- [ ] **Step 3: Commit**

```bash
git add packages/server/src/shell/shell.ts
git commit -m "feat(arbiter): handle 'learning' push event — writes Retro Analyst learnings to Qdrant"
```

---

## Environment Variables Summary

| Variable | Where | Purpose |
|---|---|---|
| `QDRANT_URL` | Arbiter | Qdrant instance URL |
| `QDRANT_API_KEY` | Arbiter | Qdrant auth key |
| `OPENAI_API_KEY` | Arbiter + indexer script | Embeddings |
| `TACTICL_KNOWLEDGE_PATH` | tacticl-core | Path to cloned tacticl-knowledge for role identity loading |
| `QDRANT_URL` | GitHub Action (tacticl-knowledge) | Indexer target |
