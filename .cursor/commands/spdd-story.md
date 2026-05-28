---
name: /spdd-story
id: spdd-story
category: Development
description: Decompose features into INVEST-compliant stories with business ACs
---

Run the SPDD **story decomposition** phase.

**Read and follow completely before executing:**
1. `.cursor/rules/spdd/spdd-shared.mdc`
2. `.cursor/rules/spdd/spdd-story.mdc`

**Input:** Feature description and/or `@file` references after the command.

**Example:** `/spdd-story @requirements/feature-idea.md`

**Output:** `requirements/[User-story-N]{title}.md`

**Next step:** Offer `/spdd-analysis @requirements/<file>.md`
