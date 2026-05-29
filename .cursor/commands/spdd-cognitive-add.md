---
name: /spdd-cognitive-add
id: spdd-cognitive-add
category: Development
description: Append a cognitive discovery item to story's metrics file (unknown unknowns, known unknowns, tacit knowledge, or converted knowns)
---

Add a learning or discovery item to the `cognitive_discovery` section in the **metrics file**. This captures the epistemological journey from uncertainty to knowledge that occurred during implementation.

**Hybrid architecture efficiency**: Reads/writes only the metrics file (~500 tokens), never touches the story body. **~95% token savings** compared to full story reads.

**Schema v2 feature**: Structured knowledge transformation capture — what surprised us, what we clarified, and what hidden assumptions we surfaced.

**Input**:

```
/spdd-cognitive-add @requirements/<story-file>.md \
  --type <section> \
  --item "<discovery description>"
```

- `--type` (required): One of `unknown_unknowns`, `known_unknowns`, `tacit_knowledge`, `converted_to_known_knowns`
- `--item` (required): The discovery or learning in natural language (1-2 sentences)

Examples:

```
/spdd-cognitive-add @requirements/[User-story-7]token-billing.md \
  --type unknown_unknowns \
  --item "Login failure message may expose whether an email exists"

/spdd-cognitive-add @requirements/[User-story-3]invoice-export.md \
  --type tacit_knowledge \
  --item "All API errors follow the standard error response format"

/spdd-cognitive-add @requirements/[User-story-5]payment-webhook.md \
  --type converted_to_known_knowns \
  --item "Rate limiting is required (confirmed: 100 req/min per customer)"
```

**Steps**

1. **Validate input**

   - Both `--type` and `--item` are required. If either is missing, abort with a clear error.
   - `--type` MUST be one of: `unknown_unknowns_surfaced`, `known_unknowns`, `tacit_knowledge_extracted`, `converted_to_known_knowns` (use the full YAML key name).
   - `--item` MUST NOT be empty or a placeholder.

2. **Locate and read metrics file**

   a. **Read story frontmatter** (lightweight):
   - Read first ~10 lines to extract `metrics_file` pointer.
   - If no `metrics_file` pointer exists, abort with:
     > "Story missing `metrics_file` pointer. Run `/spdd-meta-init` to regenerate."
   
   b. **Read metrics file**:
   - Verify metrics file exists at the specified path.
   - Confirm `cognitive_discovery` section exists.
   - If the section is missing, abort with:
     > "Metrics file missing `cognitive_discovery` section. Run `/spdd-meta-init` to regenerate."

3. **Read current cognitive_discovery section**

   - Snapshot the current array for the specified `--type` from the metrics file.
   - Treat `null` or missing as `[]`.

4. **Mutate the metrics file** in-place via Read → StrReplace (never reorder keys):

   a. Append the new item to the appropriate array:

   ```yaml
   cognitive_discovery:
     unknown_unknowns_surfaced:
       - "<existing item 1>"
       - "<existing item 2>"
       - "<new item from --item>"
   ```

   - YAML strings containing `:`, `#`, or leading `-` MUST be double-quoted.
   - Preserve the indentation style (2-space indent per level).
   - Do NOT modify any other arrays within `cognitive_discovery`.
   - Do NOT deduplicate — if the same item appears twice, that's a human editing concern, not a tool concern.

5. **Report the addition** to the user:

   ```
   📚 Cognitive discovery recorded.
      
      Metrics file (spdd/metrics/<story-id>.yml):
         - Type: <--type>
         - Item: "<--item>"
         - Total entries in this category: <n>
      
      Token efficiency: Read ~500 tokens (avoided ~2000+ story body tokens).
   ```

**Output**

The metrics file's `cognitive_discovery` section mutated to append one new item. Story file untouched.

**Guardrails**

- Do NOT proceed without both `--type` and `--item`
- Do NOT accept arbitrary strings for `--type` — validate against the four allowed section names
- MUST verify `metrics_file` pointer exists in story frontmatter
- MUST verify metrics file exists at the specified path
- Do NOT create metadata — that is `/spdd-meta-init`'s job
- Do NOT touch any field other than the single specified `cognitive_discovery.<type>` array
- Do NOT deduplicate or validate item content — trust the user to curate meaningful discoveries
- MUST quote YAML strings that contain special characters
- Always preserve the existing key order and indentation style
- MUST read only the metrics file (~500 tokens), never the story body

**When to use this command**

Use `/spdd-cognitive-add` during or after implementation when you discover:

- **unknown_unknowns_surfaced**: Surprises or edge cases that were not anticipated during analysis (e.g., "Mobile Safari doesn't support WebP", "Postgres JSONB queries are 10x slower than expected")
- **known_unknowns**: Uncertainties you're aware of that require a decision or external input (e.g., "Account lockout policy requires product confirmation", "Should expired tokens return 401 or 403?")
- **tacit_knowledge_extracted**: Hidden conventions or patterns that were only in developers' heads (e.g., "All controllers use constructor injection", "Error codes follow the SPDD-XXXX format")
- **converted_to_known_knowns**: Previously unclear items that have now been resolved into concrete knowledge (e.g., "Rate limiting confirmed: 100 req/min per API key", "User enumeration must be prevented")

**Integration with retrospectives**

The `cognitive_discovery` section is designed to feed into:
- Sprint retrospectives (what did we learn?)
- Documentation updates (what hidden knowledge should we codify?)
- Improved requirements templates (what unknowns should we surface earlier?)
- AI prompt improvements (what context was missing from the original story?)
