---
name: /spdd-analysis
id: spdd-analysis
category: Development
description: Analyze requirements against codebase for strategic enriched context
---

Run the SPDD **analysis** phase.

**Read and follow completely before executing:**
1. `.cursor/rules/spdd/spdd-shared.mdc`
2. `.cursor/rules/spdd/spdd-analysis.mdc`

**Input:** Requirement text and/or `@file` references after the command.

**Example:** `/spdd-analysis @requirements/token-usage-billing-story.md`

**Output:** `spdd/analysis/{JIRA}-{TIMESTAMP}-[Analysis]-{description}.md`

**Next step:** Offer `/spdd-reasons-canvas @spdd/analysis/<file>.md`
