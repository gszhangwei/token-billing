---
name: /spdd-prompt-update
id: spdd-prompt-update
category: Development
description: Update an existing SPDD prompt file with new requirements or architectural changes while preserving the REASONS Canvas structure
---

Update an existing SPDD (Structured Prompt-Driven Development) prompt file with new requirements, architectural changes, or refinements while maintaining the REASONS Canvas structure and following all specification rules.

**Input**: The argument after `/spdd-prompt-update` includes the prompt file reference and the update instructions.

**Examples**:

```
# Update with architectural principles
/spdd-prompt-update @spdd/prompt/SPDD-XXX-202603131758-[Feat]-api-token-usage-billing.md
Add three-layer architecture with dependency inversion principle

# Update with new requirements
/spdd-prompt-update @spdd/prompt/SPDD-XXX-202603131758-[Feat]-api-token-usage-billing.md
Add support for batch usage submission

# Update specific section
/spdd-prompt-update @spdd/prompt/SPDD-XXX-202603131758-[Feat]-api-token-usage-billing.md
Update Safeguards section to add rate limiting constraints
```

**Steps**

1. **Validate input**

   a. **If no prompt file provided**, use the **AskUserQuestion tool** to ask:
   > "Please provide the path to the SPDD prompt file to update (e.g., `@spdd/prompt/xxx.md`)"

   b. **If no update instructions provided**, use the **AskUserQuestion tool** to ask:
   > "What changes would you like to make to this prompt? (e.g., new requirements, architectural changes, constraint updates)"

   **IMPORTANT**: Do NOT proceed without both the file path and update instructions.

2. **Read and analyze the existing prompt**

   a. Read the entire SPDD prompt file
   b. Identify all existing REASONS sections:
    - Requirements
    - Entities
    - Approach
    - Structure
    - Operations
    - Norms
    - Safeguards
      c. Understand the current architecture, entities, and constraints

3. **Analyze the update request**

   Determine which sections need to be updated based on the change request:

   | Change Type | Affected Sections |
      |-------------|-------------------|
   | New functional requirement | R, E, A, S, O, possibly N, S |
   | Architectural change | A, S, O, N |
   | New entity/relationship | E, S, O |
   | New constraint/safeguard | S (Safeguards), possibly O |
   | Coding standard change | N, O |
   | Bug fix in specification | Targeted section only |

4. **Read relevant codebase context (if needed)**

   If the update involves:
    - New entities → Read existing entity classes
    - New patterns → Read existing similar implementations
    - New integrations → Read related services/repositories

5. **Apply updates to affected sections**

   For each affected section:

   a. **Preserve unchanged content** - Do NOT rewrite sections that don't need changes
   b. **Integrate changes coherently** - Ensure new content fits with existing content
   c. **Maintain consistency** - Cross-check that changes are reflected across related sections
   d. **Follow REASONS construction guidance** - Apply the same quality standards as initial generation

   **Section-specific guidance**:

    - **Requirements**: Update if business goal changes
    - **Entities**: Add/modify entities, update Mermaid diagram
    - **Approach**: Update strategies, add new architectural decisions
    - **Structure**: Update inheritance, dependencies, layered architecture
    - **Operations**: Add new operations, modify existing operation specifications
    - **Norms**: Add new standards, update package structure
    - **Safeguards**: Add new constraints, update existing rules

6. **Validate cross-section consistency**

   After updates, verify:
    - Entities mentioned in Operations exist in Entities section
    - Dependencies in Structure match what's described in Operations
    - Constraints in Safeguards are enforceable based on Operations
    - Norms are applied consistently across Operations

7. **Write the updated prompt file**

   a. Overwrite the existing file with the updated content
   b. Preserve the original filename (do NOT rename)

8. **Show update summary**

   ```
   SPDD prompt updated: `spdd/prompt/<file-name>.md`

   Changes made:
   - [Section]: [Summary of changes]
   - [Section]: [Summary of changes]

   Sections unchanged:
   - [List of sections that were not modified]

   Review recommendations:
   - [Any areas that may need manual review]
   ```

9. **Record one refine-loop iteration via `/spdd-meta-refine`** (Docs-as-Code automation)

   A prompt update is, by definition, a refine-loop iteration — even if no code is regenerated yet. Delegate the metadata write to the canonical `/spdd-meta-refine` command instead of mutating frontmatter inline.

   a. **Locate the source story file** by tracing the prompt's JIRA / story id back to a file under `requirements/` (same algorithm as Step 8 of `/spdd-generate`). Skip the delegation with a warning if no story can be matched.

   b. **Invoke `/spdd-meta-refine`** for the located story file:

   ```
   /spdd-meta-refine --file @requirements/<story-file>.md --reason prompt-update
   ```

   - Always pass `--reason prompt-update` so the run report distinguishes prompt-edit iterations from generation iterations.
   - This is invoked even when no code is regenerated — a prompt edit is itself an AI-mediated refine step that the team wants reflected in `ai_refine_loops`.

   c. **Surface the diff** that `/spdd-meta-refine` returned in the post-update message verbatim, e.g.:

   ```
   📌 Story metadata updated (requirements/[User-story-7]token-billing.md):
      - quality_metrics.ai_refine_loops: 4 → 5
      - reason: prompt-update
   ```

   If `/spdd-meta-refine` aborts (e.g., file lacks frontmatter), propagate its error and instruct the user to run `/spdd-meta-init` or regenerate via `/spdd-story`. Do NOT fall back to writing frontmatter inline.

10. **Ask for confirmation**

    > "The SPDD prompt has been updated. Would you like me to regenerate the affected code using `/spdd-generate`?"

**Output**

The updated SPDD prompt file with changes integrated while preserving the REASONS Canvas structure.

**Guardrails**

- **CRITICAL**: Do NOT rewrite the entire file - only modify sections that need changes
- Do NOT proceed without both file path and update instructions
- Do NOT change sections that are unaffected by the update request
- Do NOT break cross-section consistency - if you update Entities, check Operations too
- Do NOT leave placeholders or TODO items - generate complete, specific content
- Do NOT rename the file - preserve the original filename
- Preserve the REASONS Canvas structure (all 7 sections must remain)
- Validate that updates don't contradict existing unchanged content
- Always delegate metadata mutation to `/spdd-meta-refine` per Step 9 — do NOT mutate the source story's frontmatter inline from this command
- If `/spdd-meta-refine` aborts, propagate its error verbatim and stop — do NOT retry by writing frontmatter inline

**No Code Block Rules** (CRITICAL):

The SPDD prompt file is a **specification document**, not source code. It describes WHAT to implement, leaving the HOW to the `/spdd-generate` phase.

- **Do NOT include language-specific code blocks** (e.g., ```java, ```python, ```typescript)
- **Do NOT include implementation code** - no class definitions, method bodies, SQL queries, or annotations in code form
- **Use natural language** to describe:
    - Method signatures: "Method `findById(String id)` returns `Optional<Customer>`"
    - Query logic: "Query active subscriptions where customerId matches and date falls within effective range, ordered by createdAt DESC"
    - Interface contracts: "Interface defines methods: `save(Bill)`, `findByCustomerId(String)`"
- **Allowed diagram blocks**: Mermaid diagrams for entity relationships are permitted (```mermaid)
- **Describe, don't implement**:
    - ✅ "Adapter converts between PO and domain entity using `toDomain()` and `fromDomain()` methods"
    - ❌ ```java @Repository public class JpaCustomerRepositoryAdapter { ... } ```
- **Specification vs Implementation boundary**:
    - SPDD prompt = specification (describes contracts, behaviors, constraints)
    - Generated code = implementation (actual source files created by `/spdd-generate`)

**Update-Specific Guardrails**:

- **Minimal change principle**: Only modify what's necessary to satisfy the update request
- **Preserve intent**: Do not change the original design intent unless explicitly requested
- **Backward compatibility**: Consider impact on any existing implementation
- **Traceability**: Changes should be clearly identifiable in the updated sections

**Integration with SPDD Workflow**

This command supports the iterative refinement cycle in SPDD:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    SPDD Prompt Lifecycle                                 │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  Create: /spdd-reasons-canvas                                           │
│  ┌────────────────────────────────────────────────────────────────┐    │
│  │ Business Context → REASONS Canvas → spdd/prompt/*.md            │    │
│  └────────────────────────────────────────────────────────────────┘    │
│                              │                                          │
│                              ▼                                          │
│  Update: /spdd-prompt-update  ◄────────────────────────┐               │
│  ┌────────────────────────────────────────────────────────────────┐    │
│  │ Existing Prompt + Change Request → Updated Prompt              │    │
│  │                                                                 │    │
│  │ Triggers:                                                       │    │
│  │ - New requirements from stakeholders                           │    │
│  │ - Architectural refinements                                    │    │
│  │ - Bug fixes in specification                                   │    │
│  │ - Constraint additions                                         │    │
│  └────────────────────────────────────────────────────────────────┘    │
│                              │                                          │
│                              ▼                                          │
│  Generate: /spdd-generate                                               │
│  ┌────────────────────────────────────────────────────────────────┐    │
│  │ Structured Prompt → Implementation Code                         │    │
│  └────────────────────────────────────────────────────────────────┘    │
│                              │                                          │
│                              ▼                                          │
│  Sync: /spdd-sync (if code changes first)                              │
│  ┌────────────────────────────────────────────────────────────────┐    │
│  │ Code Changes → Update Prompt → Maintain Consistency            │────┘
│  └────────────────────────────────────────────────────────────────┘    │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

**Common Update Scenarios**

1. **Adding Architectural Principles**
    - Affects: Approach, Structure, Operations, Norms, Safeguards
    - Example: "Add dependency inversion with service/repository interfaces"

2. **Adding New Entity**
    - Affects: Entities, Structure, Operations
    - Example: "Add AuditLog entity for tracking changes"

3. **Adding New Constraint**
    - Affects: Safeguards, possibly Operations
    - Example: "Add rate limiting: max 100 requests per minute"

4. **Refining Business Logic**
    - Affects: Approach, Operations
    - Example: "Change billing calculation to support tiered pricing"

5. **Updating Coding Standards**
    - Affects: Norms, Operations (to align with new standards)
    - Example: "Switch from field injection to constructor injection"
