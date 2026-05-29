# SPDD Schema v2 - Hybrid Architecture Reference

## Overview

All SPDD stories use **schema v2 hybrid architecture** with explicit versioning and token-efficient metadata separation:
- **Story file**: Lightweight frontmatter (id, title, status, tags) + story body
- **Metrics file**: Comprehensive workflow data (assignees, flow, quality metrics, blocks, defects, etc.)

**Why hybrid?** **64% token savings** on metadata operations by avoiding story body reads.

---

## Directory Structure

```
project-root/
├── requirements/
│   ├── [User-story-1]user-auth.md         # Lightweight frontmatter + body
│   ├── [User-story-2]payment-flow.md
│   └── [User-story-7]token-billing.md
│
└── spdd/
    ├── metrics/
    │   ├── STORY-001-001.yml               # Comprehensive metadata
    │   ├── STORY-002-001.yml
    │   └── STORY-007-001.yml
    │
    ├── analysis/
    │   └── STORY-007-001-[Analysis]-token-billing.md
    │
    └── prompt/
        └── STORY-007-001-[Feat]-token-billing.md
```

---

## Story File (Lightweight)

**File**: `requirements/[User-story-7]token-billing.md`

```markdown
---
id: STORY-007-001
title: "Token usage billing API"
status: IN_PROGRESS               # BACKLOG | IN_PROGRESS | IN_TEST | BLOCKED | DONE
tags: [billing, api, core]
metrics_file: spdd/metrics/STORY-007-001.yml
---

# User Story: Token Usage Billing API

## Context
...story body content...
```

**Fields**:
- `id`: Unique story identifier
- `title`: One-line description
- `status`: Current workflow state (manually visible for quick checks)
- `tags`: Categorization labels
- `metrics_file`: **Pointer to comprehensive metrics** (enables hybrid architecture)

**Token cost**: ~50-100 tokens (very lightweight)

---

## Metrics File (Comprehensive)

**File**: `spdd/metrics/STORY-007-001.yml`

```yaml
# SPDD Lifecycle Metrics - Schema v2
# Story: Token usage billing API
# File: requirements/[User-story-7]token-billing.md

schema_version: 2
story_id: STORY-007-001
story_file: requirements/[User-story-7]token-billing.md

# Workflow tracking
iteration: Sprint-3

assignees:
  ba: wendi.zhang                 # Auto-set by /spdd-story
  qa: wangwu                       # Auto-set by /spdd-meta-analyzed
  dev: lisi                        # Auto-set by /spdd-meta-refine

flow:
  created_at: 2026-05-01 09:30
  analyzed_at: 2026-05-02 11:15
  developed_at: 2026-05-03 16:45
  tested_at: 2026-05-04 10:20
  delivered_at: 2026-05-04 11:00

quality_metrics:
  ai_refine_loops: 3               # Incremented by /spdd-meta-refine
  qa_rejections: 1                 # Incremented by CI
  test_coverage: 92                # Integer 0-100, written by CI

block_records:
  - phase: DEVELOPMENT             # Auto-inferred from status
    blocked_at: 2026-05-02 14:00
    unblocked_at: 2026-05-02 18:30
    reason: "Waiting for payments API quota approval"
    previous_status: IN_PROGRESS
    reporter: lisi
    closer: lisi
    resolution: "Quota approved by ops"

delivery_evidence:
  reasons_canvas: spdd/analysis/STORY-007-001-[Analysis]-token-billing.md
  implementation_prompts:
    - spdd/prompt/STORY-007-001-[Feat]-token-billing.md
  test_prompts:
    - spdd/prompt/STORY-007-001-[Test]-token-billing.md
  linked_commits:
    - a18f92c
    - b27e03d
  human_reviewed_reasoning: true
  prompt_code_mapping_verified: true

cognitive_discovery:
  unknown_unknowns_surfaced:
    - "Token rate limiting is per-customer, not per-API-key"
  known_unknowns:
    - "Overage billing threshold requires product confirmation"
  tacit_knowledge_extracted:
    - "All billing calculations use BigDecimal to avoid rounding errors"
  converted_to_known_knowns:
    - "Overage confirmed: charge after 10k tokens per month"

inline_defects:
  - desc: "Null pointer when customer has no subscription"
    found_by: wangwu
    severity: HIGH                 # CRITICAL | HIGH | MEDIUM | LOW | INFO
    status: FIXED                  # OPEN | FIXED | WONT_FIX | DEFERRED
    fixed_at: 2026-05-03 18:00
    fixed_in_commit: b27e03d

related_bugs: []
```

**Token cost**: ~500-600 tokens (comprehensive but still compact)

---

## Token Efficiency Comparison

| Operation | Story Body | Old (Frontmatter) | New (Hybrid) | Savings |
|-----------|------------|-------------------|--------------|---------|
| `/spdd-meta-analyzed` | 2000 tokens | 2500 tokens | 550 tokens | **78%** |
| `/spdd-meta-refine` | 2000 tokens | 2500 tokens | 500 tokens | **80%** |
| `/spdd-block` | 2000 tokens | 2500 tokens | 550 tokens | **78%** |
| `/spdd-cognitive-add` | 2000 tokens | 2500 tokens | 500 tokens | **80%** |
| `/spdd-defect-add` | 2000 tokens | 2500 tokens | 500 tokens | **80%** |
| `/spdd-analysis` (needs body) | 2000 tokens | 2500 tokens | 2550 tokens | -2% (acceptable) |

**Overall savings**: **~64%** across typical story lifecycle (10 metadata operations per story)

---

## Command Reference

### Commands Reading/Writing Metrics File Only

| Command | Reads | Writes | Token Cost |
|---------|-------|--------|------------|
| `/spdd-meta-refine` | Metrics only (~500) | Metrics file | ~500 tokens |
| `/spdd-cognitive-add` | Metrics only (~500) | Metrics file | ~500 tokens |
| `/spdd-defect-add` | Metrics only (~500) | Metrics file | ~500 tokens |

**High efficiency**: These frequent operations never touch the story body.

### Commands Reading/Writing Both Files

| Command | Reads | Writes | Token Cost |
|---------|-------|--------|------------|
| `/spdd-meta-analyzed` | Story frontmatter (~50) + Metrics (~500) | Both | ~550 tokens |
| `/spdd-block` | Story frontmatter (~50) + Metrics (~500) | Both | ~550 tokens |
| `/spdd-unblock` | Story frontmatter (~50) + Metrics (~500) | Both | ~550 tokens |

**Good efficiency**: Read lightweight frontmatter for status, mutate metrics for workflow data.

### Commands Reading Story Body (Necessary)

| Command | Reads | Writes | Token Cost |
|---------|-------|--------|------------|
| `/spdd-analysis` | Story body (~2000) + Metrics (~500) | Metrics file | ~2500 tokens |
| `/spdd-generate` | Story body (~2000) + Metrics (~500) | Metrics file | ~2500 tokens |

**Acceptable**: These commands **need** the story body to perform their function (analysis, code generation).

---

## Key Features

### 1. Workflow Lifecycle (`flow`)

Tracks the story's journey through workflow stages:

```yaml
flow:
  created_at: 2026-05-01 09:30
  analyzed_at: 2026-05-02 11:15
  developed_at: 2026-05-03 16:45
  tested_at: 2026-05-04 10:20
  delivered_at: 2026-05-04 11:00
```

**Auto-populated by SPDD commands** at each workflow transition.

---

### 2. Cognitive Discovery

Captures the epistemological journey from uncertainty → knowledge:

```yaml
cognitive_discovery:
  unknown_unknowns_surfaced:
    - "Rate limiting is per-customer, not per-API-key"
  known_unknowns:
    - "Overage billing threshold requires product confirmation"
  tacit_knowledge_extracted:
    - "All billing calculations use BigDecimal"
  converted_to_known_knowns:
    - "Overage threshold confirmed: 10k tokens/month"
```

**Use `/spdd-cognitive-add`** during implementation to record discoveries.

---

### 3. Phase-Aware Blocking (`block_records`)

Automatically categorizes blockers by workflow phase:

```yaml
block_records:
  - phase: DEVELOPMENT              # Auto-inferred from status
    blocked_at: 2026-05-02 14:00
    unblocked_at: 2026-05-02 18:30
    reason: "Waiting for API quota approval"
    previous_status: IN_PROGRESS
    reporter: wendi.zhang
```

**Phase mapping**:
- `BACKLOG` → `ANALYSIS`
- `IN_PROGRESS` → `DEVELOPMENT`
- `IN_TEST` → `TESTING`
- `DONE` → `DELIVERY`

---

### 4. Rich Defect Tracking (`inline_defects`)

Track "mini-bugs" fixed within the story lifecycle:

```yaml
inline_defects:
  - desc: "Null pointer when customer has no subscription"
    found_by: wangwu
    severity: HIGH
    status: FIXED
    fixed_at: 2026-05-03 18:00
    fixed_in_commit: b27e03d
```

**Use `/spdd-defect-add`** during QA or development.

---

### 5. Delivery Evidence (`delivery_evidence`)

Full audit trail of artifacts and reviews:

```yaml
delivery_evidence:
  reasons_canvas: spdd/analysis/STORY-007-001-[Analysis]-auth.md
  implementation_prompts:
    - spdd/prompt/STORY-007-001-[Feat]-auth.md
  test_prompts:
    - spdd/prompt/STORY-007-001-[Test]-auth.md
  linked_commits: [a18f92c, b27e03d]
  human_reviewed_reasoning: true
  prompt_code_mapping_verified: true
```

**Auto-populated** by SPDD workflow commands.

---

## Benefits of Hybrid Architecture

### 1. Token Efficiency
- **64% savings** on metadata operations
- High-frequency commands (`/spdd-meta-refine`, `/spdd-cognitive-add`) never read story body
- **ROI**: ~$2.40 saved per project (50 stories × 10 metadata ops × 2000 tokens × $3/M)

### 2. Human-Friendly
- Story file retains key fields (`status`, `tags`) for quick visual reference
- No need to open metrics file for routine checks
- Clean separation: business content vs workflow data

### 3. Git History
- Business changes (story content, status) → story commit
- Workflow data (refine loops, defects, blocks) → metrics commit
- Cleaner diffs, easier code review

### 4. Tool Integration
- Commands read only what they need
- Faster CI checks (validate only changed files)
- Metrics file easy to parse for dashboards

---

## Quick Start

### 1. Create a new story

```bash
/spdd-story "User registration with email/password"
```

→ Auto-generates hybrid architecture via `/spdd-meta-init`:
  - Story file with lightweight frontmatter
  - Metrics file at `spdd/metrics/STORY-XXX-YYY.yml`

### 2. Analyze requirements

```bash
/spdd-analysis @requirements/[User-story-7]token-billing.md
```

→ Writes to metrics file: `flow.analyzed_at`, `assignees.qa`
→ Writes to story file: `status: IN_PROGRESS`

### 3. Record discoveries during development

```bash
/spdd-cognitive-add @requirements/[User-story-7]token-billing.md \
  --type unknown_unknowns \
  --item "Rate limiting is per-customer, not per-API-key"
```

→ Appends to metrics file only (~500 tokens read)

### 4. Track defects during QA

```bash
/spdd-defect-add @requirements/[User-story-7]token-billing.md \
  --desc "Null pointer when customer has no subscription" \
  --severity HIGH
```

→ Appends to metrics file only (~500 tokens read)

### 5. Validate before committing

```bash
/spdd-meta-validate @requirements/[User-story-7]token-billing.md --strict
```

→ Validates both story frontmatter and metrics file

---

## Validation

### Pre-commit Hook (Recommended)

```bash
#!/bin/bash
# .git/hooks/pre-commit
for file in $(git diff --cached --name-only | grep '^requirements/.*\.md$'); do
  /spdd-meta-validate @"$file" --strict || exit 1
done
```

### CI Pipeline

```yaml
# .github/workflows/validate-stories.yml
name: Validate Story Metadata
on: [push, pull_request]
jobs:
  validate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Validate all stories
        run: |
          for file in requirements/*.md; do
            /spdd-meta-validate @"$file" --strict
          done
```

---

## Schema Authority

**Single source of truth**: `.cursor/commands/spdd-meta-init.md`

All SPDD commands read/write the schemas defined there:
- **Story frontmatter schema**: Lightweight (id, title, status, tags, metrics_file)
- **Metrics file schema**: Comprehensive (all workflow data)

---

## Migration Notes

**No migration needed** - this is a fresh schema v2 design. All new stories use hybrid architecture by default.

If you have old stories with embedded frontmatter, they won't break (commands will fail gracefully), but won't benefit from token efficiency. Regenerate them via `/spdd-story` to adopt hybrid architecture.

---

**Schema v2 Hybrid Architecture - Token-Efficient, Human-Friendly, Auditable** ✨
