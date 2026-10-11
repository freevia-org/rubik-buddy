---
name: iOS KMP Port Status
description: Current status of the KMP/CMP iOS port for the Rubik Solver app — what's done, what's deferred
type: project
---

All 9 tasks from `docs/superpowers/plans/2026-03-21-ios-kmp-port.md` are implemented. Task 5 Kotlin/cinterop code and Xcode project are written; they compile once macOS steps are run.

**Why:** User wants iOS support with maximum shared codebase via KMP + Compose Multiplatform.

**How to apply:** When working on this project, the KMP migration is complete. Android builds and tests pass. iOS build requires macOS to compile Kotlin/Native.

## What's done (Windows-executable)
- Tasks 1–4: KMP foundation (rubik-model, rubik-vision, rubik-solver, composeApp converted to KMP)
- Task 6: composeApp renamed, CMP plugin added
- Tasks 7–9: All UI moved to commonMain, expect/actual for CameraPreviewLayer + CameraPermission + PlatformLog, iosApp entry point files (Swift + MainViewController.kt)
- CMP resources: `composeApp/src/commonMain/composeResources/values/strings.xml` + `compose.resources { packageOfResClass = "org.freevia.rubikbuddy.generated.resources" }` in build.gradle.kts

## Key technical fixes applied
- AGP 9.1.0 + KMP: `android.builtInKotlin=false` and `android.newDsl=false` in gradle.properties
- CMP Res class package: must set `packageOfResClass` explicitly or CMP generates `rubiksolver.composeapp.generated.resources` (wrong)
- `java.util.Stack` in ColorDetector.kt → `ArrayDeque` with `addLast`/`removeLast`
- `String.format()` in HomeScreen: compute `statusText` in composable scope via `stringResource(Res.string.x, arg)` instead of template.format()
- `java.util.concurrent.AtomicReference` in Cube3DView → private `FrameRef<T>` class
- `Math.toRadians()` in Cube3DView → `kotlin.math.PI` multiplication

## Deferred to macOS (Task 5 — final steps)
- Run `rubik-solver/build-ios-framework.sh` — build min2phaseCXX as XCFramework (requires Xcode clang)
- Run `./gradlew compileKotlinIosArm64` — verify Kotlin/Native compiles with the XCFramework

## Already done on Windows (Task 5 — Kotlin/Xcode side)
- `iosMain/solver/CubeSolver.kt` — real cinterop implementation with `min2phase_c_init`/`min2phase_c_solve`
- `iosApp/iosApp.xcodeproj/project.pbxproj` — hand-crafted Xcode project, links ComposeApp.framework
- `composeApp/build.gradle.kts` — `binaries.framework { baseName = "ComposeApp"; isStatic = true }` on all iOS targets
- Xcode build script phase calls `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`

## File structure: androidMain (platform-specific)
- `AppViewModel.kt` — references CubeFrameAnalyzer (Android-only for now)
- `MainScreen.kt` — scan UI, uses CameraPreviewLayer actual
- `MainActivity.kt` + `AndroidApp()` composable — Android entry point
- `ui/CameraPreviewLayer.kt` — actual: CameraX full impl
- `CameraPermission.kt` — actual: rememberLauncherForActivityResult
- `PlatformLog.kt` — actual: android.util.Log

## File structure: iosMain (platform-specific)
- `MainViewController.kt` — ComposeUIViewController showing HomeScreen
- `ui/CameraPreviewLayer.kt` — actual: stub placeholder
- `CameraPermission.kt` — actual: AVCaptureDevice
- `PlatformLog.kt` — actual: NSLog
