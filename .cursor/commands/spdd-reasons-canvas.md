---
name: /spdd-reasons-canvas
id: spdd-reasons-canvas
category: Development
description: Generate REASONS Canvas structured prompts from business context
---

Run the SPDD **REASONS Canvas** phase.

**Read and follow completely before executing:**
1. `.cursor/rules/spdd/spdd-shared.mdc`
2. `.cursor/rules/spdd/reasons-framework.mdc`
3. `.cursor/rules/spdd/spdd-reasons-canvas.mdc`

**Input:** Business context and/or `@file` references (e.g. analysis output).

**Example:** `/spdd-reasons-canvas @spdd/analysis/GGQPA-XXX-*-[Analysis]-token-usage-billing.md`

**Output:** `spdd/prompt/{JIRA}-{TIMESTAMP}-[Feat]-{description}.md`

**Next step:** Offer `/spdd-generate @spdd/prompt/<file>.md` after user confirms
