---
name: /spdd-prompt-update
id: spdd-prompt-update
category: Development
description: Update an existing SPDD prompt with new requirements or design changes
---

Run the SPDD **prompt update** phase.

**Read and follow completely before executing:**
1. `.cursor/rules/spdd/spdd-shared.mdc`
2. `.cursor/rules/spdd/reasons-framework.mdc`
3. `.cursor/rules/spdd/spdd-prompt-update.mdc`

**Input:** Prompt file `@reference` plus change instructions in the same message.

**Example:** `/spdd-prompt-update @spdd/prompt/GGQPA-XXX-*-[Feat]-api-token-usage-billing.md Add constructor injection and three-layer architecture`

**Output:** Updated `spdd/prompt/<same-file>.md`

**Next step:** Offer `/spdd-generate` for affected components
