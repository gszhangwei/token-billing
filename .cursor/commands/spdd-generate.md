---
name: /spdd-generate
id: spdd-generate
category: Development
description: Generate code from a REASONS Canvas SPDD prompt file
---

Run the SPDD **code generation** phase.

**Read and follow completely before executing:**
1. `.cursor/rules/spdd/spdd-shared.mdc`
2. `.cursor/rules/spdd/reasons-framework.mdc`
3. `.cursor/rules/spdd/spdd-generate.mdc`

**Input:** Path to structured prompt file after the command.

**Example:** `/spdd-generate @spdd/prompt/GGQPA-XXX-*-[Feat]-api-token-usage-billing.md`

**Output:** Implementation source files per Operations sequence

**Next step:** Offer `/spdd-api-test` against generated code or prompt
