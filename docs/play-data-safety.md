# Google Play data safety (draft for Play Console)

Answers reflect the shipped app as built from this repository (verify against the merged
manifest of the release build before each submission).

- Does the app collect or share any of the required user data types? **No.**
- Is all user data encrypted in transit? **Not applicable** (no data collected, no INTERNET permission).
- Way for users to request data deletion? **Not applicable** (no data collected).
- Permissions: `CAMERA` only. Frames are processed on-device in memory and never stored or transmitted.
- Third-party SDKs that collect data: none. No Firebase, analytics, ads or crash reporting in the app.
- App access: everything available without login. No ads. Not directed at children.
- Privacy policy URL: https://freevia.org/rubik-buddy/privacy
