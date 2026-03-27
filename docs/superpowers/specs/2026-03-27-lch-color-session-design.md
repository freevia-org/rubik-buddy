# LCH Color Session Design

**Date:** 2026-03-27
**Status:** Approved
**Replaces:** CIELAB Gaussian NLL classifier (`ColorDetector.kt` + `ColorModel.kt`)

---

## Problem

The existing CIELAB Bayesian classifier misclassifies RED vs ORANGE under varied lighting
because it uses 3D Euclidean distance in [L, a, b] space. L (lightness) is the most
lighting-sensitive axis and contaminates the distance metric. RED (a≈56, b≈36) and ORANGE
(a≈50, b≈55) are only 6 units apart on the `a` axis but 15° apart in hue angle — a far
more illuminant-invariant discriminator.

---

## Solution: (a, b)-plane scoring with session calibration

### 1. Scoring Model

**Chromatic classification** uses Euclidean distance in the (a, b) plane only — L is
excluded from the distance metric:

```
score(tile, color) = sqrt((tile_a - model_a)² + (tile_b - model_b)²)
→ classify as the color with minimum score
```

This is mathematically equivalent to classifying by hue angle and chroma (LCH), since
`a = C·cos(H)` and `b = C·sin(H)`. Dropping L eliminates the primary source of
lighting-induced misclassification.

**WHITE detection** is special-cased before distance scoring:

```
chroma C = sqrt(a² + b²)
if C < WHITE_CHROMA_THRESHOLD → WHITE
```

White is the only low-chroma color on a Rubik's cube. This is robust because chroma is
far less affected by lighting than individual a/b values.

**Confidence** = gap between top-1 and top-2 scores, normalized by mean inter-color
distance. Tiles where the gap is below `UNCERTAIN_GAP_THRESHOLD` are flagged as uncertain
in the UI.

**Adjustable constants (defined in one place):**

```kotlin
const val CENTER_TILE_WEIGHT       = 4.0f   // weight for center tile (tileIndex == 4)
const val NON_CENTER_TILE_WEIGHT   = 1.0f   // weight for all other tiles
const val WHITE_CHROMA_THRESHOLD   = 15.0f  // C below this → WHITE
const val UNCERTAIN_GAP_THRESHOLD  = 0.4f   // normalized confidence gap → flag tile
```

---

### 2. Session Calibration Model

A flat map of confirmed tile measurements keyed by `(face, tileIndex)`. Max 54 entries.
When a face is rescanned its 9 entries are **replaced** (not accumulated), preventing
stale or incorrect scans from polluting the model.

```kotlin
data class TileMeasurement(
    val face: CubeFace,
    val tileIndex: Int,         // 0–8; center = 4
    val assignedColor: CubeColor,
    val lab: FloatArray,        // [L, a, b]
    val weight: Float           // CENTER_TILE_WEIGHT or NON_CENTER_TILE_WEIGHT
)

// keyed by (face, tileIndex)
measurements: Map<Pair<CubeFace, Int>, TileMeasurement>
```

**Rebuild** (runs after every `commitFace` or `revertFace`):

```
for each CubeColor:
    collect all measurements where assignedColor == color
    model_a = weightedMean(measurements.a)
    model_b = weightedMean(measurements.b)
```

If no session measurements exist yet for a color, fall back to the fixed LCH priors
(converted from the existing camera-tuned CIELAB priors to (a, b) form).

**`commitFace(face, colors[9], labs[9])`:**
1. Write 9 `TileMeasurement` entries into the map (overwrites any existing entries for
   that face+tile)
2. tileIndex 4 (center) gets `CENTER_TILE_WEIGHT`; all others get `NON_CENTER_TILE_WEIGHT`
3. Rebuild all 6 color models
4. Append a `ScanEvent` to the history log

**`revertFace(face)`:**
Remove the 9 entries for that face, rebuild. Replaces the old checkpoint/undo stack —
reverts are per-face rather than per-snapshot.

**`reset()`:**
Clear all measurements and history. Returns to prior-only state. Called when the user
starts a new scan session.

---

### 3. Scan History & Debug Log

An append-only list of `ScanEvent` records. Never deleted, even on rescan (a rescan
appends a new event marked `isRescan = true`).

```kotlin
data class ScanEvent(
    val timestamp: Long,
    val face: CubeFace,
    val isRescan: Boolean,
    val tiles: List<TileRecord>
)

data class TileRecord(
    val tileIndex: Int,
    val isCenter: Boolean,
    val assignedColor: CubeColor,
    val lab: FloatArray,                          // [L, a, b]
    val lch: FloatArray,                          // [L, C, H_degrees] — computed for readability
    val weight: Float,
    val scoresBeforeThisFace: Map<CubeColor, Float>  // model state before this commit
)
```

`scoresBeforeThisFace` captures what the model would have scored each tile *before* the
current face was added, making it possible to see exactly how each confirmed face shifted
the model. For a rescan, "before" means the model state with that face's *previous*
measurements still in place — so you can compare old vs new scan side by side in the log.

**`debugLog(): String`** produces a human-readable dump:

```
=== COLOR SESSION DEBUG LOG ===
[Face F | 14:23:01 | first scan]
  tile 0  RED    L=41 a=55 b=35  C=64 H=32°  w=1.0  scores-before: R=8.2 O=14.1 ...
  tile 4* RED    L=40 a=57 b=36  C=67 H=32°  w=4.0  scores-before: R=7.8 O=15.3 ...
  ...

[Face B | 14:23:45 | first scan]
  tile 4* ORANGE L=57 a=49 b=54  C=73 H=48°  w=4.0  ...
  ...

[Face F | 14:24:12 | RESCAN]
  ...

=== CURRENT MODEL ===
  RED     mean_a=56.1 mean_b=35.8  (n=9, w=12.0)
  ORANGE  mean_a=49.7 mean_b=53.2  (n=9, w=12.0)
  WHITE   prior (no session data)
  ...
```

`*` marks center tiles.

---

## Architecture & File Changes

### New / replaced files (`rubik-vision/src/commonMain/`)

| Action | File | Purpose |
|--------|------|---------|
| **Replace** | `ColorDetector.kt` → `ColorSession.kt` | New session model: `commitFace`, `revertFace`, `classify`, `debugLog`, `reset` |
| **Delete** | `ColorModel.kt` | Absorbed into `ColorSession` |
| **Modify** | `LabConverter.kt` | Add `labToLch(lab): FloatArray` → [L, C, H_degrees] for logging |

### Caller changes

| File | Change |
|------|--------|
| `AppViewModel.kt` | `confirmFace()` calls `colorSession.commitFace()`; undo calls `colorSession.revertFace(face)`; debug dump calls `colorSession.debugLog()` |
| `CubeFrameAnalyzer.kt` | `classify()` call signature unchanged — targets `ColorSession` instead of `ColorDetector` |

### What stays the same

- `LabConverter.kt` sRgbToLab / labToSRgb
- `CubeFrameAnalyzer.kt` temporal median, tile sampling, center stability gate
- `ScanOrchestrator.kt`
- All UI / Compose layers

### What is removed

| Removed | Replacement |
|---------|-------------|
| Checkpoint/undo stack (6 snapshots) | Per-face `revertFace(face)` |
| `colorCycle()` / `rankFor()` / `scoreFor()` | `debugLog()` |
| `calibrateFace()` / `calibrateTile()` / `calibrateTileLab()` | `commitFace()` |
| `ColorModel.kt` (Welford Gaussian) | Weighted mean in `ColorSession` |
| `ColorDetectorTest.kt` + `ColorModelTest.kt` | `ColorSessionTest.kt` |

---

## LCH Priors (fallback before any face is confirmed)

Derived from existing camera-tuned CIELAB priors, kept as (a, b) pairs:

| Color | prior_a | prior_b | Notes |
|-------|---------|---------|-------|
| WHITE | — | — | Classified by C < 15 only |
| YELLOW | 8 | 64 | High positive b |
| RED | 56 | 36 | High a, moderate b |
| ORANGE | 50 | 55 | Moderate a and b |
| BLUE | 16 | -50 | Negative b |
| GREEN | -42 | 32 | Negative a |
