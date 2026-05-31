# SPDD — Structured Prompt-Driven Development

SPDD turns business requirements into implementation-ready **REASONS Canvas** prompts, then into code, API tests, and keeps prompts and code in sync. Run each phase as a Cursor chat command (`/spdd-*`); rules live in `.cursor/rules/spdd/`.

**Core principle:** When behavior diverges from the spec, fix the **prompt first**, then the code. Commit prompt and code together.

## Directory layout

```
requirements/              User stories and business specs
spdd/
  analysis/                Strategic analysis artifacts
  prompt/                  REASONS Canvas implementation prompts
  template/                Shared templates (e.g. API test skeleton)
scripts/
  test-api.sh              Generated curl-based API test script
```

## Workflow overview

Run phases in order. Each phase produces an artifact the next phase consumes.

```
/spdd-story (optional)
  → /spdd-analysis
  → /spdd-reasons-canvas
  → /spdd-generate
  → /spdd-api-test
  → /spdd-sync or /spdd-prompt-update (iterate)
```

| Phase | Command | Primary output |
|-------|---------|----------------|
| Story split | `/spdd-story` | `requirements/[User-story-N]*.md` |
| Analysis | `/spdd-analysis` | `spdd/analysis/*-[Analysis]-*.md` |
| Design prompt | `/spdd-reasons-canvas` | `spdd/prompt/*-[Feat\|Fix\|...]-*.md` |
| Code | `/spdd-generate` | Source files under `src/` |
| API tests | `/spdd-api-test` | `scripts/test-api.sh` |
| Code → prompt | `/spdd-sync` | Updated prompt file |
| Intent → prompt | `/spdd-prompt-update` | Updated prompt file |

---

## How to run a command

Every `/spdd-*` command accepts **text and/or `@file` / `@folder` references** after the command name.

1. Type the command in Cursor chat (e.g. `/spdd-analysis`).
2. Attach context with `@` — requirements, analysis files, prompt files, or code folders.
3. If required input is missing, the agent asks before proceeding.
4. The agent reads every referenced file completely and merges sources into one context.
5. When a phase finishes, the agent summarizes outputs and suggests the **next command**.

**Example (greenfield feature):**

```
/spdd-analysis @requirements/token-usage-billing-story.md
/spdd-reasons-canvas @spdd/analysis/GGQPA-XXX-202605271200-[Analysis]-token-usage-billing.md
/spdd-generate @spdd/prompt/GGQPA-XXX-202605271215-[Feat]-api-token-usage-billing.md
/spdd-api-test @spdd/prompt/GGQPA-XXX-202605271215-[Feat]-api-token-usage-billing.md
```

---

## Phase 1: `/spdd-story` (optional)

Split a large feature into INVEST-compliant user stories. **No codebase exploration** — that happens in analysis.

**When to use:** A requirement is too big for one analysis/generate cycle, or you want numbered stories under `requirements/`.

**Steps the agent follows:**

1. Consolidate input (text + `@` references).
2. Scan `requirements/` for existing `[User-story-N]` files; assign the next number.
3. Run INVEST analysis (Independent, Negotiable, Valuable, Estimable, Small, Testable).
4. Split by business capability — not by technical layer (each story: ~1–5 days, 2–3 related functional points).
5. Write each story with: title, background, business value, dependencies, scope in/out, Given-When-Then acceptance criteria.
6. Quality-check: testable ACs in business language; no implementation prescriptions.
7. Save to `requirements/[User-story-{N}]{kebab-title}.md`.
8. Summarize and offer `/spdd-analysis @requirements/<file>.md`.

**Guardrails:** No code generation. Do not split one API endpoint across many stories.

---

## Phase 2: `/spdd-analysis`

Produce strategic **What** and **Why** context for the REASONS Canvas. No implementation details.

**Input:** `@requirements/...` story or spec (or pasted text).

**Steps the agent follows:**

1. Consolidate business input; preserve requirement text verbatim in the output.
2. Concept-driven codebase exploration (fingerprint build/config, search by domain concepts — not a full-repo read). Also scan `spdd/prompt/` and `spdd/analysis/` for prior artifacts.
3. Identify domain concepts: existing vs new, key business rules.
4. Document strategic approach: direction, design decisions, trade-offs, high-level data flow.
5. Risk & gap analysis: ambiguities, edge cases, technical risks, AC coverage table.
6. Assemble and save to `spdd/analysis/{JIRA}-{YYYYMMDDHHmm}-[Analysis]-{kebab-desc}.md`.
7. Summarize and offer `/spdd-reasons-canvas @spdd/analysis/<file>.md`.

**Output sections:** Original Business Requirement, Domain Concept Identification, Strategic Approach, Risk & Gap Analysis.

**Guardrails:** No code, no SQL/DTOs/signatures/JSON shapes.

---

## Phase 3: `/spdd-reasons-canvas`

Turn analysis (or raw requirements) into an implementation-ready **REASONS Canvas** prompt.

**Input:** `@spdd/analysis/...` or `@requirements/...`.

**Steps the agent follows:**

1. Consolidate business context.
2. Scoped codebase read — enough to model entities, layers, and conventions.
3. Populate all seven REASONS sections (see below); no placeholders.
4. Save to `spdd/prompt/{JIRA}-{YYYYMMDDHHmm}-[{Feat|Fix|Refactor|Test|Docs}]-{scope-}{kebab-desc}.md`.
5. Summarize one line per section.
6. Offer `/spdd-generate @spdd/prompt/<file>.md` — **does not implement code until you confirm.**

**Guardrails:** Operations must be specific, ordered, and executable. Respect existing implementations.

---

## REASONS Canvas sections

Each prompt file under `spdd/prompt/` contains seven sections (detailed rules: `.cursor/rules/spdd/reasons-framework.mdc`):

| Section | Purpose |
|---------|---------|
| **R**equirements | Essential problem and value in concise verb phrases |
| **E**ntities | Mermaid class diagram: entities, DTOs, relationships, request/response flow |
| **A**pproach | Solution direction, technical choices, business rules, rationale |
| **S**tructure | Inheritance, dependencies, layered architecture |
| **O**perations | Executable tasks in **dependency order** — signatures, logic steps, annotations |
| **N**orms | DI, exceptions, validation, logging, response format |
| **S**afeguards | Constraints, status codes, **exact error messages**, measurable criteria |

Optional: Acceptance Criteria traceability table linking ACs to Operations.

The saved prompt contains **only** these sections — no timestamps, framework metadata, or code blocks.

---

## Phase 4: `/spdd-generate`

Implement code from a REASONS prompt, following **Operations order exactly**.

**Input:** `@spdd/prompt/<file>.md` (required).

**Steps the agent follows:**

1. Validate prompt path; read the entire prompt.
2. Analyze project stack, package layout, existing patterns.
3. Validate Operations sequence (dependencies, completeness vs Structure); report issues before coding.
4. Generate code operation-by-operation: signatures, logic, Norms, Safeguards (including exact error messages).
5. Batch validate: compile/lint, layering, DI, AC traceability.
6. Report files created, assumptions, validation results.

**If behavior is wrong after generation:**

1. Trace the issue to a REASONS section (R/E/A/S/O/N/S).
2. Update the prompt first (`/spdd-prompt-update` or manual edit).
3. Regenerate affected components only.
4. Commit prompt and code together.

**Guardrails:** Do not skip Operations, reorder them, or change Safeguard messages without updating the prompt.

---

## Phase 5: `/spdd-api-test`

Generate a self-contained bash + curl test script for the API.

**Input:** Controllers, `@spdd/prompt/...`, requirements, or `@folder` with API code.

**Steps the agent follows:**

1. Consolidate input; extract endpoints, schemas, ACs, error messages.
2. Find seed data in migrations, test resources, fixtures.
3. Design test tables grouped by pattern (validation, happy path, edge cases); map ACs to test IDs.
4. Generate `scripts/test-api.sh` from `spdd/template/api-test-script-skeleton.sh`.
5. Make executable (`chmod +x`).
6. Summarize test counts and usage: `./scripts/test-api.sh [BASE_URL]`.

**Run tests** (app must be up, e.g. `./gradlew bootRun`):

```bash
./scripts/test-api.sh
# or
./scripts/test-api.sh http://localhost:8080
```

**Guardrails:** Expectations must match actual paths, bodies, and error messages in code.

---

## Iteration: keeping prompt and code aligned

### `/spdd-sync` — code changed, update the prompt

Use after refactors or fixes that changed implementation but not business intent.

**Input:** `@spdd/prompt/<file>.md` (required).

**Steps:**

1. Read prompt; identify code changes (user description or git diff).
2. Compare code vs prompt per component.
3. Draft sync plan (Operations highest priority); **get approval before deletions**.
4. Apply updates; validate cross-section consistency.
5. Report sections changed.

Sync priority: Operations → Entities/Structure → Approach/Norms → Requirements/Safeguards.

### `/spdd-prompt-update` — requirements or design changed, update the prompt

Use when new requirements, constraints, or architecture decisions arrive.

**Input:** `@spdd/prompt/<file>.md` **and** change instructions (both required).

**Steps:**

1. Read existing prompt.
2. Map change to affected sections (minimal edit only).
3. Read codebase if needed for new entities or patterns.
4. Apply edits; validate consistency; overwrite same filename.
5. Summarize and offer `/spdd-generate`.

**Guardrails:** Do not rewrite the whole file. No placeholders or code blocks in prompts.

---

## File naming

| Artifact | Pattern | JIRA fallback |
|----------|---------|---------------|
| Story | `[User-story-{N}]{kebab-title}.md` | next N in `requirements/` |
| Analysis | `{JIRA}-{YYYYMMDDHHmm}-[Analysis]-{kebab-desc}.md` | `GGQPA-XXX` |
| Prompt | `{JIRA}-{YYYYMMDDHHmm}-[{Feat\|Fix\|Refactor\|Test\|Docs}]-{scope-}{kebab-desc}.md` | `GGQPA-XXX` |

Timestamps use local `YYYYMMDDHHmm` at creation time.

---

## Phase boundaries

| Concern | Story / Analysis | REASONS Canvas | Generate |
|---------|------------------|----------------|----------|
| Level | What & why | How | Implementation |
| Codebase | Scoped exploration | Scoped exploration | Full execution |
| Output | Business language | Spec language | Source code |

---

## Rules reference

| Rule file | Used by |
|-----------|---------|
| `spdd-shared.mdc` | All commands — workflow, naming, input handling |
| `spdd-story.mdc` | `/spdd-story` |
| `spdd-analysis.mdc` | `/spdd-analysis` |
| `spdd-reasons-canvas.mdc` | `/spdd-reasons-canvas` |
| `reasons-framework.mdc` | Canvas construction, sync, prompt update |
| `spdd-generate.mdc` | `/spdd-generate` |
| `spdd-api-test.mdc` | `/spdd-api-test` |
| `spdd-sync.mdc` | `/spdd-sync` |
| `spdd-prompt-update.mdc` | `/spdd-prompt-update` |

Domain-specific billing rules for this repo: `.cursor/rules/token-billing-domain.mdc` (align REASONS prompts and generated code with these rules).

---

## Quick checklist

- [ ] Requirement documented (`requirements/` or pasted text)
- [ ] Optional: stories split (`/spdd-story`)
- [ ] Analysis saved under `spdd/analysis/` (`/spdd-analysis`)
- [ ] REASONS prompt saved under `spdd/prompt/` (`/spdd-reasons-canvas`)
- [ ] Code generated from prompt (`/spdd-generate`)
- [ ] API script generated and run (`/spdd-api-test`)
- [ ] After changes: sync or update prompt, then regenerate as needed
- [ ] Commit prompt and code together
