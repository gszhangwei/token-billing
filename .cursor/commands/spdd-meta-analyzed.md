---
name: /spdd-meta-analyzed
id: spdd-meta-analyzed
category: Development
description: Record analysis-time metadata (qa assignee + analyzed_at) in metrics file and update story status. Normally invoked by /spdd-analysis.
---

Mutate the SPDD lifecycle metadata using **hybrid architecture**: writes to the metrics file (`assignees.qa`, `flow.analyzed_at`) and updates the story file (`status`). This command is the canonical writer for analysis-time metadata and the `BACKLOG → IN_PROGRESS` status transition.

**Hybrid architecture efficiency**: Reads only the lightweight story frontmatter (~50 tokens) and metrics file (~500 tokens), avoiding the story body (potentially 2000+ tokens). **~80% token savings** compared to reading full story.

**Input**:

```
/spdd-meta-analyzed --file @requirements/<story-file>.md [--no-status-prompt]
```

- `--file` (required): the story whose frontmatter to mutate.
- `--no-status-prompt` (optional): skip the interactive status-transition prompt; only update `assignees.qa` and `analyzed_at`. Useful when the analysis was triggered as part of a non-interactive batch.

Examples:

```
/spdd-meta-analyzed --file @requirements/[User-story-7]token-billing.md
/spdd-meta-analyzed --file @requirements/[User-story-3]invoice-export.md --no-status-prompt
```

**Steps**

1. **Validate input and locate metrics file**

   - `--file` is required. If missing, abort — this command is invoked programmatically by `/spdd-analysis`.
   - Read the story file frontmatter (lightweight, first ~10 lines).
   - Extract `metrics_file` path from frontmatter. If missing, abort with:
     > "Story file missing `metrics_file` pointer. Run `/spdd-meta-init` to regenerate."
   - Verify metrics file exists at the specified path. If not, abort with:
     > "Metrics file not found at `{metrics_file}`. Run `/spdd-meta-init` to regenerate."

2. **Capture machine-derived values**

   - `qa`: `git config user.name`, falling back to `$USER`, then `"unknown"` (with a warning in the report).
   - `now`: local clock, `YYYY-MM-DD HH:MM`.

3. **Read current metadata** from both files:
   
   a. From **story frontmatter** (lightweight read):
   - Current value of `status`
   
   b. From **metrics file** (Read tool):
   - Current value of `assignees.qa`
   - Current value of `flow.analyzed_at`

4. **Mutate metadata across both files** via StrReplace. Do NOT reorder keys, do NOT touch any unrelated fields.

   **A. Update metrics file**:
   
   a. **`assignees.qa`**:
    - If currently `null`, replace with `"<qa>"`.
    - If currently a non-null string, leave untouched (preserves the original analyst across re-runs).

   b. **`flow.analyzed_at`**:
    - Always overwrite with `"<now>"` so the field reflects the most recent successful analysis.

   **B. Update story frontmatter**:
   
   c. **`status`**:
    - If `--no-status-prompt` was passed → skip status mutation entirely.
    - Else if `status` is currently `BACKLOG`, use the **AskQuestion tool**:
      > "Analysis complete. Advance status from `BACKLOG` to `IN_PROGRESS` and queue this story for development? (Y/n)"
      Default `Y`. On `Y` set `status: IN_PROGRESS`; on `n` leave it as `BACKLOG`.
    - Else (status is `IN_PROGRESS`, `IN_TEST`, `BLOCKED`, or `DONE`) → leave untouched and surface a note in the report (re-running analysis at a later stage MUST never regress state).
   
   **Token efficiency**: This two-file mutation reads ~550 tokens total (50 for story frontmatter + 500 for metrics), avoiding the potentially 2000+ token story body.

5. **Report the diff**:

   ```
   📌 Story metadata updated:
      
      Metrics file (spdd/metrics/<story-id>.yml):
         - assignees.qa: <before> → <after>            [unchanged if preserved]
         - flow.analyzed_at: <before> → <after>
      
      Story file (requirements/<story-file>.md):
         - status: <before> → <after>                  [or "(unchanged: <status>)"]
      
      Token efficiency: Read ~550 tokens (avoided ~2000+ story body tokens).
   ```

   If `qa` had to fall back to `"unknown"`, append:
   > "⚠️ git user.name not configured — run `git config --global user.name \"Your Name\"`."

**Output**

Two files mutated:
1. **Metrics file**: `assignees.qa` and `flow.analyzed_at` updated
2. **Story frontmatter**: `status` updated (body untouched)

**Guardrails**

- Do NOT proceed without `--file` — this is a programmatic command
- Do NOT initialize metadata — that is `/spdd-meta-init`'s job; abort if missing
- MUST verify `metrics_file` pointer exists in story frontmatter
- MUST verify metrics file exists at the specified path
- Do NOT touch any metrics field other than `assignees.qa` and `flow.analyzed_at`
- Do NOT touch any story field other than `status`
- Do NOT overwrite a non-null `assignees.qa` — preserve the original analyst across re-runs
- Do NOT regress `status` (e.g., never move `IN_PROGRESS` back to `BACKLOG`); only the `BACKLOG → IN_PROGRESS` transition is allowed from this command
- Do NOT rewrite the story body or reorder keys
- `qa` MUST come from `git config user.name`; do NOT accept it as an argument
- `analyzed_at` MUST come from the local clock; do NOT accept it as an argument
- MUST read only lightweight story frontmatter (first ~10 lines), not the full file body
