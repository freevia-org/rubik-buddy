# iOS builds

The iOS host shares the home, six-face scan, sticker correction and animated
solution flow with Android. AVFoundation supplies camera frames on a device.
Simulator builds offer an explicitly labeled sample cube (`R U F`) so the full
flow can be tested without a physical camera.

## Requirements

Apple Silicon Mac, full Xcode with iOS SDK, JDK 21, CMake, and Android SDK platform
36 (the Gradle build also configures Android). Set `JAVA_HOME` if JDK 21 is not
discoverable by `/usr/libexec/java_home`; use `ANDROID_HOME` or an ignored
`local.properties` with `sdk.dir` for the Android SDK. First-launch Xcode setup must
be complete. An installed simulator runtime is needed to launch the app.

```bash
./scripts/build-ios.sh            # unsigned Debug simulator app
./scripts/archive-ios.sh          # unsigned Release iPhone archive
./scripts/archive-ios.sh --help   # signing/export options
```

Simulator app:
`build/xcode-derived-data/Build/Products/Debug-iphonesimulator/iosApp.app`.
Device archive: `build/ios-device/RubikBuddy-unsigned.xcarchive.zip`.
The unsigned archive cannot be installed directly on an iPhone.
See [iPhone signing and release handoff](IOS_RELEASE.md).

The shared `iosApp` scheme is checked in. Kotlin runs on every Xcode build to
avoid stale frameworks after source changes. ComposeApp is linked statically;
it must not be copied into Embed Frameworks.

## Tests

```bash
./gradlew :rubik-model:jvmTest :rubik-vision:testAndroidHostTest :rubik-solver:testAndroidHostTest
IOS_SIMULATOR_UDID=<available-device-id> ./scripts/test-ios-native.sh
```

The native test command links the arm64 simulator tests and runs them with
`simctl spawn --standalone`, avoiding GUI test-runner requirements. List IDs with
`xcrun simctl list devices available`. Build the solver XCFramework first if absent.

The iOS GitHub workflow runs simulator compilation, native tests and an unsigned
Release device archive on pushes to main, pull requests and manual runs.
No Apple signing secrets are required. CI artifacts contain ZIPs to preserve app
bundle contents and executable permissions.

## Simulator validation

On 18 October 2026 the app was launched on “Rubik Buddy Test” (iPhone Duo, iOS 27.1).
Home → six sample scans → native solver returned `F' U' R'` → Previous, Replay and
Next → “Solved!” at 100%. Screenshots are in `build/ios-test-artifacts/` (generated,
not committed). The 19 October readiness work adds capture lifecycle handling,
solver serialization/validation, icon/privacy/notice resources, and Release
archiving. Final check results and build metadata accompany the device archive.

Real-camera accuracy and device-only interruption/permission behavior remain
physical-iPhone acceptance checks, listed in [IOS_RELEASE.md](IOS_RELEASE.md).

See [the readiness validation report](IOS_VALIDATION.md) for the final local check
results. Apple signing was unavailable; the generated iPhone archive is unsigned.
