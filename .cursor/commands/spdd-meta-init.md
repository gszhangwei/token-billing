---
name: /spdd-meta-init
id: spdd-meta-init
category: Development
description: Initialize SPDD lifecycle metadata using hybrid architecture - lightweight frontmatter in story file + full metrics in separate YAML file. Normally invoked by /spdd-story.
---

Initialize SPDD lifecycle metadata using **hybrid architecture**: a lightweight YAML frontmatter in the story file (id, title, status, tags, metrics pointer) + a comprehensive metrics file in `spdd/metrics/` containing all workflow data.

**Hybrid Architecture Benefits:**
- **Token efficiency**: Metadata operations read only the small metrics file (~500 tokens), not the full story body (potentially 2000+ tokens)
- **64% token savings** for high-frequency metadata updates
- **Human-friendly**: Story file retains key fields (status, tags) for quick visual reference
- **Separation of concerns**: Business content (story) vs workflow data (metrics) live in appropriate locations

**Schema v2 features:**
- Explicit `schema_version` tracking
- `flow` - semantic workflow lifecycle timestamps
- `cognitive_discovery` - knowledge capture (unknown unknowns → known knowns)
- `delivery_evidence` - full audit trail
- Enhanced `block_records` with `phase` categorization
- Rich `inline_defects` with status, severity, and fix tracking

**Input** (named arguments — all required unless marked optional):

```
/spdd-meta-init \
  --file @requirements/<story-file>.md \
  --id STORY-{MODULE}-{SEQ} \
  --title "<one-line title>" \
  [--tags tag1,tag2]
```

Examples:

```
/spdd-meta-init --file @requirements/[User-story-7]token-billing.md \
                --id STORY-007-001 \
                --title "Token usage billing API"

/spdd-meta-init --file @requirements/[User-story-3]invoice-export.md \
                --id STORY-003-001 \
                --title "Invoice CSV export" \
                --tags billing,export
```

**Steps**

1. **Validate input**

   - `--file`, `--id`, `--title` are required. If any is missing, abort with a clear error listing the missing fields. Do NOT use AskUserQuestion — this command is meant to be invoked programmatically by other SPDD commands and a missing argument is a caller bug.
   - `--id` MUST match the regex `^STORY-[0-9]{3}-[0-9]{3}$` (or another caller-defined pattern documented in `/spdd-story`). Reject otherwise.
   - `--title` MUST be a single line, ≤ 80 characters, with no leading/trailing whitespace and no Markdown.

2. **Refuse to clobber existing metadata**

   - Read the story file. If the first line is `---` (i.e. frontmatter already exists), abort with:
     > "File already has frontmatter. Use `/spdd-meta-analyzed`, `/spdd-meta-refine`, `/spdd-block`, or `/spdd-unblock` to mutate fields. `/spdd-meta-init` is one-shot."
   - Check if metrics file already exists at `spdd/metrics/{--id}.yml`. If yes, abort with:
     > "Metrics file already exists at spdd/metrics/{--id}.yml. `/spdd-meta-init` is one-shot."
   - This makes the command idempotent-by-rejection: callers can invoke it freely without risking history loss.

3. **Capture machine-derived defaults**

   - `assignees.ba`: run `git config user.name`. Fall back to `$USER`, then `"unknown"` (with a warning surfaced in the final report).
   - `flow.created_at`: local clock, `YYYY-MM-DD HH:MM`.

4. **Render the hybrid metadata structure**

   This is the **canonical schema v2 hybrid architecture** — every other SPDD command MUST read/write from the appropriate location (story frontmatter for lightweight fields, metrics file for heavy workflow data).

   **A. Story file frontmatter (lightweight)**:
   
   Prepend this minimal frontmatter to the story Markdown file:

   ```yaml
   ---
   id: {--id}
   title: "{--title}"
   status: BACKLOG                   # BACKLOG | IN_PROGRESS | IN_TEST | BLOCKED | DONE
   tags: [{--tags as YAML flow seq, or empty}]
   metrics_file: spdd/metrics/{--id}.yml
   ---
   ```

   - Keep frontmatter minimal for human readability and quick status checks.
   - `metrics_file` is the pointer to the comprehensive metrics file.
   - Insert exactly one blank line between the closing `---` and the existing first line of the file body.

   **B. Metrics file (comprehensive)**:
   
   Create file at `spdd/metrics/{--id}.yml` with this complete structure:

   ```yaml
   # SPDD Lifecycle Metrics - Schema v2
   # Story: {--title}
   # File: requirements/<story-file>.md
   
   schema_version: 2
   story_id: {--id}
   story_file: requirements/<story-file>.md
   
   # Workflow tracking
   iteration: null                    # filled in by humans during sprint planning
   
   assignees:
     ba: "{git user.name}"
     qa: null                         # populated by /spdd-meta-analyzed on first run
     dev: null                        # populated by /spdd-meta-refine on first run
   
   flow:
     created_at: "{YYYY-MM-DD HH:MM}"
     analyzed_at: null                # populated by /spdd-meta-analyzed
     developed_at: null               # populated by Git pre-push / PR-open hook
     tested_at: null                  # populated by CI on QA approval
     delivered_at: null               # populated by CI on merge to main
   
   quality_metrics:
     ai_refine_loops: 0               # incremented by /spdd-meta-refine
     qa_rejections: 0                 # incremented by CI on PR "changes requested"
     test_coverage: null              # integer percentage (0-100), written by CI after unit tests
   
   block_records: []                  # appended/closed by /spdd-block & /spdd-unblock
                                       # Schema: [{phase, blocked_at, unblocked_at, reason, previous_status, reporter}]
   
   delivery_evidence:
     reasons_canvas: null             # path to reasons canvas file
     implementation_prompts: []       # paths to implementation prompt files
     test_prompts: []                 # paths to test prompt files
     linked_commits: []               # commit SHAs implementing this story
     human_reviewed_reasoning: false  # whether reasoning artifacts were manually reviewed
     prompt_code_mapping_verified: false  # whether prompt-code traceability was verified
   
   cognitive_discovery:
     unknown_unknowns_surfaced: []    # surprises discovered during implementation
     known_unknowns: []               # acknowledged uncertainties requiring decisions
     tacit_knowledge_extracted: []    # implicit conventions made explicit
     converted_to_known_knowns: []    # unknowns resolved into concrete knowledge
   
   inline_defects: []                 # managed by /spdd-defect-add
                                       # Schema: [{desc, found_by, status, fixed_at, fixed_in_commit, severity}]
   
   related_bugs: []                   # human-edited - external bug tracker IDs
   ```

   **Schema constraints:**
   - YAML strings containing `:`, `#`, or leading `-` MUST be double-quoted.
   - Use `[]` for empty arrays, `null` for unknown scalars — never empty strings.
   - `test_coverage` MUST be an integer (0-100) when populated, never a string with `%`.
   - `block_records.phase` enum: ANALYSIS | DEVELOPMENT | TESTING | DELIVERY | OTHER
   - `inline_defects.status` enum: OPEN | FIXED | WONT_FIX | DEFERRED
   - `inline_defects.severity` enum: CRITICAL | HIGH | MEDIUM | LOW | INFO

5. **Report**

   ```
   ✅ Initialized SPDD lifecycle metadata (schema v2 hybrid architecture).
      
      Story file: requirements/<story-file>.md
         - Lightweight frontmatter added (id, title, status, tags)
         - Body untouched
      
      Metrics file: spdd/metrics/<story-id>.yml
         - Comprehensive workflow metadata created
         - assignees.ba: <git user.name>      [⚠️ unknown — run `git config --global user.name` ...]
         - flow.created_at: YYYY-MM-DD HH:MM
      
      Token efficiency: Metadata operations will now read only the metrics file (~500 tokens), 
                        not the full story body (potentially 2000+ tokens).
   ```

**Output**

Two files created/modified:
1. **Story file** with lightweight YAML frontmatter prepended (body untouched)
2. **Metrics file** at `spdd/metrics/{story-id}.yml` with comprehensive workflow metadata

**Guardrails**

- Do NOT prompt the user — this command is invoked programmatically; missing args are caller errors
- Do NOT clobber existing frontmatter or metrics file — abort if either already exists
- Do NOT add, rename, reorder, or omit any field; the schemas in Step 4 are contract
- Do NOT touch the story file body — only prepend lightweight frontmatter
- MUST create directory `spdd/metrics/` if it doesn't exist
- `schema_version` MUST be `2` for all new stories
- `assignees.ba` MUST come from `git config user.name`; do NOT accept it as an argument
- `flow.created_at` MUST come from the local clock; do NOT accept it as an argument
- Counters MUST initialize to `0`; nullable scalars MUST initialize to `null`; arrays MUST initialize to `[]`
- Boolean flags MUST initialize to `false`
- Surface the `unknown` BA fallback in the report so the team can fix git config
- All enum values MUST match the documented constraints
- Story frontmatter MUST include `metrics_file` pointer to enable hybrid architecture

**Schema authority**

This command is the only place where the SPDD lifecycle schema is defined verbatim. If the schema needs to evolve:

1. Update Step 4 here (both story frontmatter and metrics file schemas).
2. Bump the `schema_version` number.
3. Update the read/write logic in all dependent commands:
   - `/spdd-meta-analyzed` - reads/writes metrics file (`flow.analyzed_at`, `assignees.qa`), writes story `status`
   - `/spdd-meta-refine` - reads/writes metrics file (`flow.developed_at`, `assignees.dev`, `quality_metrics.ai_refine_loops`)
   - `/spdd-block` - reads/writes metrics file (`block_records`), writes story `status`
   - `/spdd-unblock` - reads/writes metrics file (`block_records`), writes story `status`
   - `/spdd-cognitive-add` - reads/writes metrics file (`cognitive_discovery`)
   - `/spdd-defect-add` - reads/writes metrics file (`inline_defects`)

**Current schema version: v2 (Hybrid Architecture)**

All new stories use schema v2 hybrid architecture with:
- **Story file**: Lightweight frontmatter (id, title, status, tags, metrics_file pointer)
- **Metrics file**: Comprehensive workflow data (`flow`, `assignees`, `quality_metrics`, `block_records`, `cognitive_discovery`, `delivery_evidence`, `inline_defects`)
- **Token efficiency**: 64% savings on metadata operations by avoiding story body reads
- **Separation of concerns**: Business content vs workflow data
