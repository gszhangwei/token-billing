---
name: /spdd-meta-validate
id: spdd-meta-validate
category: Development
description: Validate SPDD story frontmatter + metrics file conform to canonical schema v2 hybrid architecture
---

Validate that a story's **hybrid architecture** conforms to the canonical SPDD lifecycle schema v2: lightweight story frontmatter + comprehensive metrics file. Reports missing fields, invalid enum values, type mismatches, and logical inconsistencies. Useful for CI checks, pre-commit hooks, and manual quality assurance.

**Hybrid architecture validation**: Checks both story frontmatter (id, title, status, tags, metrics_file pointer) and metrics file (all workflow data).

**Input**:

```
/spdd-meta-validate @requirements/<story-file>.md [--strict]
```

- `--strict` (optional): Fail on warnings (e.g., `null` values that should be populated by now based on `status`). Default: warnings are non-fatal.

Examples:

```
# Validate a single story (warnings allowed)
/spdd-meta-validate @requirements/[User-story-7]token-billing.md

# Strict mode (warnings are errors)
/spdd-meta-validate @requirements/[User-story-3]invoice-export.md --strict
```

**Steps**

1. **Read and validate story frontmatter (lightweight)**

   - Read the story file (first ~20 lines sufficient for frontmatter).
   - Check for YAML frontmatter (first line must be `---`).
   - If no frontmatter, abort:
     > "File has no SPDD lifecycle frontmatter. Run `/spdd-meta-init` first."
   
   - Check that story frontmatter contains:
     - `id` (required)
     - `title` (required)
     - `status` (required)
     - `tags` (required, allow empty array)
     - `metrics_file` (required pointer to metrics file)
   
   - Extract `metrics_file` path. If missing, error:
     > "Story missing `metrics_file` pointer. All stories must use hybrid architecture (schema v2)."

2. **Read and validate metrics file (comprehensive)**

   - Verify metrics file exists at the path specified in `metrics_file`.
   - If missing, error:
     > "Metrics file not found at `{metrics_file}`. Run `/spdd-meta-init` to regenerate."
   
   - Parse the metrics file and check schema version:
     - If `schema_version` is missing, error:
       > "Metrics file missing `schema_version` key. All stories must use schema v2."
     - If `schema_version: 2` → proceed with validation
     - If `schema_version` > 2 → warn "Unknown schema version X; validating against v2 as best-effort"
     - If `schema_version: 1` or other value → error:
       > "Invalid schema version in metrics file. All stories must use `schema_version: 2`."

3. **Validate required keys in metrics file**

   Check that all schema v2 metrics keys exist:
   - `schema_version`
   - `story_id`
   - `story_file`
   - `iteration`
   - `assignees` (with nested `ba`, `qa`, `dev`)
   - `flow` (with nested `created_at`, `analyzed_at`, `developed_at`, `tested_at`, `delivered_at`)
   - `quality_metrics` (with nested `ai_refine_loops`, `qa_rejections`, `test_coverage`)
   - `block_records`
   - `delivery_evidence`
   - `cognitive_discovery`
   - `inline_defects`
   - `related_bugs`

   Report missing keys as **errors**.

3. **Validate enum fields**

   Check that enum-typed fields use only allowed values:

   - `status`: Must be one of `BACKLOG`, `IN_PROGRESS`, `IN_TEST`, `BLOCKED`, `DONE`
   - `block_records[*].phase`: Must be one of `ANALYSIS`, `DEVELOPMENT`, `TESTING`, `DELIVERY`, `OTHER`
   - `inline_defects[*].status`: Must be one of `OPEN`, `FIXED`, `WONT_FIX`, `DEFERRED`
   - `inline_defects[*].severity`: Must be one of `CRITICAL`, `HIGH`, `MEDIUM`, `LOW`, `INFO`

   Report invalid enum values as **errors**.

4. **Validate type constraints**

   - `schema_version`: Must be integer `2`
   - `id`: Must be a string matching `STORY-\d{3}-\d{3}` (or other project-specific pattern)
   - `title`: Must be a string, ≤ 80 characters
   - `tags`: Must be an array (allow empty `[]`)
   - `quality_metrics.ai_refine_loops`: Must be an integer ≥ 0
   - `quality_metrics.qa_rejections`: Must be an integer ≥ 0
   - `quality_metrics.test_coverage`: Must be `null` or an integer 0-100 (NOT a string like "92%")
   - `flow.*`: Must be `null` or match `YYYY-MM-DD HH:MM` format
   - All `*_at` fields in `block_records`: Must match `YYYY-MM-DD HH:MM` format
   - `assignees.*`: Must be `null` or a non-empty string
   - `block_records`, `inline_defects`, `related_bugs`, all `delivery_evidence` arrays, all `cognitive_discovery` arrays: Must be arrays (allow empty `[]`)

   Report type mismatches as **errors**.

5. **Validate logical consistency**

   - If `status` is `IN_PROGRESS`, warn if `flow.analyzed_at` is still `null` (should have been set by `/spdd-meta-analyzed`).
   - If `status` is `IN_TEST`, warn if `flow.developed_at` is still `null`.
   - If `status` is `DONE`, warn if any of `analyzed_at`, `developed_at`, `tested_at`, `delivered_at` are `null`.
   - If `status` is `BLOCKED`, error if there is no open block record (one with `unblocked_at: null`).
   - If `status` is NOT `BLOCKED`, warn if there is an open block record (suggests stale block).
   - If `ai_refine_loops` > 0, warn if `assignees.dev` is still `null`.
   - If `test_coverage` is not `null`, warn if it's outside the 0-100 range.

   Report logical issues as **warnings** (or errors if `--strict`).

6. **Validate structured field schemas**

   - `delivery_evidence.reasons_canvas`: Must be `null` or a non-empty string (path)
   - `delivery_evidence.implementation_prompts`: Must be an array (allow empty)
   - `delivery_evidence.test_prompts`: Must be an array (allow empty)
   - `delivery_evidence.linked_commits`: Must be an array of strings (7-40 chars each, typically commit SHAs)
   - `delivery_evidence.human_reviewed_reasoning`: Must be a boolean
   - `delivery_evidence.prompt_code_mapping_verified`: Must be a boolean
   - `cognitive_discovery.*`: All four arrays (`unknown_unknowns_surfaced`, `known_unknowns`, `tacit_knowledge_extracted`, `converted_to_known_knowns`) must exist and be arrays (allow empty)
   - `block_records[*]`: Must have `phase`, `blocked_at`, `unblocked_at`, `reason`, `previous_status`, `reporter` keys
   - `inline_defects[*]`: Must have `desc`, `found_by`, `severity`, `status`, `fixed_at`, `fixed_in_commit` keys

   Report structural issues as **errors**.

7. **Report validation results**

   Collect all errors and warnings, then emit a summary:

   **If no errors and no warnings**:
   ```
   ✅ Story metadata is valid (schema v2 hybrid architecture).
      
      Story file: requirements/<story-file>.md
         - Lightweight frontmatter valid (id, title, status, tags)
      
      Metrics file: spdd/metrics/<story-id>.yml
         - Comprehensive metadata valid
         - All required fields present
         - All enum values valid
         - All type constraints satisfied
         - All logical consistency checks passed
   ```

   **If warnings only** (and not `--strict`):
   ```
   ⚠️  Story metadata is valid with warnings.
      
      Story file: requirements/<story-file>.md ✓
      Metrics file: spdd/metrics/<story-id>.yml ⚠️
      Schema: v2 hybrid architecture
      
   Warnings (non-fatal):
      - flow.developed_at is null but status is IN_TEST (expected to be set)
      - test_coverage is 105 (expected 0-100)
      
   These warnings suggest incomplete metadata but do not block workflow.
   ```

   **If errors**:
   ```
   ❌ Story metadata validation FAILED.
      
      Story file: requirements/<story-file>.md ❌
         - Missing required key: metrics_file
      
      Metrics file: spdd/metrics/<story-id>.yml ❌
         - Missing required key: cognitive_discovery
         - Invalid enum value for status: "IN PROGRESS" (expected one of: BACKLOG, IN_PROGRESS, IN_TEST, BLOCKED, DONE)
         - Type mismatch: quality_metrics.test_coverage is string "92%" (expected integer 0-100 or null)
         - Logical inconsistency: status is BLOCKED but no open block_records entry exists
      
   Fix these errors and re-run validation.
   ```

   **If `--strict` and warnings exist**: Treat warnings as errors and fail.

**Output**

A validation report listing all errors and warnings. Exit status (for CI):
- 0 if valid (no errors, or warnings in non-strict mode)
- 1 if invalid (any errors, or warnings in strict mode)

**Guardrails**

- Do NOT modify any files — this is a read-only validation tool
- Do NOT guess or infer missing values — only report what is present
- MUST validate hybrid architecture — both story frontmatter and metrics file
- MUST verify `metrics_file` pointer exists in story frontmatter
- MUST verify metrics file exists at the specified path
- MUST validate against schema v2 — stories without `schema_version: 2` in metrics file are errors
- MUST distinguish between errors (schema violations) and warnings (suspicious but not invalid)
- MUST report all issues found, not just the first one (collect errors, then report)
- For `schema_version` > 2, validate as best-effort against v2 (warn about unknown version)
- MUST read only lightweight story frontmatter (~50 tokens) + metrics file (~500 tokens), not story body

**Use cases**

1. **Manual QA before committing**:
   ```bash
   /spdd-meta-validate @requirements/[User-story-7]token-billing.md
   ```

2. **Pre-commit hook** (validate all changed stories):
   ```bash
   #!/bin/bash
   # .git/hooks/pre-commit
   for file in $(git diff --cached --name-only | grep '^requirements/.*\.md$'); do
     /spdd-meta-validate @"$file" --strict || exit 1
   done
   ```

3. **CI pipeline** (validate all stories):
   ```yaml
   # .github/workflows/validate-stories.yml
   - name: Validate story metadata
     run: |
       for file in requirements/*.md; do
         /spdd-meta-validate @"$file" --strict
       done
   ```

4. **Find stories with incomplete metadata**:
   ```bash
   for file in requirements/*.md; do
     /spdd-meta-validate @"$file" 2>&1 | grep -i "warning"
   done
   ```

**Common validation errors and fixes**

| Error | Cause | Fix |
|-------|-------|-----|
| `Missing schema_version key` | Story missing version field | Run `/spdd-meta-init` to regenerate |
| `Missing required key: cognitive_discovery` | Incomplete frontmatter | Run `/spdd-meta-init` to regenerate |
| `Invalid enum value for status: "WIP"` | Custom status not in schema | Change to `IN_PROGRESS` |
| `Type mismatch: test_coverage is string` | CI wrote "92%" instead of 92 | Fix CI script to write integer |
| `Logical inconsistency: status is BLOCKED but no open block_records` | Manual edit broke consistency | Run `/spdd-block` or fix `status` manually |
| `flow.analyzed_at is null but status is IN_PROGRESS` | Workflow skipped analysis step | Run `/spdd-meta-analyzed` to backfill |

**Related commands**

- `/spdd-meta-init` - Initialize schema v2 frontmatter on a new story
- `/spdd-meta-analyzed` - Populate `flow.analyzed_at` and `assignees.qa`
- `/spdd-meta-refine` - Increment `ai_refine_loops` counter
- `/spdd-cognitive-add` - Add cognitive discoveries
- `/spdd-defect-add` - Add inline defects
