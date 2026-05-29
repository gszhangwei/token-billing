---
name: /spdd-meta-refine
id: spdd-meta-refine
category: Development
description: Record one refine-loop iteration in metrics file (dev assignee + ai_refine_loops counter). Normally invoked by /spdd-generate and /spdd-prompt-update.
---

Mutate the SPDD lifecycle metadata in the **metrics file** to reflect that one refine-loop iteration just happened — i.e. an AI code generation run or a structured-prompt edit. This command is the canonical writer for `assignees.dev` and `quality_metrics.ai_refine_loops`.

**Hybrid architecture efficiency**: Reads only the metrics file (~500 tokens), avoiding the story body (potentially 2000+ tokens). **~80% token savings** for this high-frequency operation.

**Input**:

```
/spdd-meta-refine --file @requirements/<story-file>.md [--reason "generate" | "prompt-update" | "manual"]
```

- `--file` (required): the story whose frontmatter to mutate.
- `--reason` (optional, default `manual`): a short tag describing what triggered the iteration. Surfaced in the run report only; **not** persisted to YAML to keep the schema lean.

Examples:

```
/spdd-meta-refine --file @requirements/[User-story-7]token-billing.md --reason generate
/spdd-meta-refine --file @requirements/[User-story-7]token-billing.md --reason prompt-update
```

**Steps**

1. **Validate input and locate metrics file**

   - `--file` is required. If missing, abort — this command is invoked programmatically.
   - Read the story file frontmatter (lightweight, first ~10 lines).
   - Extract `metrics_file` path from frontmatter. If missing, abort with:
     > "Story file missing `metrics_file` pointer. Run `/spdd-meta-init` to regenerate."
   - Verify metrics file exists at the specified path. If not, abort with:
     > "Metrics file not found at `{metrics_file}`. Run `/spdd-meta-init` to regenerate."

2. **Capture machine-derived values**

   - `dev`: `git config user.name`, falling back to `$USER`, then `"unknown"` (with a warning in the report).

3. **Read the current metrics file** and snapshot:
   - Current value of `assignees.dev`.
   - Current value of `quality_metrics.ai_refine_loops` (treat `null` or missing as `0`).

4. **Mutate the metrics file** in-place via StrReplace. Do NOT reorder keys, do NOT touch any field other than the two listed below.

   a. **`assignees.dev`**:
    - If currently `null`, replace with `"<dev>"`.
    - If currently a non-null string, leave untouched (preserves the original developer across re-runs; multi-dev pairing should be expressed in `inline_defects`/`related_bugs` or via review trailers, not by stomping this field).

   b. **`quality_metrics.ai_refine_loops`**:
    - Always increment by 1 (`current + 1`). Treat `null`/missing as `0` before incrementing.
    - Even if generation upstream partially failed, the loop counter MUST still increment — the count reflects "AI work attempted on this story", which is the metric the team wants.

5. **Forbidden mutations** — abort the whole step rather than touch:
   - `status` (state transitions owned by `/spdd-meta-analyzed`, `/spdd-block`, `/spdd-unblock`, Git hooks, CI - lives in story file)
   - `flow.developed_at` / `tested_at` / `delivered_at` (CI-owned)
   - `quality_metrics.qa_rejections` / `test_coverage` (CI-owned)
   - any other field

6. **Report the diff**:

   ```
   📌 Story metadata updated:
      
      Metrics file (spdd/metrics/<story-id>.yml):
         - assignees.dev: <before> → <after>           [unchanged if preserved]
         - quality_metrics.ai_refine_loops: <n> → <n+1>
         - reason: <--reason or "manual">
      
      Token efficiency: Read ~500 tokens (avoided ~2000+ story body tokens).
   ```

   If `dev` fell back to `"unknown"`, append the same git-config warning that `/spdd-meta-analyzed` emits.

**Output**

The metrics file mutated to record one refine-loop iteration. Story file untouched.

**Guardrails**

- Do NOT proceed without `--file` — this is a programmatic command
- Do NOT initialize metadata — that is `/spdd-meta-init`'s job; abort if missing
- MUST verify `metrics_file` pointer exists in story frontmatter
- MUST verify metrics file exists at the specified path
- Do NOT touch any field other than `assignees.dev` and `quality_metrics.ai_refine_loops` in the metrics file
- Do NOT touch the story file at all (status transitions happen in other commands)
- Do NOT overwrite a non-null `assignees.dev`
- Do NOT skip incrementing `ai_refine_loops` even when the upstream caller partially failed; this counter measures attempted AI work, not successful work
- Do NOT mutate `status`, any `flow.*` timestamp, or any CI-owned counter
- Do NOT persist `--reason` into YAML — it is a report-time annotation only
- `dev` MUST come from `git config user.name`; do NOT accept it as an argument
- MUST read only the metrics file (~500 tokens), not the story body
