---
name: /spdd-sync
id: spdd-sync
category: Development
description: Sync refactored code back into the SPDD prompt file
---

Run the SPDD **code-to-prompt sync** phase.

**Read and follow completely before executing:**
1. `.cursor/rules/spdd/spdd-shared.mdc`
2. `.cursor/rules/spdd/reasons-framework.mdc`
3. `.cursor/rules/spdd/spdd-sync.mdc`

**Input:** Prompt file path after the command; clarify which code changed if not obvious.

**Example:** `/spdd-sync @spdd/prompt/GGQPA-XXX-*-[Feat]-api-token-usage-billing.md`

**Output:** Updated `spdd/prompt/<same-file>.md`
