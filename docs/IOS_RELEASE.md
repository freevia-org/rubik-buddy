# iOS test and release handoff

The iOS port is prepared for device testing. Physical-camera verification is still
pending: no iPhone was available during preparation. This is not an App Store submission.

## Build and install later

Open `iosApp/iosApp.xcodeproj` in Xcode. In Settings → Accounts, add your Apple
account; then select your development team under the `iosApp` target's Signing &
Capabilities. Keep automatic signing enabled. Connect and trust the iPhone,
enable Developer Mode if requested, select it as the run destination, and run.
Use `org.freevia.rubikbuddy` if available to your team; otherwise choose a unique
bundle identifier. Build the native solver first with
`./rubik-solver/build-ios-framework.sh` (JDK 21 and CMake must be on PATH).

For a signed Release archive and exported development IPA:

```bash
IOS_DEVELOPMENT_TEAM=YOURTEAMID ./scripts/archive-ios.sh --signed
```

Optional overrides: `IOS_BUNDLE_ID` and `IOS_BUILD_NUMBER`. The development
certificate and provisioning profile stay in your Keychain and Xcode account.
An IPA can only run on devices covered by its provisioning profile. For a
personal/free team, running directly from Xcode is the supported starting point;
export availability depends on your account. See Apple's
[registered-device distribution guide](https://developer.apple.com/documentation/xcode/distributing-your-app-to-registered-devices).

Without signing credentials, `./scripts/archive-ios.sh` produces
`build/ios-device/RubikBuddy-unsigned.xcarchive.zip`, its SHA-256 checksum and
build metadata. The ZIP is a device archive, **not an installable IPA**. Run the
signed command above once your team and iPhone provisioning are available.

## Device acceptance checks

- First scan: allow camera access; confirm preview and sticker guide align.
- Deny camera access: verify explanation and Back; grant access in Settings,
  return, and verify camera recovery. Repeat with access revoked in Settings.
- Scan a real, known scramble under daylight and indoor lighting; correct one
  misread tile, rescan a face, and confirm the reconstructed cube is valid.
- Follow every solution move on the real cube; test Previous, Replay, Restart.
- Background/foreground, lock/unlock, rotate an iPad, and interrupt camera access;
  verify fresh alignment is required and Retry recovers without stale colors.
- Verify the app icon, display name, launch layout, and accessibility labels.

Automated lifecycle policy tests cover foreground/configuration/interruption and
permanent close. Simulator sample frames cover the classifier/orientation/solver
flow. They cannot establish camera color accuracy on real hardware.

## Prepared metadata

- Display name: Rubik Buddy
- Bundle identifier: org.freevia.rubikbuddy
- Version: 1.0.0; script build number defaults to Git commit count
- Minimum iOS: 15.0; arm64 device and arm64 simulator
- App icon: opaque 1024×1024 asset catalog, generated from the existing branding
- Privacy policy: https://github.com/freevia-org/rubik-buddy/blob/main/docs/privacy-policy.md
- Support: https://github.com/freevia-org/rubik-buddy/blob/main/.github/SUPPORT.md
- Privacy: no tracking; no collected data; all camera processing stays on device
- Encryption declaration: no non-exempt encryption
- Third-party notices and privacy manifest included in the app bundle

The manifest declares app-local resource file metadata (C617.1) and elapsed-time
measurement (35F9.1) used by the shared runtime, alongside empty collection and
tracking declarations. See Apple's [required-reason API documentation](https://developer.apple.com/documentation/bundleresources/describing-use-of-required-reason-api).
Recheck the declarations whenever adding dependencies or data flows.

## App Store preparation

The complete submission kit is in [`app-store/README.md`](../app-store/README.md),
with copy-ready metadata, questionnaire drafts, review notes and native simulator
screenshots. Validate it with `python3 scripts/validate-app-store.py`.

Once a paid developer team is available, export locally for App Store Connect:

```bash
IOS_DEVELOPMENT_TEAM=YOURTEAMID IOS_BUILD_NUMBER=100 ./scripts/archive-ios.sh --app-store
```

Choose a build number higher than previously uploaded builds. This exports to
`build/ios-device/app-store-ipa/` without uploading. Xcode Organizer or Transporter
performs the eventual validation/upload. No phone registration is needed for
App Store/TestFlight signing, but camera acceptance still requires real hardware.

## Store copy and final review

Copy-ready English metadata is in [`app-store/metadata/en-US/`](../app-store/metadata/en-US/).
Review notes and TestFlight test instructions are adjacent. The App Store kit is
also packaged as an artifact by the iOS GitHub workflow after a successful archive.

Before publishing: complete real-device checks, review the final screenshots,
configure App Store Connect/TestFlight, finish owner questionnaires and run Apple
validation. No listing or build has been uploaded from this preparation task.
