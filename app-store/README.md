# App Store submission kit

Prepared 19 October 2026 for Rubik Buddy 1.0.0. No Apple developer account or
physical iPhone/iPad was available. This kit has not been submitted to Apple.

## Ready locally

- English listing copy in `metadata/en-US/`; app-level draft answers in `app.json`.
- App Review and TestFlight instructions in the adjacent text files.
- Native simulator screenshots for iPhone and iPad, with capture provenance in
  `screenshots/README.md`. Review these against the final signed version.
- Opaque 1024px icon, privacy manifest, license notices, camera permission text,
  expanded [runtime notices](../docs/IOS_LICENSES.md), and an offline privacy explanation with full policy/support links in the app.
- Unsigned physical-device Release archive from `./scripts/archive-ios.sh`.
- Local checks: `python3 scripts/validate-app-store.py`.

Rebuild and bundle the local handoff with:

```bash
./scripts/archive-ios.sh
./scripts/package-app-store.sh
```

The resulting `build/RubikBuddy-AppStore-Preparation.zip` includes the unsigned
archive, listing assets, validation, icon and checksums. It is not an IPA.

Run the commands above from the repository checkout; the ZIP is a handoff kit,
not a complete source checkout.

The metadata is plain text, ready to paste into App Store Connect. `app.json` is a
handoff file, not an Apple API payload. Name and bundle-ID availability cannot be
reserved or verified until the account exists.

## When the account is available

1. Enroll in the paid [Apple Developer Program](https://developer.apple.com/programs/enroll/).
   Add it to Xcode Settings → Accounts. Complete agreements and legal identity.
2. Register `org.freevia.rubikbuddy` (or an available unique ID). Create the iOS app
   in App Store Connect using SKU `rubik-buddy-ios`, English (U.S.), version 1.0.0.
   If changing the ID, update `app.json` and the Xcode project consistently.
3. Set your team and export an App Store distribution IPA locally:

   ```bash
   IOS_DEVELOPMENT_TEAM=YOURTEAMID IOS_BUILD_NUMBER=100 ./scripts/archive-ios.sh --app-store
   ```

   Choose a build number higher than every previously uploaded build. The script
   uses `app-store-connect` export, automatic signing and local `export` destination.
   It does **not** upload or release. Output is in `build/ios-device/app-store-ipa/`.
   Xcode may need to create/download distribution signing assets; no device UDID
   is required for App Store/TestFlight distribution. This path cannot be fully
   exercised without the account. A personal/free team does not provide App Store distribution.
4. In Xcode Organizer, select the new archive and use Validate App, then Distribute
   App → App Store Connect. Alternatively upload the exported IPA in Transporter.
   Wait for Apple's build processing and address all validation/privacy warnings.
5. Copy listing files and review notes, upload the screenshots, and complete the
   privacy questionnaire: no data collected, no tracking, camera processed locally.
   Use the public GitHub policy URL supplied in `metadata/en-US/privacy_url.txt`;
   it matches the in-app policy link. The existing marketing site's policy still
   uses Android-only wording and should be synchronized before switching back to it.
6. Complete App Review contact name/phone, content rights, actual age-rating
   questionnaire, pricing/territories, EU DSA trader status and applicable regional
   compliance. These require owner decisions; they are not guessed in this kit.
   The draft category is Utilities (secondary Education); draft price is Free.
7. Initially disable Mac and Apple Vision Pro availability unless those environments
   are separately tested. Do not claim accessibility support that has not been
   evaluated on devices. This app is not designated Made for Kids.
8. Upload to TestFlight. A tester with a physical iPhone/iPad can test it even when
   the publisher does not own one. Complete the real-camera checklist in
   `docs/IOS_RELEASE.md`, including iPad rotation, and record results. Provide a
   real-device demonstration video if App Review requests one.
9. Replace screenshots if the signed UI differs. Select manual release, submit for
   review only after physical-camera acceptance, and release after approval.

## Current blockers

Apple account/signing/App Store Connect record, final owner questionnaires and
contacts, Apple server-side validation, and physical camera/permission/lifecycle
acceptance. Local archive checks and simulator screenshots do not certify App
Store approval or camera accuracy. No installable distribution IPA exists yet.

## Apple references

- [Submission overview](https://developer.apple.com/app-store/submitting/)
- [Screenshot sizes](https://developer.apple.com/help/app-store-connect/reference/app-information/screenshot-specifications)
- [Privacy definitions](https://developer.apple.com/app-store/app-privacy-details/)
- [Complete review information](https://developer.apple.com/help/app-review/before-submitting-for-review/complete-review)
