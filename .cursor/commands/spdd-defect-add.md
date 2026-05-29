---
name: /spdd-defect-add
id: spdd-defect-add
category: Development
description: Append an inline defect record to story's metrics file with status, severity, and fix tracking
---

Add a defect discovered during implementation or QA to the `inline_defects` section in the **metrics file**. This creates an audit trail of issues found and resolved within the story lifecycle, before they escalate to separate bug tickets.

**Hybrid architecture efficiency**: Reads/writes only the metrics file (~500 tokens), never touches the story body. **~95% token savings** compared to full story reads.

**Schema v2 feature**: Rich defect tracking with status lifecycle, severity classification, and fix attribution — capturing "mini bugs" that get fixed during development without creating formal bug tickets.

**Input**:

```
/spdd-defect-add @requirements/<story-file>.md \
  --desc "<defect description>" \
  --severity <CRITICAL|HIGH|MEDIUM|LOW|INFO> \
  [--status <OPEN|FIXED|WONT_FIX|DEFERRED>] \
  [--found-by "<name>"]
```

- `--desc` (required): Clear description of the defect (1-2 sentences)
- `--severity` (required): One of `CRITICAL`, `HIGH`, `MEDIUM`, `LOW`, `INFO`
- `--status` (optional, default `OPEN`): Initial status
- `--found-by` (optional): Defaults to `git config user.name`

Examples:

```
/spdd-defect-add @requirements/[User-story-7]token-billing.md \
  --desc "Password field was not masked in the UI" \
  --severity HIGH

/spdd-defect-add @requirements/[User-story-3]invoice-export.md \
  --desc "CSV export generates incorrect header when no data" \
  --severity MEDIUM \
  --status FIXED \
  --found-by "wangwu"

/spdd-defect-add @requirements/[User-story-5]payment-webhook.md \
  --desc "Race condition when multiple webhooks arrive simultaneously" \
  --severity CRITICAL
```

**Steps**

1. **Validate input**

   - `--desc` and `--severity` are required. If either is missing, abort with a clear error.
   - `--severity` MUST be one of: `CRITICAL`, `HIGH`, `MEDIUM`, `LOW`, `INFO` (case-insensitive, normalize to uppercase).
   - `--status` (if provided) MUST be one of: `OPEN`, `FIXED`, `WONT_FIX`, `DEFERRED` (normalize to uppercase).
   - `--desc` MUST NOT be empty or a placeholder.

2. **Capture machine-derived defaults**

   - `found_by`: If `--found-by` not provided, run `git config user.name`, falling back to `$USER`, then `"unknown"` (with a warning in the report).
   - `status`: If `--status` not provided, default to `OPEN`.

3. **Locate and read metrics file**

   a. **Read story frontmatter** (lightweight):
   - Read first ~10 lines to extract `metrics_file` pointer.
   - If no `metrics_file` pointer exists, abort with:
     > "Story missing `metrics_file` pointer. Run `/spdd-meta-init` to regenerate."
   
   b. **Read metrics file**:
   - Verify metrics file exists at the specified path.
   - Confirm `inline_defects` array exists.

4. **Read current inline_defects array**

   - Snapshot the current `inline_defects` array from the metrics file.
   - Treat `null` or missing as `[]`.

5. **Mutate the metrics file** in-place via Read → StrReplace (never reorder keys):

   a. Append a new defect record:

   ```yaml
   inline_defects:
     - desc: "<--desc>"
       found_by: "<found_by>"
       severity: <--severity>
       status: <--status or OPEN>
       fixed_at: null              # populated later when status → FIXED
       fixed_in_commit: null       # populated later when status → FIXED
   ```

   - **Key order**: `desc`, `found_by`, `severity`, `status`, `fixed_at`, `fixed_in_commit` (schema v2 convention).
   - YAML strings containing `:`, `#`, or leading `-` MUST be double-quoted.
   - Preserve the indentation style already used in the file (2-space indent inside arrays).
   - Do NOT modify any earlier `inline_defects` entries — they are immutable history (updates should use `/spdd-defect-update`).

6. **Report the addition** to the user:

   ```
   🐛 Inline defect recorded.
      
      Metrics file (spdd/metrics/<story-id>.yml):
         - Severity: <severity>
         - Status: <status>
         - Description: "<desc>"
         - Found by: <found_by>
         - Total inline defects: <n>
      
      Token efficiency: Read ~500 tokens (avoided ~2000+ story body tokens).
   
   To mark this defect as fixed, run:
      /spdd-defect-update @requirements/<story-file>.md --index <n-1> --status FIXED [--commit <sha>]
   ```

**Output**

The metrics file's `inline_defects` array mutated to append one new defect record with initial status and severity. Story file untouched.

**Guardrails**

- Do NOT proceed without both `--desc` and `--severity`
- Do NOT accept arbitrary strings for `--severity` or `--status` — validate against enums
- MUST verify `metrics_file` pointer exists in story frontmatter
- MUST verify metrics file exists at the specified path
- Do NOT create metadata — that is `/spdd-meta-init`'s job
- Do NOT touch any field other than the `inline_defects` array
- Do NOT modify existing defect entries — only append new ones (updates use `/spdd-defect-update`)
- Do NOT set `fixed_at` or `fixed_in_commit` when adding a new defect — those are populated by `/spdd-defect-update` when status changes to `FIXED`
- MUST normalize `--severity` and `--status` to uppercase
- Always preserve the existing key order and indentation style
- MUST read only the metrics file (~500 tokens), never the story body

**When to use this command**

Use `/spdd-defect-add` when:

- QA discovers a bug during testing that will be fixed in the same story cycle (no need to create a separate bug ticket)
- A developer discovers an issue during implementation (e.g., "I forgot to add validation", "This edge case crashes")
- Code review surfaces a defect that needs immediate attention
- The defect is scoped within the current story and doesn't require separate tracking

**Do NOT use this for:**
- Bugs that affect production or other stories → create a proper bug ticket and add to `related_bugs: []` instead
- Feature requests or scope changes → those need new stories
- Blockers that prevent story progress → use `/spdd-block` instead

**Related commands**

- `/spdd-defect-update` - Update an existing defect's status, add fix timestamp/commit
- `/spdd-defect-list` - List all defects for a story (filtered by status/severity)
- `/spdd-meta-migrate` - Upgrade v1 stories to v2 schema (adds rich inline_defects structure)

**Example workflow**

```bash
# QA finds a bug during testing
/spdd-defect-add @requirements/story.md \
  --desc "Null pointer when customer has no payment method" \
  --severity HIGH

# Developer fixes it
# ... code changes ...
git commit -m "fix: handle null payment method"

# Mark as fixed
/spdd-defect-update @requirements/story.md \
  --index 0 \
  --status FIXED \
  --commit abc123f

# Story proceeds to DONE with full defect audit trail
```
