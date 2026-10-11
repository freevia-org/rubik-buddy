# Hue-Angle Scoring Design

**Date:** 2026-03-28
**Status:** Approved
**Replaces:** (a,b)-plane Euclidean scoring in `ColorSession.kt`

---

## Problem

The (a,b)-plane Euclidean scorer in `ColorSession` is sensitive to chroma variation.
Scoring by `sqrt((a - model_a)² + (b - model_b)²)` penalises tiles whose chroma differs
from the prior's chroma, even when the hue is identical.

Example: a dimly-lit orange tile (C≈50) at hue 43° renders as `(a=37, b=34)`. The
(a,b) distance to the ORANGE prior (55, 50) is ~24, but to the RED prior (54, 34) is
only ~17 — so it gets called RED despite having a perfect orange hue.

---

## Solution: hue-angle scoring

### 1. Scoring Model

**Chromatic classification** converts each tile to hue angle H = atan2(b, a) ∈ [0°, 360°)
and scores by **circular angular distance**:

```
tileH  = atan2(b, a)  [in degrees, normalised to [0, 360)]
score(tile, color) = angularDist(tileH, model_H)
  where angularDist(h1, h2) = min(|h1-h2|, 360-|h1-h2|)
→ classify as the color with minimum score
```

This is chroma-invariant: a dim tile and a bright tile at the same hue angle score
identically. Dropping both L and chroma (C) eliminates the two main sources of
lighting-induced misclassification.

**WHITE detection** is unchanged — special-cased before hue scoring:

```
chroma C = sqrt(a² + b²)
if C < WHITE_CHROMA_THRESHOLD → WHITE
```

**Confidence** = gap between top-1 and top-2 hue scores (degrees) / CONFIDENCE_SCALE.

**Adjustable constants:**

```kotlin
const val CENTER_TILE_WEIGHT      = 4.0f   // weight for center tile (tileIndex == 4)
const val NON_CENTER_TILE_WEIGHT  = 1.0f   // weight for all other tiles
const val WHITE_CHROMA_THRESHOLD  = 15.0f  // C below this → WHITE
const val UNCERTAIN_GAP_THRESHOLD = 4.0f   // degree gap below which tile is uncertain
private const val CONFIDENCE_SCALE = 15.0f // degree gap that maps to confidence 1.0
```

---

### 2. Session Calibration Model

Unchanged in structure. Only `rebuild()` changes: instead of computing weighted mean
`(a, b)`, it computes the **circular weighted mean hue**:

```
for each CubeColor:
    collect all measurements where assignedColor == color
    sumSin = Σ weight_i · sin(H_i)
    sumCos = Σ weight_i · cos(H_i)
    model_H = atan2(sumSin, sumCos)  [normalised to [0, 360)]
```

Circular mean is required to correctly average hue angles near 0°/360°.

If no session measurements exist for a color, fall back to the **hue priors** below.

---

### 3. Hue Priors

Derived from camera measurements (this camera, standard Rubik's pigments):

| Color  | prior_H | Notes                            |
|--------|---------|----------------------------------|
| WHITE  | —       | Classified by C < 15 only        |
| RED    | 32°     | atan2(34, 54)                    |
| ORANGE | 42°     | atan2(50, 55)                    |
| YELLOW | 83°     | atan2(64, 8)                     |
| GREEN  | 143°    | atan2(32, −42)                   |
| BLUE   | 288°    | atan2(−50, 16) normalised        |

RED–ORANGE separation: **10°** (was 16 (a,b) units, but chroma-variant).

---

### 4. Scan History & Debug Log

Unchanged in structure. Score values in `scoresBeforeThisFace` are now in degrees
(0–180) rather than (a,b) Euclidean units. `debugLog()` CURRENT MODEL section shows
`mean_H=32.4°` instead of `mean_a=… mean_b=…`.

---

## File Changes

| File | Change |
|------|--------|
| `rubik-vision/src/commonMain/.../ColorSession.kt` | Replace (a,b) scoring with hue-angle scoring throughout |
| `rubik-vision/src/commonTest/.../ColorSessionTest.kt` | Update `center tile` test (modelMeanFor returns Float); verify score/confidence tests still pass |

Everything else unchanged: `LabConverter`, `CubeFrameAnalyzer`, `AppViewModel`, UI.
