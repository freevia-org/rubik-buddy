# Hue-Angle Scoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace (a,b)-plane Euclidean distance scoring in `ColorSession` with chroma-invariant hue-angle scoring.

**Architecture:** Convert each tile's (a,b) to hue angle H = atan2(b,a), score by circular angular distance. Session calibration uses circular weighted mean of hue angles. Priors become single hue-angle values. Everything else (WHITE detection, commitFace, history, UI) is unchanged.

**Tech Stack:** Kotlin Multiplatform (`commonMain`), `kotlin.math.*` only (no `java.lang.Math`). Tests via `./gradlew :rubik-vision:testDebugUnitTest`. Working directory: `<repo-root>`.

---

## Files

| Action | File |
|--------|------|
| Modify | `rubik-vision/src/commonMain/kotlin/org/freevia/rubikbuddy/vision/ColorSession.kt` |
| Modify | `rubik-vision/src/commonTest/kotlin/org/freevia/rubikbuddy/vision/ColorSessionTest.kt` |

---

## Task 1: Replace (a,b) scoring with hue-angle scoring

**Files:**
- Modify: `rubik-vision/src/commonMain/kotlin/org/freevia/rubikbuddy/vision/ColorSession.kt`
- Modify: `rubik-vision/src/commonTest/kotlin/org/freevia/rubikbuddy/vision/ColorSessionTest.kt`

### Step 1: Update the `center tile gets CENTER_TILE_WEIGHT` test

The test currently destructures `modelMeanFor()` as a `Pair<Float,Float>`. After this task `modelMeanFor()` returns `Float?` (hue angle). Update the test now so it fails with the right error.

In `ColorSessionTest.kt`, replace the test `center tile (index 4) gets CENTER_TILE_WEIGHT`:

```kotlin
@Test fun `center tile (index 4) gets CENTER_TILE_WEIGHT`() {
    val session = ColorSession()
    val colors = Array(9) { CubeColor.RED }
    val labs = Array(9) { i ->
        if (i == 4) floatArrayOf(50f, 49f, 54f) else floatArrayOf(50f, 56f, 36f)
    }
    session.commitFace(Face.F, colors, labs)

    // Hues: center (49,54)→H≈47.8°, non-center (56,36)→H≈32.7°
    // Circular weighted mean: w=4 center, w=1 each of 8 non-centers
    // sumSin = 4·sin(47.8°)+8·sin(32.7°) ≈ 4·0.741+8·0.540 = 7.285
    // sumCos = 4·cos(47.8°)+8·cos(32.7°) ≈ 4·0.672+8·0.841 = 9.416
    // mean_H = atan2(7.285, 9.416) ≈ 37.8°
    // Unweighted mean would be (47.8+8×32.7)/9 ≈ 34.4° — verifies weight matters
    val meanH = session.modelMeanFor(CubeColor.RED)!!
    assertTrue(meanH > 35f && meanH < 40f,
        "Expected mean_H ≈ 37.8° (center weighted), got $meanH")
}
```

### Step 2: Run the test to confirm it fails

```
cd <repo-root>
./gradlew :rubik-vision:testDebugUnitTest --tests "*.ColorSessionTest.center tile*" 2>&1 | tail -10
```

Expected: FAIL — compilation error (Pair destructuring on Float?) or assertion error.

### Step 3: Rewrite `ColorSession.kt` with hue-angle scoring

Replace the entire file content. Key changes from the current file:

**a) Imports** — add `kotlin.math.atan2`, `kotlin.math.sin`, `kotlin.math.cos`, `kotlin.math.abs`; keep `kotlin.math.sqrt`:

```kotlin
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
```

**b) Constants** — replace `UNCERTAIN_GAP_THRESHOLD` and `CONFIDENCE_SCALE` values (now in degrees):

```kotlin
companion object {
    const val CENTER_TILE_WEIGHT      = 4.0f
    const val NON_CENTER_TILE_WEIGHT  = 1.0f
    /** Chroma sqrt(a²+b²) below this → WHITE, regardless of hue. */
    const val WHITE_CHROMA_THRESHOLD  = 15.0f
    /** Hue-angle gap (degrees) below which the top-2 colors are considered ambiguous. */
    const val UNCERTAIN_GAP_THRESHOLD = 4.0f
    /** Hue-angle gap (degrees) that maps to confidence 1.0. */
    private const val CONFIDENCE_SCALE = 15.0f
    private const val CENTER_TILE_INDEX = 4
}
```

**c) Priors** — replace `PRIORS_AB` with `PRIORS_H` (hue degrees, camera-tuned):

```kotlin
// Camera-tuned hue-angle priors [degrees, 0–360). WHITE classified by chroma only.
private val PRIORS_H: Map<CubeColor, Float> = mapOf(
    CubeColor.RED    to  32f,   // atan2(34, 54)
    CubeColor.ORANGE to  42f,   // atan2(50, 55)
    CubeColor.YELLOW to  83f,   // atan2(64,  8)
    CubeColor.GREEN  to 143f,   // atan2(32,-42)
    CubeColor.BLUE   to 288f    // atan2(-50,16) normalised
)
```

**d) `colorModels` field type** — replace `ColorMean` with `HueMean`:

```kotlin
private var colorModels = mutableMapOf<CubeColor, HueMean>()
```

**e) `classify()`** — replace (a,b) Euclidean with hue-angle distance:

```kotlin
fun classify(lab: FloatArray): Pair<CubeColor, Float> {
    val chroma = sqrt((lab[1] * lab[1] + lab[2] * lab[2]).toDouble()).toFloat()
    if (chroma < WHITE_CHROMA_THRESHOLD) return CubeColor.WHITE to 1.0f

    val tileH = hueDeg(lab)
    val ranked = CHROMATIC_COLORS.map { color ->
        color to angularDist(tileH, hueModel(color))
    }.sortedBy { it.second }

    val gap = ranked[1].second - ranked[0].second
    val confidence = (gap / CONFIDENCE_SCALE).coerceIn(0f, 1f)
    return ranked[0].first to confidence
}
```

**f) `modelSummaryStr()`** — show H° instead of a=/b=:

```kotlin
fun modelSummaryStr(): String = buildString {
    for (color in CubeColor.entries) {
        if (color == CubeColor.WHITE) continue
        val m = colorModels[color]
        if (m != null) append("${color.name}[H=%.0f° n=${m.sampleCount}] ".format(m.meanH))
        else append("${color.name}[prior] ")
    }
}
```

**g) `debugLog()`** — update CURRENT MODEL section only (tile history lines are unchanged):

```kotlin
appendLine("=== CURRENT MODEL ===")
for (color in CubeColor.entries) {
    if (color == CubeColor.WHITE) {
        appendLine("  ${color.name.padEnd(6)} chroma < $WHITE_CHROMA_THRESHOLD (no hue model)")
        continue
    }
    val m = colorModels[color]
    if (m == null) {
        val h = PRIORS_H[color]!!
        appendLine("  ${color.name.padEnd(6)} prior H=%.1f° (no session data)".format(h))
    } else {
        appendLine("  ${color.name.padEnd(6)} mean_H=%.1f°  (n=${m.sampleCount} w=%.1f)"
            .format(m.meanH, m.totalWeight))
    }
}
```

**h) `allRanked()`** — replace (a,b) sort with hue-angle sort:

```kotlin
private fun allRanked(lab: FloatArray): List<CubeColor> {
    val chroma = sqrt((lab[1] * lab[1] + lab[2] * lab[2]).toDouble()).toFloat()
    val tileH = hueDeg(lab)
    val chromatic = CHROMATIC_COLORS.sortedBy { color -> angularDist(tileH, hueModel(color)) }
    return if (chroma < WHITE_CHROMA_THRESHOLD) listOf(CubeColor.WHITE) + chromatic
    else chromatic + listOf(CubeColor.WHITE)
}
```

**i) `modelMeanFor()`** — return Float? (hue angle) instead of Pair<Float,Float>?:

```kotlin
/** For testing only: returns mean hue (degrees) from session model for [color], or null if using prior. */
internal fun modelMeanFor(color: CubeColor): Float? = colorModels[color]?.meanH
```

**j) Replace `abMean()` with `hueModel()`**:

```kotlin
/** Returns the session hue angle for [color], or the prior if no session data. */
private fun hueModel(color: CubeColor): Float =
    colorModels[color]?.meanH ?: (PRIORS_H[color] ?: 0f)
```

**k) `scoresFor()`** — replace (a,b) Euclidean with hue-angle distance:

```kotlin
private fun scoresFor(lab: FloatArray): Map<CubeColor, Float> {
    val chroma = sqrt((lab[1] * lab[1] + lab[2] * lab[2]).toDouble()).toFloat()
    val tileH = hueDeg(lab)
    return buildMap {
        put(CubeColor.WHITE, chroma)
        for (color in CHROMATIC_COLORS) {
            put(color, angularDist(tileH, hueModel(color)))
        }
    }
}
```

**l) `rebuild()`** — circular weighted mean hue:

```kotlin
private fun rebuild() {
    val newModels = mutableMapOf<CubeColor, HueMean>()
    for (color in CHROMATIC_COLORS) {
        val entries = measurements.values.filter { it.assignedColor == color }
        if (entries.isEmpty()) continue
        val totalWeight = entries.sumOf { it.weight.toDouble() }.toFloat()
        var sumSin = 0.0
        var sumCos = 0.0
        for (entry in entries) {
            val hRad = hueDeg(entry.lab) * (kotlin.math.PI / 180.0)
            sumSin += entry.weight * sin(hRad)
            sumCos += entry.weight * cos(hRad)
        }
        var meanH = (atan2(sumSin, sumCos) * (180.0 / kotlin.math.PI)).toFloat()
        if (meanH < 0f) meanH += 360f
        newModels[color] = HueMean(meanH, totalWeight, entries.size)
    }
    colorModels = newModels
}
```

**m) Add private helpers `hueDeg()` and `angularDist()`** (before or after `rebuild()`):

```kotlin
/** Hue angle of [lab] in degrees, normalised to [0, 360). */
private fun hueDeg(lab: FloatArray): Float {
    val h = (atan2(lab[2].toDouble(), lab[1].toDouble()) * (180.0 / kotlin.math.PI)).toFloat()
    return if (h < 0f) h + 360f else h
}

/** Shortest circular distance between two hue angles in degrees. Result in [0, 180]. */
private fun angularDist(h1: Float, h2: Float): Float {
    val d = abs(h1 - h2) % 360f
    return if (d > 180f) 360f - d else d
}
```

**n) Replace `ColorMean` data class with `HueMean`** at the bottom of the class:

```kotlin
private data class HueMean(
    val meanH: Float,
    val totalWeight: Float,
    val sampleCount: Int
)
```

The `TileMeasurement`, `TileRecord`, and `ScanEvent` package-level data classes are **unchanged**.

### Step 4: Run all ColorSession tests

```
./gradlew :rubik-vision:testDebugUnitTest --tests "*.ColorSessionTest" 2>&1 | tail -20
```

Expected: all 25 tests pass.

If any test fails, diagnose from the output. Common issues:
- `commitFace rescan replaces previous measurements`: test classifies `(50f, 50f, 55f)` → H≈47.7°. ORANGE prior H=42°, RED prior H=32°. Distance to ORANGE=5.7°, to RED=15.7°. Should still pass.
- `RED and ORANGE prior labs are not confused`: RED lab `(40,56,36)` H≈32.7°, ORANGE lab `(56,50,55)` H≈47.7°. Clear separation. Should pass.
- `confidence is high for unambiguous tile`: lab `(40,56,36)` H≈32.7°, nearest RED H=32° (dist≈0.7°), second ORANGE H=42° (dist≈9.3°). Gap=8.6°, confidence=8.6/15=0.57. Test checks `conf > 0.3` — passes.
- `debugLog shows prior for unscanned colors`: checks `log.contains("prior")` — output now says `"prior H=288.0°"` for BLUE — still contains "prior". Passes.
- `modelSummaryStr contains color names`: checks `summary.contains("RED")` — output contains `"RED[H=32° n=9]"`. Passes.

### Step 5: Run full rubik-vision suite

```
./gradlew :rubik-vision:testDebugUnitTest 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

### Step 6: Commit

```bash
git add rubik-vision/src/commonMain/kotlin/org/freevia/rubikbuddy/vision/ColorSession.kt \
        rubik-vision/src/commonTest/kotlin/org/freevia/rubikbuddy/vision/ColorSessionTest.kt \
        docs/superpowers/specs/2026-03-27-lch-color-session-design.md \
        docs/superpowers/specs/2026-03-28-hue-angle-scoring-design.md
git commit -m "feat: replace (a,b) Euclidean scoring with chroma-invariant hue-angle scoring"
```

---

## Self-review

**Spec coverage:**
- ✅ Hue-angle score = angularDist(tileH, model_H) — Step 3e
- ✅ WHITE by chroma — unchanged, Step 3e
- ✅ Circular weighted mean in rebuild — Step 3l
- ✅ Priors as hue angles — Step 3c
- ✅ Constants updated to degree units — Step 3b
- ✅ debugLog shows H° — Step 3g
- ✅ modelSummaryStr shows H° — Step 3f
- ✅ center weight test updated for Float return — Step 1

**Placeholder scan:** None found.

**Type consistency:**
- `HueMean.meanH: Float` used consistently in `hueModel()`, `modelMeanFor()`, `rebuild()`, `modelSummaryStr()`, `debugLog()`.
- `colorModels: MutableMap<CubeColor, HueMean>` — consistent throughout.
- `PRIORS_H: Map<CubeColor, Float>` — consistent with `hueModel()` fallback.
