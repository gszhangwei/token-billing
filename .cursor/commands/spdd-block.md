---
name: /spdd-block
id: spdd-block
category: Development
description: Mark story as BLOCKED (in story file) and append block_records entry to metrics file with phase, reason and timestamp
---

Record an external blocker using **hybrid architecture**: updates story `status` to `BLOCKED` and appends a block record to the metrics file. The previous `status` and inferred workflow `phase` are preserved so `/spdd-unblock` can restore cleanly.

**Hybrid architecture efficiency**: Reads only lightweight story frontmatter (~50 tokens) and metrics file (~500 tokens), avoiding story body. **~80% token savings**.

**Phase inference**: Automatically categorizes blockers by workflow phase (ANALYSIS, DEVELOPMENT, TESTING, DELIVERY) based on current status.

**Input**: `/spdd-block @requirements/<story-file>.md -m "<reason>"`

Both arguments are required. Examples:

```
/spdd-block @requirements/[User-story-7]token-billing.md -m "Waiting on payments API quota approval"
/spdd-block @requirements/[User-story-3]invoice-export.md -m "Blocked by upstream story User-story-2"
```

**Steps**

1. **Validate input**

   a. **If no `@` story file is provided**, use the **AskUserQuestion tool** (open-ended) to ask:
   > "Which story should be marked as BLOCKED? Provide a path under `requirements/`."

   b. **If no `-m "<reason>"` is provided**, use the **AskUserQuestion tool** to ask:
   > "What is the blocker reason? (Be concrete — this is logged in the audit trail.)"

   **IMPORTANT**: Do NOT proceed without both inputs. Reasons MUST NOT be empty strings or placeholders.

2. **Locate and read metadata**

   a. **Read story frontmatter** (lightweight):
   - Read first ~10 lines to extract `status` and `metrics_file` pointer.
   - If no `metrics_file` pointer exists, abort with:
     > "Story missing `metrics_file` pointer. Run `/spdd-meta-init` to regenerate."
   
   b. **Read metrics file**:
   - Verify metrics file exists at the specified path.
   - Read the metrics file to access `block_records`.

3. **Capture the previous status, infer phase, and capture reporter identity**

   - Snapshot the current `status` value from story frontmatter into `prev_status`.
   - If `prev_status` is already `BLOCKED`, read `block_records` from metrics file and surface the most recent open entry (one whose `unblocked_at` is null). Exit with:
     > "Story is already BLOCKED. Call `/spdd-unblock` first to close the current blocker."
   
   - **Infer the workflow phase** from `prev_status` using this mapping:
     - `BACKLOG` → `ANALYSIS` (requirements/analysis phase)
     - `IN_PROGRESS` → `DEVELOPMENT` (coding/implementation phase)
     - `IN_TEST` → `TESTING` (QA validation phase)
     - `DONE` → `DELIVERY` (post-merge deployment/delivery)
     - Any other status → `OTHER` (unknown/custom status)
   
   - Run `git config user.name` to capture `reporter` (fall back to `$USER`, then `"unknown"`).
   - Capture the current local timestamp `now = YYYY-MM-DD HH:MM`.

4. **Mutate metadata across both files** via StrReplace:

   **A. Update story frontmatter**:
   - Set `status: BLOCKED` (preserving all other frontmatter keys).

   **B. Update metrics file**:
   - Append a new entry to `block_records` (create the array if currently `[]`):

   ```yaml
   block_records:
     - phase: "{inferred phase}"        # ANALYSIS | DEVELOPMENT | TESTING | DELIVERY | OTHER
       blocked_at: "{now}"
       unblocked_at: null
       reason: "{reason from -m}"
       previous_status: "{prev_status}"
       reporter: "{reporter}"
   ```

   - **Key order matters**: `phase` comes first to make block records sortable/filterable by workflow stage.
   - YAML strings containing `:`, `#`, or leading `-` MUST be double-quoted.
   - Preserve the indentation style (2-space indent inside arrays).
   - Do NOT modify any earlier `block_records` entries — they are immutable history.

5. **Report the change** to the user:

   ```
   Story marked as BLOCKED.
      
      Story file (requirements/<story-file>.md):
         - Status: <prev_status> → BLOCKED
      
      Metrics file (spdd/metrics/<story-id>.yml):
         - Block record added:
            Phase: <inferred phase>
            Reason: <reason>
            Logged at: <now> by <reporter>
      
      Token efficiency: Read ~550 tokens (avoided ~2000+ story body tokens).

   When the blocker is resolved, run:
      /spdd-unblock @requirements/<story-file>.md
   ```

**Output**

Two files mutated:
1. **Story frontmatter**: `status` changed to `BLOCKED` (body untouched)
2. **Metrics file**: New block record appended to `block_records` array

**Guardrails**

- Do NOT proceed without both a story file and a non-empty reason
- Do NOT create metadata — abort if `metrics_file` pointer is missing
- MUST verify `metrics_file` pointer exists in story frontmatter
- MUST verify metrics file exists at the specified path
- Do NOT touch any story field other than `status`
- Do NOT touch any metrics field other than `block_records`
- Do NOT modify previously closed `block_records` entries — they are immutable audit history
- Do NOT push a second open block record on top of an existing open one — fail fast and require `/spdd-unblock` first
- Do NOT prompt for the reporter or phase — both are derived automatically
- MUST infer `phase` from `prev_status` using the documented mapping (Step 3)
- MUST place `phase` as the first field in the block record
- Always preserve the existing key order and indentation style
- MUST read only lightweight story frontmatter (~50 tokens) and metrics file (~500 tokens), not story body
