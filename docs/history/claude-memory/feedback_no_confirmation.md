---
name: No confirmation for edits, builds, deploys
description: User does not want to be asked for confirmation before editing project files, building, or deploying
type: feedback
---

Do not ask for confirmation before editing files in the project folder, building, or deploying — including running adb install.

**Why:** User prefers autonomous operation for these routine actions within their project.

**How to apply:** Proceed directly with file edits, gradle builds, and adb install commands without pausing to confirm. Only pause for truly destructive or irreversible actions outside this scope (e.g., deleting the repo, modifying unrelated systems).
