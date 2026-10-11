---
name: feedback_deploy
description: After deploying, always also install on device with installDebug
type: feedback
---

Always run `./gradlew installDebug` after `./gradlew appDistributionUploadDebug`.

**Why:** User wants the build installed on the connected device immediately, not just uploaded to Firebase.

**How to apply:** Every deploy sequence should be:
1. `./gradlew assembleDebug`
2. `./gradlew appDistributionUploadDebug`
3. `./gradlew installDebug`
