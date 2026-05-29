---
name: /spdd-unblock
id: spdd-unblock
category: Development
description: Close open block_records entry in metrics file, restore story status, and stamp unblocked_at
---

Close an open blocker using **hybrid architecture**: updates story `status` back to the pre-block value and closes the block record in the metrics file. This is the symmetric counterpart to `/spdd-block`.

**Hybrid architecture efficiency**: Reads only lightweight story frontmatter (~50 tokens) and metrics file (~500 tokens), avoiding story body. **~80% token savings**.

**Input**: `/spdd-unblock @requirements/<story-file>.md [-m "<resolution note>"]`

The `-m` note is optional but recommended — it is appended to the closed block record as `resolution`. Examples:

```
/spdd-unblock @requirements/[User-story-7]token-billing.md
/spdd-unblock @requirements/[User-story-3]invoice-export.md -m "Quota approved by ops; proceeding"
```

**Steps**

1. **Validate input**

   a. **If no `@` story file is provided**, use the **AskUserQuestion tool** to ask:
   > "Which story should be unblocked? Provide a path under `requirements/`."

   **IMPORTANT**: Do NOT proceed without a story file. The `-m` note is optional.

2. **Locate and read metadata**

   a. **Read story frontmatter** (lightweight):
   - Read first ~10 lines to extract `status` and `metrics_file` pointer.
   - If `status` is not `BLOCKED`, abort with:
     > "Story is not currently BLOCKED (status = <status>). Nothing to unblock."
   - If no `metrics_file` pointer exists, abort with:
     > "Story missing `metrics_file` pointer. Run `/spdd-meta-init` to regenerate."
   
   b. **Read metrics file**:
   - Verify metrics file exists at the specified path.
   - Read the metrics file to access `block_records`.

3. **Locate the open block record**

   - Find the LAST entry in `block_records` whose `unblocked_at` is `null`. This is the active blocker.
   - If no such entry exists (e.g., schema drift, manual edits), abort with:
     > "No open block record found. Cannot unblock. Check metrics file manually."

4. **Capture closing metadata**

   - Run `git config user.name` to capture `closer` (fall back to `$USER`, then `"unknown"`).
   - Capture `now = YYYY-MM-DD HH:MM` from the local clock.
   - Read `previous_status` from the open block record — this is the status to restore.

5. **Mutate metadata across both files** via StrReplace:

   **A. Update story frontmatter**:
   - Set `status: <previous_status>` (the value snapshotted by `/spdd-block`).

   **B. Update metrics file**:
   - Update the open block record by setting:
     - `unblocked_at: "{now}"`
     - `closer: "{closer}"`
     - `resolution: "{-m note}"` — only add this key if `-m` was provided; do NOT add it as an empty string.
   - Do NOT modify any other entries in `block_records`.

6. **Report the change** to the user:

   ```
   Story unblocked.
      
      Story file (requirements/<story-file>.md):
         - Status: BLOCKED → <previous_status>
      
      Metrics file (spdd/metrics/<story-id>.yml):
         - Block record closed:
            Phase: <phase>
            Block duration: <blocked_at> → <now>
            Closed by: <closer>
            Resolution: <-m note OR "(none)">
      
      Token efficiency: Read ~550 tokens (avoided ~2000+ story body tokens).
   ```

**Output**

Two files mutated:
1. **Story frontmatter**: `status` restored to pre-block value (body untouched)
2. **Metrics file**: Active block record closed with `unblocked_at`, `closer`, and (optionally) `resolution`. Block history preserved.

**Guardrails**

- Do NOT proceed without a story file
- MUST verify `metrics_file` pointer exists in story frontmatter
- MUST verify metrics file exists at the specified path
- Do NOT touch any story field other than `status`
- Do NOT touch any metrics field other than the single open `block_records` entry
- Do NOT modify any closed `block_records` entries — they are immutable audit history
- Do NOT invent or fabricate a block record if none is open — abort with clear error
- Do NOT add `resolution: ""` when no `-m` note was provided — omit the key instead
- Always restore exactly the `previous_status` recorded by `/spdd-block` — never guess
- Always preserve the existing key order and indentation style
- Do NOT add or remove the `phase` field when closing a record — only set `unblocked_at`, `closer`, and optionally `resolution`
- MUST read only lightweight story frontmatter (~50 tokens) and metrics file (~500 tokens), not story body
