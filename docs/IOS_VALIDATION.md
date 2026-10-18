# iOS readiness validation — 19 October 2026

Local toolchain: Xcode 27.1 (27A9275), JDK 21, Kotlin 2.3.20, CMake 4.4.4.

| Check | Result |
| --- | --- |
| Physical iPhone Kotlin compile (`iosArm64`) | Passed |
| Simulator Kotlin compile (`iosSimulatorArm64`) | Passed |
| Android Debug Kotlin compile | Passed |
| Model JVM tests | 23 passed |
| Vision Android host tests | 55 passed |
| Android solver host tests | 9 passed |
| Vision iOS native tests | 50 passed |
| iOS solver tests | 3 passed |
| Unsigned Release device archive | Built and validated |

Native solver checks include a nontrivial scramble, simultaneous solves (shared
native state is serialized), invalid/unscanned cubes, and applying the returned
moves to verify a solved state. Vision checks include sample-cube reconstruction,
aspect-fill cropping, stability/reset, covered-lens rejection and capture
lifecycle policy. Existing Android tests now use the KMP host-test source set and
are actually executed rather than reported as NO-SOURCE.

The archive validator checks device platform/architecture, version, icon catalog,
camera permission text, display-timing key, privacy manifest, third-party notices,
and application dSYM. Each exported archive has a validation JSON, build/source
metadata, and SHA-256 checksum under `build/ios-device/`.

The simulator scan/solve UI was exercised through six sample faces, native result
`F' U' R'`, Previous/Replay/Next, and Solved at 100% on 18 October. This does not
substitute for a real-camera acceptance test.

## Not verified / not available

- No physical iPhone was available. Real camera accuracy, permission denial and
  Settings recovery, and hardware interruptions still need the checks in
  [IOS_RELEASE.md](IOS_RELEASE.md).
- Xcode has no configured Apple developer account or valid signing identity on
  this Mac. The prepared archive is unsigned and cannot be installed as-is.
- App Store/TestFlight upload and Apple review have not been performed.

The source includes a signed archive/export command for later use; credentials
and provisioning profiles are never checked in. See [IOS_BUILD.md](IOS_BUILD.md)
for repeatable commands and [IOS_RELEASE.md](IOS_RELEASE.md) for the iPhone handoff.
