---
name: /spdd-api-test
id: spdd-api-test
category: Testing
description: Generate curl-based API test script from code and acceptance criteria
---

Run the SPDD **API test script** phase.

**Read and follow completely before executing:**
1. `.cursor/rules/spdd/spdd-shared.mdc`
2. `.cursor/rules/spdd/spdd-api-test.mdc`
3. Use `spdd/template/api-test-script-skeleton.sh` as the script starting point

**Input:** API code, prompt, or requirements via `@file` references after the command.

**Example:** `/spdd-api-test @requirements/token-usage-billing-story.md @src/main/java/`

**Output:** `scripts/test-api.sh` (executable, bash + curl only)

**Next step:** Run `./scripts/test-api.sh`; fix issues via `/spdd-prompt-update` or `/spdd-sync`
