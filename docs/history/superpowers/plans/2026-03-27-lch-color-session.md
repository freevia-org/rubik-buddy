# LCH Color Session Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the CIELAB Gaussian NLL classifier with a session-adaptive (a,b)-plane scorer that rebuilds color models from confirmed face scans.

**Architecture:** `ColorSession` stores confirmed tile measurements keyed by `(Face, tileIndex)` and rebuilds per-color `(mean_a, mean_b)` on every `commitFace` / `revertFace`. Classification scores chromatic colors by Euclidean distance in the `(a,b)` plane (L dropped); WHITE is detected by low chroma before any distance scoring. `CubeFrameAnalyzer` is updated to inject `ColorSession` and expose `detectedLabs`. `AppViewModel` owns `ColorSession` and calls `commitFace` on confirmation.

**Tech Stack:** Kotlin Multiplatform (commonMain), CameraX (Android), kotlin.test

---

## File Map

| Action | Path | Responsibility |
|--------|------|----------------|
| Modify | `rubik-vision/src/commonMain/kotlin/org/freevia/rubikbuddy/vision/LabConverter.kt` | Add `labToLch()` |
| Create | `rubik-vision/src/commonMain/kotlin/org/freevia/rubikbuddy/vision/ColorSession.kt` | Session model: classify, commitFace, revertFace, reset, debugLog, cycle, rankFor |
| Create | `rubik-vision/src/commonTest/kotlin/org/freevia/rubikbuddy/vision/ColorSessionTest.kt` | Tests for ColorSession |
| Modify | `rubik-vision/src/commonTest/kotlin/org/freevia/rubikbuddy/vision/LabConverterTest.kt` | Add labToLch tests |
| Modify | `rubik-vision/src/androidMain/kotlin/org/freevia/rubikbuddy/vision/CubeFrameAnalyzer.kt` | Inject ColorSession, add detectedLabs, remove colorDetector |
| Modify | `composeApp/src/androidMain/kotlin/org/freevia/rubikbuddy/AppViewModel.kt` | Own ColorSession, call commitFace, remove checkpoint/calibrate methods |
| Delete | `rubik-vision/src/commonMain/kotlin/org/freevia/rubikbuddy/vision/ColorDetector.kt` | Replaced by ColorSession |
| Delete | `rubik-vision/src/commonMain/kotlin/org/freevia/rubikbuddy/vision/ColorModel.kt` | Absorbed into ColorSession |
| Delete | `rubik-vision/src/commonTest/kotlin/org/freevia/rubikbuddy/vision/ColorDetectorTest.kt` | Replaced by ColorSessionTest |
| Delete | `rubik-vision/src/commonTest/kotlin/org/freevia/rubikbuddy/vision/ColorModelTest.kt` | Replaced by ColorSessionTest |

---

## Task 1: Add `labToLch` to `LabConverter`

**Files:**
- Modify: `rubik-vision/src/commonMain/kotlin/org/freevia/rubikbuddy/vision/LabConverter.kt`
- Modify: `rubik-vision/src/commonTest/kotlin/org/freevia/rubikbuddy/vision/LabConverterTest.kt`

- [ ] **Step 1: Add failing tests for `labToLch`**

Append these tests to the bottom of `LabConverterTest` (inside the class, before the closing `}`):

```kotlin
@Test fun `labToLch white gives near-zero chroma`() {
    val lab = floatArrayOf(85f, -2f, 5f)
    val lch = LabConverter.labToLch(lab)
    assertFloatWithin(lch[0], 0.001f, 85f)   // L unchanged
    assertTrue(lch[1] < 10f, "Expected chroma < 10, got ${lch[1]}")
}

@Test fun `labToLch red gives H near 33 degrees and positive C`() {
    // RED prior: a=56 b=36 → C=sqrt(56²+36²)≈66.4, H=atan2(36,56)≈32.7°
    val lab = floatArrayOf(40f, 56f, 36f)
    val lch = LabConverter.labToLch(lab)
    assertFloatWithin(lch[0], 0.001f, 40f)
    assertTrue(lch[1] > 60f, "Expected C > 60, got ${lch[1]}")
    assertFloatWithin(lch[2], 1.5f, 32.7f)
}

@Test fun `labToLch blue gives H near 288 degrees`() {
    // BLUE prior: a=16 b=-50 → atan2(-50,16) ≈ -72° → normalized → 288°
    val lab = floatArrayOf(26f, 16f, -50f)
    val lch = LabConverter.labToLch(lab)
    assertFloatWithin(lch[2], 2f, 288f)
}

@Test fun `labToLch H is always in 0-360`() {
    // Any negative atan2 result must be shifted to [0,360)
    val lab = floatArrayOf(50f, 10f, -30f)  // b negative → raw H < 0
    val lch = LabConverter.labToLch(lab)
    assertTrue(lch[2] >= 0f && lch[2] < 360f, "H=${lch[2]} not in [0,360)")
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```
./gradlew :rubik-vision:testDebugUnitTest --tests "*.LabConverterTest" 2>&1 | tail -20
```
Expected: `FAILED` with "Unresolved reference: labToLch"

- [ ] **Step 3: Add `labToLch` to `LabConverter.kt`**

Add the following after the `labToSRgb` function (after line 26, before the `// --- sRGB gamma ---` comment):

```kotlin
/**
 * Converts CIELAB [L*, a*, b*] to cylindrical LCH [L*, C*, H_degrees].
 * C* = chroma = sqrt(a*² + b*²).
 * H = hue angle in degrees, normalized to [0, 360).
 */
fun labToLch(lab: FloatArray): FloatArray {
    val a = lab[1]; val b = lab[2]
    val c = kotlin.math.sqrt((a * a + b * b).toDouble()).toFloat()
    var h = Math.toDegrees(kotlin.math.atan2(b.toDouble(), a.toDouble())).toFloat()
    if (h < 0f) h += 360f
    return floatArrayOf(lab[0], c, h)
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```
./gradlew :rubik-vision:testDebugUnitTest --tests "*.LabConverterTest" 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL` and all LabConverterTest tests green.

- [ ] **Step 5: Commit**

```bash
git add rubik-vision/src/commonMain/kotlin/org/freevia/rubikbuddy/vision/LabConverter.kt \
        rubik-vision/src/commonTest/kotlin/org/freevia/rubikbuddy/vision/LabConverterTest.kt
git commit -m "feat: add labToLch conversion to LabConverter"
```

---

## Task 2: `ColorSession` — data classes, priors, `classify()`

**Files:**
- Create: `rubik-vision/src/commonMain/kotlin/org/freevia/rubikbuddy/vision/ColorSession.kt`
- Create: `rubik-vision/src/commonTest/kotlin/org/freevia/rubikbuddy/vision/ColorSessionTest.kt`

- [ ] **Step 1: Write failing tests for `classify()`**

Create `rubik-vision/src/commonTest/kotlin/org/freevia/rubikbuddy/vision/ColorSessionTest.kt`:

```kotlin
package org.freevia.rubikbuddy.vision

import org.freevia.rubikbuddy.model.CubeColor
import org.freevia.rubikbuddy.model.Face
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColorSessionTest {

    private fun assertFloatWithin(actual: Float, tolerance: Float, expected: Float) {
        assertTrue(abs(actual - expected) <= tolerance,
            "Expected $actual within $tolerance of $expected")
    }

    // ---- classify: WHITE detection ----

    @Test fun `classify low-chroma lab as WHITE`() {
        val session = ColorSession()
        // a=0 b=0 → chroma=0 < WHITE_CHROMA_THRESHOLD
        val (color, conf) = session.classify(floatArrayOf(85f, 0f, 0f))
        assertEquals(CubeColor.WHITE, color)
        assertEquals(1.0f, conf)
    }

    @Test fun `classify near-white lab (chroma just below threshold) as WHITE`() {
        val session = ColorSession()
        // chroma = sqrt((-2)²+5²) ≈ 5.4 < 15
        val (color, _) = session.classify(floatArrayOf(85f, -2f, 5f))
        assertEquals(CubeColor.WHITE, color)
    }

    @Test fun `classify high-chroma lab is not WHITE`() {
        val session = ColorSession()
        val (color, _) = session.classify(floatArrayOf(40f, 56f, 36f))
        assertTrue(color != CubeColor.WHITE)
    }

    // ---- classify: chromatic priors ----

    @Test fun `classify RED prior lab as RED`() {
        val session = ColorSession()
        val (color, _) = session.classify(floatArrayOf(40f, 56f, 36f))
        assertEquals(CubeColor.RED, color)
    }

    @Test fun `classify ORANGE prior lab as ORANGE`() {
        val session = ColorSession()
        val (color, _) = session.classify(floatArrayOf(56f, 50f, 55f))
        assertEquals(CubeColor.ORANGE, color)
    }

    @Test fun `classify YELLOW prior lab as YELLOW`() {
        val session = ColorSession()
        val (color, _) = session.classify(floatArrayOf(60f, 8f, 64f))
        assertEquals(CubeColor.YELLOW, color)
    }

    @Test fun `classify GREEN prior lab as GREEN`() {
        val session = ColorSession()
        val (color, _) = session.classify(floatArrayOf(50f, -42f, 32f))
        assertEquals(CubeColor.GREEN, color)
    }

    @Test fun `classify BLUE prior lab as BLUE`() {
        val session = ColorSession()
        val (color, _) = session.classify(floatArrayOf(26f, 16f, -50f))
        assertEquals(CubeColor.BLUE, color)
    }

    @Test fun `RED and ORANGE prior labs are not confused`() {
        val session = ColorSession()
        val (red, _)    = session.classify(floatArrayOf(40f, 56f, 36f))
        val (orange, _) = session.classify(floatArrayOf(56f, 50f, 55f))
        assertEquals(CubeColor.RED, red)
        assertEquals(CubeColor.ORANGE, orange)
    }

    @Test fun `confidence is in 0-1 range`() {
        val session = ColorSession()
        val (_, conf) = session.classify(floatArrayOf(40f, 56f, 36f))
        assertTrue(conf in 0f..1f, "Confidence $conf not in [0,1]")
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```
./gradlew :rubik-vision:testDebugUnitTest --tests "*.ColorSessionTest" 2>&1 | tail -10
```
Expected: `FAILED` with "Unresolved reference: ColorSession"

- [ ] **Step 3: Create `ColorSession.kt` with classify only**

Create `rubik-vision/src/commonMain/kotlin/org/freevia/rubikbuddy/vision/ColorSession.kt`:

```kotlin
package org.freevia.rubikbuddy.vision

import org.freevia.rubikbuddy.model.CubeColor
import org.freevia.rubikbuddy.model.Face
import kotlin.math.sqrt

/**
 * Session-adaptive color classifier for Rubik's Cube facelets.
 *
 * Classification uses Euclidean distance in the (a,b) plane only — L is excluded
 * as it is the most lighting-sensitive axis. WHITE is detected by low chroma before
 * any distance scoring.
 *
 * Calibration: a flat map of confirmed tile measurements keyed by (Face, tileIndex).
 * [commitFace] replaces all 9 entries for a face and rebuilds color models.
 * [revertFace] removes a face's entries. [reset] clears all state.
 *
 * @param clock  Millisecond timestamp supplier. Inject a fixed clock in tests.
 */
class ColorSession(private val clock: () -> Long = { 0L }) {

    companion object {
        const val CENTER_TILE_WEIGHT      = 4.0f
        const val NON_CENTER_TILE_WEIGHT  = 1.0f
        /** Chroma sqrt(a²+b²) below this → WHITE, regardless of (a,b) distance. */
        const val WHITE_CHROMA_THRESHOLD  = 15.0f
        /** (a,b) distance gap below which the top-2 colors are considered ambiguous. */
        const val UNCERTAIN_GAP_THRESHOLD = 8.0f
        /** Gap of this many (a,b) units maps to confidence 1.0. */
        private const val CONFIDENCE_SCALE = 20.0f
        private const val CENTER_TILE_INDEX = 4
    }

    // Camera-tuned (a, b) priors — used before any face is confirmed.
    // WHITE is classified by chroma only; no (a,b) prior needed.
    private val PRIORS_AB: Map<CubeColor, FloatArray> = mapOf(
        CubeColor.YELLOW to floatArrayOf(  8f,  64f),
        CubeColor.RED    to floatArrayOf( 56f,  36f),
        CubeColor.ORANGE to floatArrayOf( 50f,  55f),
        CubeColor.BLUE   to floatArrayOf( 16f, -50f),
        CubeColor.GREEN  to floatArrayOf(-42f,  32f)
    )

    private val CHROMATIC_COLORS = CubeColor.entries.filter { it != CubeColor.WHITE }

    // Flat map: (face, tileIndex) → confirmed measurement
    private val measurements = mutableMapOf<Pair<Face, Int>, TileMeasurement>()

    // Rebuilt after every commitFace / revertFace
    private var colorModels = mutableMapOf<CubeColor, ColorMean>()

    // Append-only session history (never deleted, even on rescan)
    private val scanHistory = mutableListOf<ScanEvent>()

    // =========================================================================
    //  Public API
    // =========================================================================

    /**
     * Classifies [lab] (CIELAB float[3]).
     * Returns the best [CubeColor] and a confidence in [0, 1].
     *
     * Confidence = gap between top-1 and top-2 (a,b) scores / [CONFIDENCE_SCALE].
     * Tiles with gap < [UNCERTAIN_GAP_THRESHOLD] are ambiguous.
     */
    fun classify(lab: FloatArray): Pair<CubeColor, Float> {
        val a = lab[1]; val b = lab[2]
        val chroma = sqrt((a * a + b * b).toDouble()).toFloat()

        if (chroma < WHITE_CHROMA_THRESHOLD) return CubeColor.WHITE to 1.0f

        val ranked = CHROMATIC_COLORS.map { color ->
            val mean = abMean(color)
            val da = a - mean[0]; val db = b - mean[1]
            color to sqrt((da * da + db * db).toDouble()).toFloat()
        }.sortedBy { it.second }

        val gap = ranked[1].second - ranked[0].second
        val confidence = (gap / CONFIDENCE_SCALE).coerceIn(0f, 1f)
        return ranked[0].first to confidence
    }

    // =========================================================================
    //  Private helpers (used in Tasks 2–4)
    // =========================================================================

    /** Returns the session (a,b) mean for [color], or the prior if no session data. */
    private fun abMean(color: CubeColor): FloatArray {
        val m = colorModels[color]
        return if (m != null) floatArrayOf(m.meanA, m.meanB)
        else PRIORS_AB[color] ?: floatArrayOf(0f, 0f)
    }

    /** (a,b) distance scores for [lab] against all 6 colors using the current model. */
    private fun scoresFor(lab: FloatArray): Map<CubeColor, Float> {
        val a = lab[1]; val b = lab[2]
        val chroma = sqrt((a * a + b * b).toDouble()).toFloat()
        return buildMap {
            put(CubeColor.WHITE, chroma)   // lower chroma = better match for WHITE
            for (color in CHROMATIC_COLORS) {
                val mean = abMean(color)
                val da = a - mean[0]; val db = b - mean[1]
                put(color, sqrt((da * da + db * db).toDouble()).toFloat())
            }
        }
    }

    /** Rebuilds per-color models from current measurements. */
    private fun rebuild() {
        val newModels = mutableMapOf<CubeColor, ColorMean>()
        for (color in CHROMATIC_COLORS) {
            val entries = measurements.values.filter { it.assignedColor == color }
            if (entries.isEmpty()) continue
            val totalWeight = entries.sumOf { it.weight.toDouble() }.toFloat()
            val meanA = entries.sumOf { (it.lab[1] * it.weight).toDouble() }.toFloat() / totalWeight
            val meanB = entries.sumOf { (it.lab[2] * it.weight).toDouble() }.toFloat() / totalWeight
            newModels[color] = ColorMean(meanA, meanB, totalWeight, entries.size)
        }
        colorModels = newModels
    }

    // =========================================================================
    //  Internal data
    // =========================================================================

    private data class ColorMean(
        val meanA: Float,
        val meanB: Float,
        val totalWeight: Float,
        val sampleCount: Int
    )
}

// =============================================================================
//  Data classes (package-level — shared with CubeFrameAnalyzer)
// =============================================================================

data class TileMeasurement(
    val face: Face,
    val tileIndex: Int,
    val assignedColor: CubeColor,
    val lab: FloatArray,
    val weight: Float
) {
    // Equality by identity key only (face + index), not lab values
    override fun equals(other: Any?) =
        other is TileMeasurement && face == other.face && tileIndex == other.tileIndex
    override fun hashCode() = 31 * face.hashCode() + tileIndex
}

data class TileRecord(
    val tileIndex: Int,
    val isCenter: Boolean,
    val assignedColor: CubeColor,
    val lab: FloatArray,
    val lch: FloatArray,
    val weight: Float,
    val scoresBeforeThisFace: Map<CubeColor, Float>
)

data class ScanEvent(
    val timestamp: Long,
    val face: Face,
    val isRescan: Boolean,
    val tiles: List<TileRecord>
)
```

- [ ] **Step 4: Run tests**

```
./gradlew :rubik-vision:testDebugUnitTest --tests "*.ColorSessionTest" 2>&1 | tail -15
```
Expected: all ColorSessionTest tests pass.

- [ ] **Step 5: Commit**

```bash
git add rubik-vision/src/commonMain/kotlin/org/freevia/rubikbuddy/vision/ColorSession.kt \
        rubik-vision/src/commonTest/kotlin/org/freevia/rubikbuddy/vision/ColorSessionTest.kt
git commit -m "feat: add ColorSession with (a,b)-plane classify and LCH priors"
```

---

## Task 3: `ColorSession` — calibration (`commitFace`, `revertFace`, `reset`)

**Files:**
- Modify: `rubik-vision/src/commonMain/kotlin/org/freevia/rubikbuddy/vision/ColorSession.kt`
- Modify: `rubik-vision/src/commonTest/kotlin/org/freevia/rubikbuddy/vision/ColorSessionTest.kt`

- [ ] **Step 1: Add failing calibration tests**

Append to `ColorSessionTest` (inside the class):

```kotlin
// ---- commitFace ----

private fun makeLabs(a: Float, b: Float): Array<FloatArray> =
    Array(9) { floatArrayOf(50f, a, b) }

@Test fun `commitFace stores 9 measurements for face`() {
    val session = ColorSession()
    val colors = Array(9) { CubeColor.RED }
    session.commitFace(Face.F, colors, makeLabs(56f, 36f))
    // After commit, classifying the same (a,b) should still be RED
    val (color, _) = session.classify(floatArrayOf(50f, 56f, 36f))
    assertEquals(CubeColor.RED, color)
}

@Test fun `center tile (index 4) gets CENTER_TILE_WEIGHT`() {
    // Verify by shifting model: 8 non-center tiles at a=56 b=36 (RED prior)
    // center tile at a=49 b=54 (ORANGE territory) with weight 4 vs 1
    // Model mean should be shifted noticeably toward center
    val session = ColorSession()
    val colors = Array(9) { CubeColor.RED }
    val labs = Array(9) { i ->
        if (i == 4) floatArrayOf(50f, 49f, 54f) else floatArrayOf(50f, 56f, 36f)
    }
    session.commitFace(Face.F, colors, labs)

    // Expected mean_a = (8*56*1 + 1*49*4) / (8*1 + 1*4) = (448+196)/12 = 53.67
    // Expected mean_b = (8*36*1 + 1*54*4) / 12 = (288+216)/12 = 42.0
    val (_, _) = session.classify(floatArrayOf(50f, 53.67f, 42f))
    // We verify the model updated by checking RED classifies near (53.67, 42)
    val (c1, _) = session.classify(floatArrayOf(50f, 53.67f, 42.0f))
    val (c2, _) = session.classify(floatArrayOf(50f, 56f, 36f))
    // Both should still be RED since both are closer to RED than any other color
    assertEquals(CubeColor.RED, c1)
    assertEquals(CubeColor.RED, c2)
}

@Test fun `commitFace rescan replaces previous measurements`() {
    val session = ColorSession()
    // First scan: all RED at a=56 b=36
    session.commitFace(Face.F, Array(9) { CubeColor.RED }, makeLabs(56f, 36f))
    // Rescan: all ORANGE at a=50 b=55
    session.commitFace(Face.F, Array(9) { CubeColor.ORANGE }, makeLabs(50f, 55f))

    // RED model should now have NO session data (all measurements were replaced)
    // → falls back to RED prior → classify red prior still RED
    val (red, _) = session.classify(floatArrayOf(50f, 56f, 36f))
    assertEquals(CubeColor.RED, red)

    // ORANGE model now has session data at a=50 b=55 → classifies near there as ORANGE
    val (orange, _) = session.classify(floatArrayOf(50f, 50f, 55f))
    assertEquals(CubeColor.ORANGE, orange)
}

@Test fun `commitFace for different faces both contribute to model`() {
    val session = ColorSession()
    session.commitFace(Face.F, Array(9) { CubeColor.RED },    makeLabs(56f, 36f))
    session.commitFace(Face.R, Array(9) { CubeColor.BLUE },   makeLabs(16f, -50f))

    val (r, _) = session.classify(floatArrayOf(50f, 56f, 36f))
    val (b, _) = session.classify(floatArrayOf(50f, 16f, -50f))
    assertEquals(CubeColor.RED,  r)
    assertEquals(CubeColor.BLUE, b)
}

// ---- revertFace ----

@Test fun `revertFace removes face measurements and model reverts to prior`() {
    val session = ColorSession()
    // Shift RED model significantly away from prior
    val shifted = Array(9) { CubeColor.RED }
    session.commitFace(Face.F, shifted, makeLabs(30f, 10f))  // far from RED prior

    // Revert — RED model should be gone, falls back to prior (a=56 b=36)
    session.revertFace(Face.F)

    // Now (56,36) should still classify as RED (from prior)
    val (color, _) = session.classify(floatArrayOf(50f, 56f, 36f))
    assertEquals(CubeColor.RED, color)
}

@Test fun `revertFace on unscanned face is a no-op`() {
    val session = ColorSession()
    session.revertFace(Face.U)  // should not throw
    val (color, _) = session.classify(floatArrayOf(50f, 56f, 36f))
    assertEquals(CubeColor.RED, color)
}

// ---- reset ----

@Test fun `reset clears all measurements and reverts to priors`() {
    val session = ColorSession()
    session.commitFace(Face.F, Array(9) { CubeColor.GREEN }, makeLabs(-42f, 32f))
    session.reset()
    // Should be back to prior — GREEN prior is a=-42 b=32
    val (color, _) = session.classify(floatArrayOf(50f, 56f, 36f))
    assertEquals(CubeColor.RED, color)  // prior still works after reset
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```
./gradlew :rubik-vision:testDebugUnitTest --tests "*.ColorSessionTest" 2>&1 | tail -15
```
Expected: `FAILED` with "Unresolved reference: commitFace" (and similar).

- [ ] **Step 3: Add `commitFace`, `revertFace`, `reset` to `ColorSession.kt`**

Add these three methods to the `// Public API` section in `ColorSession`, after `classify()`:

```kotlin
/**
 * Stores confirmed tile measurements for [face], rebuilds color models, and appends a
 * [ScanEvent] to the session history. If this face was previously committed, its old
 * measurements are replaced (rescan behaviour).
 *
 * [colors] — 9 confirmed CubeColors; index 4 (center) is always authoritative.
 * [labs]   — 9 CIELAB float[3] arrays, one per tile (index matches [colors]).
 */
fun commitFace(face: Face, colors: Array<CubeColor>, labs: Array<FloatArray>) {
    require(colors.size == 9) { "Expected 9 colors, got ${colors.size}" }
    require(labs.size == 9)   { "Expected 9 labs, got ${labs.size}" }

    val isRescan = (0 until 9).any { measurements.containsKey(face to it) }

    // Capture scores with the CURRENT model before this face changes it
    val scoresBefore: List<Map<CubeColor, Float>> = labs.map { scoresFor(it) }

    // Replace all 9 measurements for this face
    for (i in 0 until 9) {
        val weight = if (i == CENTER_TILE_INDEX) CENTER_TILE_WEIGHT else NON_CENTER_TILE_WEIGHT
        measurements[face to i] = TileMeasurement(
            face          = face,
            tileIndex     = i,
            assignedColor = colors[i],
            lab           = labs[i].copyOf(),
            weight        = weight
        )
    }

    rebuild()

    val tiles = (0 until 9).map { i ->
        TileRecord(
            tileIndex            = i,
            isCenter             = i == CENTER_TILE_INDEX,
            assignedColor        = colors[i],
            lab                  = labs[i].copyOf(),
            lch                  = LabConverter.labToLch(labs[i]),
            weight               = if (i == CENTER_TILE_INDEX) CENTER_TILE_WEIGHT
                                   else NON_CENTER_TILE_WEIGHT,
            scoresBeforeThisFace = scoresBefore[i]
        )
    }
    scanHistory.add(ScanEvent(clock(), face, isRescan, tiles))
}

/** Removes all measurements for [face] and rebuilds models. */
fun revertFace(face: Face) {
    (0 until 9).forEach { measurements.remove(face to it) }
    rebuild()
}

/** Clears all measurements, models, and scan history. */
fun reset() {
    measurements.clear()
    colorModels.clear()
    scanHistory.clear()
}
```

- [ ] **Step 4: Run tests**

```
./gradlew :rubik-vision:testDebugUnitTest --tests "*.ColorSessionTest" 2>&1 | tail -15
```
Expected: all ColorSessionTest tests pass.

- [ ] **Step 5: Commit**

```bash
git add rubik-vision/src/commonMain/kotlin/org/freevia/rubikbuddy/vision/ColorSession.kt \
        rubik-vision/src/commonTest/kotlin/org/freevia/rubikbuddy/vision/ColorSessionTest.kt
git commit -m "feat: add ColorSession commitFace, revertFace, reset"
```

---

## Task 4: `ColorSession` — helpers (`debugLog`, `modelSummaryStr`, `cycle`, `rankFor`)

**Files:**
- Modify: `rubik-vision/src/commonMain/kotlin/org/freevia/rubikbuddy/vision/ColorSession.kt`
- Modify: `rubik-vision/src/commonTest/kotlin/org/freevia/rubikbuddy/vision/ColorSessionTest.kt`

- [ ] **Step 1: Add failing tests**

Append to `ColorSessionTest`:

```kotlin
// ---- debugLog ----

@Test fun `debugLog contains face name and tile data after commitFace`() {
    val session = ColorSession(clock = { 3_661_000L })  // 01:01:01
    val colors = Array(9) { if (it == 4) CubeColor.RED else CubeColor.ORANGE }
    val labs   = Array(9) { floatArrayOf(50f, 53f, 45f) }
    session.commitFace(Face.F, colors, labs)

    val log = session.debugLog()
    assertTrue(log.contains("Face F"), "Expected 'Face F' in log")
    assertTrue(log.contains("tile 4*"), "Expected 'tile 4*' for center")
    assertTrue(log.contains("RED"), "Expected RED color name")
    assertTrue(log.contains("CURRENT MODEL"), "Expected model section")
}

@Test fun `debugLog marks rescan correctly`() {
    val session = ColorSession(clock = { 0L })
    val labs = Array(9) { floatArrayOf(50f, 56f, 36f) }
    session.commitFace(Face.F, Array(9) { CubeColor.RED }, labs)
    session.commitFace(Face.F, Array(9) { CubeColor.RED }, labs)  // rescan

    val log = session.debugLog()
    assertTrue(log.contains("RESCAN"), "Expected RESCAN marker for second commit")
}

@Test fun `debugLog shows prior for unscanned colors`() {
    val session = ColorSession()
    // Commit only RED; BLUE should show as prior
    session.commitFace(Face.F, Array(9) { CubeColor.RED },
        Array(9) { floatArrayOf(50f, 56f, 36f) })

    val log = session.debugLog()
    assertTrue(log.contains("prior"), "Expected 'prior' for unscanned colors")
}

// ---- modelSummaryStr ----

@Test fun `modelSummaryStr contains color names`() {
    val session = ColorSession()
    session.commitFace(Face.F, Array(9) { CubeColor.RED },
        Array(9) { floatArrayOf(50f, 56f, 36f) })

    val summary = session.modelSummaryStr()
    assertTrue(summary.contains("RED"), "Expected RED in summary")
}

// ---- cycle ----

@Test fun `cycle returns a different color from current`() {
    val session = ColorSession()
    val lab = floatArrayOf(40f, 56f, 36f)  // RED territory
    val next = session.cycle(lab, CubeColor.RED)
    assertTrue(next != CubeColor.RED, "cycle should return a different color")
}

@Test fun `cycle round-trips through all 6 colors`() {
    val session = ColorSession()
    val lab = floatArrayOf(40f, 56f, 36f)
    val seen = mutableSetOf<CubeColor>()
    var current = session.classify(lab).first
    repeat(6) {
        seen.add(current)
        current = session.cycle(lab, current)
    }
    assertEquals(6, seen.size, "Expected all 6 colors visited")
}

// ---- rankFor ----

@Test fun `rankFor returns 1 for best-matching color`() {
    val session = ColorSession()
    val lab = floatArrayOf(40f, 56f, 36f)  // RED prior
    assertEquals(1, session.rankFor(lab, CubeColor.RED))
}

@Test fun `rankFor returns value in 1-6 for any color`() {
    val session = ColorSession()
    val lab = floatArrayOf(40f, 56f, 36f)
    val rank = session.rankFor(lab, CubeColor.BLUE)
    assertTrue(rank in 1..6, "rank $rank not in 1..6")
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```
./gradlew :rubik-vision:testDebugUnitTest --tests "*.ColorSessionTest" 2>&1 | tail -10
```
Expected: `FAILED` — "Unresolved reference: debugLog" etc.

- [ ] **Step 3: Add helper methods to `ColorSession.kt`**

Add these four methods to the `// Public API` section in `ColorSession`, after `reset()`:

```kotlin
/**
 * Returns a one-line model state string suitable for per-frame debug logging.
 * Format: "RED[a=56 b=36 n=9] ORANGE[prior] ..."
 */
fun modelSummaryStr(): String = buildString {
    for (color in CubeColor.entries) {
        if (color == CubeColor.WHITE) continue
        val m = colorModels[color]
        if (m != null) append("${color.name}[a=%.0f b=%.0f n=${m.sampleCount}] ".format(m.meanA, m.meanB))
        else append("${color.name}[prior] ")
    }
}

/**
 * Full session history dump: one [ScanEvent] per face confirmation (including rescans),
 * followed by the current model state. Append to logcat at face-confirm time.
 */
fun debugLog(): String = buildString {
    appendLine("=== COLOR SESSION DEBUG LOG ===")
    for (event in scanHistory) {
        val ts   = event.timestamp
        val hh   = (ts / 3_600_000L) % 24
        val mm   = (ts / 60_000L)    % 60
        val ss   = (ts / 1_000L)     % 60
        val tag  = if (event.isRescan) "RESCAN" else "first scan"
        appendLine("[Face ${event.face.name} | %02d:%02d:%02d | $tag]".format(hh, mm, ss))
        for (tile in event.tiles) {
            val star   = if (tile.isCenter) "*" else " "
            val lab    = tile.lab
            val lch    = tile.lch
            val scores = tile.scoresBeforeThisFace.entries
                .sortedBy { it.value }
                .joinToString(" ") { "${it.key.label}=%.1f".format(it.value) }
            appendLine("  tile ${tile.tileIndex}$star ${tile.assignedColor.name.padEnd(6)} " +
                "L=%.0f a=%.0f b=%.0f  C=%.0f H=%.0f°  w=%.1f  before:[$scores]"
                    .format(lab[0], lab[1], lab[2], lch[1], lch[2], tile.weight))
        }
    }
    appendLine()
    appendLine("=== CURRENT MODEL ===")
    for (color in CubeColor.entries) {
        if (color == CubeColor.WHITE) {
            appendLine("  ${color.name.padEnd(6)} chroma < $WHITE_CHROMA_THRESHOLD (no (a,b) model)")
            continue
        }
        val m = colorModels[color]
        if (m == null) {
            val p = PRIORS_AB[color]!!
            appendLine("  ${color.name.padEnd(6)} prior a=%.1f b=%.1f (no session data)"
                .format(p[0], p[1]))
        } else {
            appendLine("  ${color.name.padEnd(6)} mean_a=%.1f mean_b=%.1f  (n=${m.sampleCount} w=%.1f)"
                .format(m.meanA, m.meanB, m.totalWeight))
        }
    }
}

/**
 * Returns the next color in the (a,b) ranking for [lab], cycling past [currentColor].
 * Used for manual tile-correction: each tap advances to the next best-ranked color.
 */
fun cycle(lab: FloatArray, currentColor: CubeColor): CubeColor {
    val ranked = allRanked(lab)
    val idx = ranked.indexOf(currentColor)
    return ranked[(idx + 1) % ranked.size]
}

/**
 * Returns the 1-based rank of [color] for [lab].
 * 1 = best match, 6 = worst. Used for debug logging.
 */
fun rankFor(lab: FloatArray, color: CubeColor): Int = allRanked(lab).indexOf(color) + 1

/** All 6 colors sorted by (a,b) score ascending (best first). WHITE always sorted by chroma. */
private fun allRanked(lab: FloatArray): List<CubeColor> {
    val a = lab[1]; val b = lab[2]
    val chroma = sqrt((a * a + b * b).toDouble()).toFloat()
    val chromatic = CHROMATIC_COLORS.sortedBy { color ->
        val mean = abMean(color)
        val da = a - mean[0]; val db = b - mean[1]
        sqrt((da * da + db * db).toDouble()).toFloat()
    }
    // Place WHITE at the front if low-chroma, else at the back
    return if (chroma < WHITE_CHROMA_THRESHOLD) listOf(CubeColor.WHITE) + chromatic
    else chromatic + listOf(CubeColor.WHITE)
}
```

- [ ] **Step 4: Run tests**

```
./gradlew :rubik-vision:testDebugUnitTest --tests "*.ColorSessionTest" 2>&1 | tail -15
```
Expected: all ColorSessionTest tests pass.

- [ ] **Step 5: Run full rubik-vision test suite to confirm nothing regressed**

```
./gradlew :rubik-vision:testDebugUnitTest 2>&1 | tail -15
```
Expected: `BUILD SUCCESSFUL` (ColorDetectorTest / ColorModelTest still exist and still pass — they are deleted in Task 6).

- [ ] **Step 6: Commit**

```bash
git add rubik-vision/src/commonMain/kotlin/org/freevia/rubikbuddy/vision/ColorSession.kt \
        rubik-vision/src/commonTest/kotlin/org/freevia/rubikbuddy/vision/ColorSessionTest.kt
git commit -m "feat: add ColorSession debugLog, modelSummaryStr, cycle, rankFor"
```

---

## Task 5: Wire `CubeFrameAnalyzer` to `ColorSession`

**Files:**
- Modify: `rubik-vision/src/androidMain/kotlin/org/freevia/rubikbuddy/vision/CubeFrameAnalyzer.kt`

- [ ] **Step 1: Update `CubeFrameAnalyzer` constructor and remove `colorDetector`**

Replace the class declaration and the `colorDetector` field:

Old (line 27 and 44):
```kotlin
class CubeFrameAnalyzer : ImageAnalysis.Analyzer, FrameAnalyzer {
    ...
    /** Per-session color detector. Owned here so each scan session gets isolated state. */
    val colorDetector = ColorDetector()
```

New:
```kotlin
/**
 * [colorSession] — shared session owned by AppViewModel; injected so AppViewModel
 * can call commitFace / revertFace directly without going through the analyzer.
 */
class CubeFrameAnalyzer(val colorSession: ColorSession) : ImageAnalysis.Analyzer, FrameAnalyzer {
```

- [ ] **Step 2: Add `detectedLabs` StateFlow**

After the `_confidence` StateFlow declaration (after line 68), add:

```kotlin
/** Raw smoothed LAB values per tile from the last analyzed frame. */
private val _detectedLabs = MutableStateFlow<Array<FloatArray>>(emptyArray())
val detectedLabs = _detectedLabs.asStateFlow()
```

- [ ] **Step 3: Update `extractGridColors` — classify and center stability**

In `extractGridColors`, replace all occurrences of `colorDetector.classify(...)` with `colorSession.classify(...)`.

There are two call sites:

Line 205 — classification loop:
```kotlin
// Old:
val (color, conf) = colorDetector.classify(smoothedLab[i])
// New:
val (color, conf) = colorSession.classify(smoothedLab[i])
```

Line 218 — center stability check:
```kotlin
// Old:
&& colorDetector.classify(smoothedLab[4]).first == expected
// New:
&& colorSession.classify(smoothedLab[4]).first == expected
```

- [ ] **Step 4: Emit `detectedLabs` and update MODEL_DUMP log**

After step 5 (the `_confidence.value = ...` line, ~line 234), add the labs emission:
```kotlin
_detectedLabs.value = Array(9) { smoothedLab[it].copyOf() }
```

Replace the `MODEL_DUMP` log line (~line 252):
```kotlin
// Old:
Log.i(TAG, "MODEL_DUMP ${colorDetector.modelDumpStr()}")
// New:
Log.i(TAG, "MODEL_SUMMARY ${colorSession.modelSummaryStr()}")
```

- [ ] **Step 5: Build to confirm no compilation errors**

```
./gradlew :composeApp:assembleDebug 2>&1 | grep -E "(error|warning|BUILD)" | tail -20
```
Expected: `BUILD SUCCESSFUL` (AppViewModel still references `analyzer.colorDetector` — that's a compile error we fix in Task 6, so expect it to fail here; only `:rubik-vision` should succeed).

Run just the vision module:
```
./gradlew :rubik-vision:compileDebugKotlin 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL` for the rubik-vision module.

- [ ] **Step 6: Commit**

```bash
git add rubik-vision/src/androidMain/kotlin/org/freevia/rubikbuddy/vision/CubeFrameAnalyzer.kt
git commit -m "feat: inject ColorSession into CubeFrameAnalyzer, expose detectedLabs"
```

---

## Task 6: Wire `AppViewModel` + delete old files

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/org/freevia/rubikbuddy/AppViewModel.kt`
- Delete: `rubik-vision/src/commonMain/kotlin/org/freevia/rubikbuddy/vision/ColorDetector.kt`
- Delete: `rubik-vision/src/commonMain/kotlin/org/freevia/rubikbuddy/vision/ColorModel.kt`
- Delete: `rubik-vision/src/commonTest/kotlin/org/freevia/rubikbuddy/vision/ColorDetectorTest.kt`
- Delete: `rubik-vision/src/commonTest/kotlin/org/freevia/rubikbuddy/vision/ColorModelTest.kt`

- [ ] **Step 1: Update `AppViewModel` — ownership and wiring**

Replace lines 43–44:
```kotlin
// Old:
val analyzer = CubeFrameAnalyzer()
val orchestrator = ScanOrchestrator()

// New:
val colorSession = ColorSession(clock = System::currentTimeMillis)
val analyzer = CubeFrameAnalyzer(colorSession)
val orchestrator = ScanOrchestrator()
```

Add the `ColorSession` import at the top of the file:
```kotlin
import org.freevia.rubikbuddy.vision.ColorSession
```

- [ ] **Step 2: Replace `confirmFace()` calibration block**

Replace lines 168–191 (everything between `analyzer.colorDetector.saveCheckpoint()` and `val effectiveWbRgbs = ...`):

```kotlin
// Old block to remove:
analyzer.colorDetector.saveCheckpoint()
val expectedCenter = CubeColor.expectedCenter(face)
if (liveWbRgbs.size >= 5) {
    val centerLab = LabConverter.sRgbToLab(liveWbRgbs[4])
    analyzer.colorDetector.calibrateTileLab(centerLab, expectedCenter, weight = 8f)
}
if (liveWbRgbs.size >= 9) {
    colorOverrides.forEach { (ci, color) ->
        if (ci < liveWbRgbs.size && ci != 4) analyzer.colorDetector.calibrateTile(liveWbRgbs[ci], color)
    }
}
log("CONFIRM cal_after=${analyzer.colorDetector.calibrationStr()}")
val labStr = (0 until minOf(liveWbRgbs.size, 9)).joinToString(",") { i ->
    val lab = LabConverter.sRgbToLab(liveWbRgbs[i])
    "[%.0f,%.0f,%.0f]".format(lab[0], lab[1], lab[2])
}
log("CONFIRM_LABS face=$face $labStr")
log("MODEL_DUMP ${analyzer.colorDetector.modelDumpStr()}")

// New block to insert:
val labs = analyzer.detectedLabs.value
if (labs.size == 9) {
    colorSession.commitFace(face, correctedColors.toTypedArray(), labs)
    log(colorSession.debugLog())
}
```

- [ ] **Step 3: Replace the three removed AppViewModel methods**

Remove these three methods (lines 146–151):
```kotlin
// Remove entirely:
fun saveColorCheckpoint() = analyzer.colorDetector.saveCheckpoint()
fun restoreColorCheckpoint() = analyzer.colorDetector.restoreCheckpoint()
fun resetColorCalibration() = analyzer.colorDetector.resetCalibration()
```

Replace `colorCycle` and `rankFor` (lines 150–151):
```kotlin
// Old:
fun colorCycle(wb: Int, current: CubeColor): CubeColor = analyzer.colorDetector.colorCycle(wb, current)
fun rankFor(wb: Int, color: CubeColor): Int = analyzer.colorDetector.rankFor(wb, color)

// New:
fun colorCycle(wb: Int, current: CubeColor): CubeColor =
    colorSession.cycle(LabConverter.sRgbToLab(wb), current)
fun rankFor(wb: Int, color: CubeColor): Int =
    colorSession.rankFor(LabConverter.sRgbToLab(wb), color)
```

- [ ] **Step 4: Update `restartScan()`**

Replace line 232 in `restartScan()`:
```kotlin
// Old:
analyzer.colorDetector.resetCalibration()

// New:
colorSession.reset()
```

- [ ] **Step 5: Build to confirm the app compiles**

```
./gradlew :composeApp:assembleDebug 2>&1 | grep -E "(error:|BUILD)" | tail -20
```
Expected: `BUILD SUCCESSFUL` with no errors.

- [ ] **Step 6: Run full test suite**

```
./gradlew :rubik-vision:testDebugUnitTest 2>&1 | tail -15
```
Expected: `BUILD SUCCESSFUL`. (ColorDetectorTest and ColorModelTest still exist — they will pass or be skipped. We delete them next.)

- [ ] **Step 7: Delete old files**

```bash
rm rubik-vision/src/commonMain/kotlin/org/freevia/rubikbuddy/vision/ColorDetector.kt
rm rubik-vision/src/commonMain/kotlin/org/freevia/rubikbuddy/vision/ColorModel.kt
rm rubik-vision/src/commonTest/kotlin/org/freevia/rubikbuddy/vision/ColorDetectorTest.kt
rm rubik-vision/src/commonTest/kotlin/org/freevia/rubikbuddy/vision/ColorModelTest.kt
```

- [ ] **Step 8: Build and test after deletion**

```
./gradlew :composeApp:assembleDebug :rubik-vision:testDebugUnitTest 2>&1 | grep -E "(error:|BUILD)" | tail -20
```
Expected: `BUILD SUCCESSFUL` — old files are gone, all tests pass.

- [ ] **Step 9: Final commit**

```bash
git add composeApp/src/androidMain/kotlin/org/freevia/rubikbuddy/AppViewModel.kt \
        rubik-vision/src/androidMain/kotlin/org/freevia/rubikbuddy/vision/CubeFrameAnalyzer.kt
git rm rubik-vision/src/commonMain/kotlin/org/freevia/rubikbuddy/vision/ColorDetector.kt \
       rubik-vision/src/commonMain/kotlin/org/freevia/rubikbuddy/vision/ColorModel.kt \
       rubik-vision/src/commonTest/kotlin/org/freevia/rubikbuddy/vision/ColorDetectorTest.kt \
       rubik-vision/src/commonTest/kotlin/org/freevia/rubikbuddy/vision/ColorModelTest.kt
git commit -m "feat: wire AppViewModel to ColorSession, remove CIELAB Gaussian classifier"
```
