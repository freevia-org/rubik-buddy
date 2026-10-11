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

    // Measured on a Pixel-class phone under cool light (2026-10-11): white tiles had L~70-77,
    // chroma 18-20 and were misread as BLUE by the hue scoring; real blue tiles had L<=39.
    @Test fun `classify light bluish-grey tile under cool light as WHITE not BLUE`() {
        val session = ColorSession()
        assertEquals(CubeColor.WHITE, session.classify(floatArrayOf(76f, -8f, -18f)).first)
        assertEquals(CubeColor.WHITE, session.classify(floatArrayOf(70f, -7f, -17f)).first)
        assertEquals(CubeColor.WHITE, session.classify(floatArrayOf(77f, -7f, -18f)).first)
    }

    @Test fun `classify light warm-cast tile as WHITE not YELLOW`() {
        val session = ColorSession()
        assertEquals(CubeColor.WHITE, session.classify(floatArrayOf(72f, 3f, 20f)).first)
    }

    @Test fun `classify real blue tiles stay BLUE`() {
        val session = ColorSession()
        assertEquals(CubeColor.BLUE, session.classify(floatArrayOf(22f, 24f, -50f)).first)
        assertEquals(CubeColor.BLUE, session.classify(floatArrayOf(39f, 10f, -39f)).first)
        assertEquals(CubeColor.BLUE, session.classify(floatArrayOf(30f, 15f, -41f)).first)
    }

    @Test fun `classify real yellow tile stays YELLOW`() {
        val session = ColorSession()
        assertEquals(CubeColor.YELLOW, session.classify(floatArrayOf(61f, 6f, 61f)).first)
        assertEquals(CubeColor.YELLOW, session.classify(floatArrayOf(63f, 4f, 48f)).first)
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

    @Test fun `commitFace rescan replaces previous measurements`() {
        val session = ColorSession()
        session.commitFace(Face.F, Array(9) { CubeColor.RED }, makeLabs(56f, 36f))
        session.commitFace(Face.F, Array(9) { CubeColor.ORANGE }, makeLabs(50f, 55f))
        // RED model now has NO session data — falls back to prior
        val (red, _) = session.classify(floatArrayOf(50f, 56f, 36f))
        assertEquals(CubeColor.RED, red)
        // ORANGE model has session data at a=50 b=55
        val (orange, _) = session.classify(floatArrayOf(50f, 50f, 55f))
        assertEquals(CubeColor.ORANGE, orange)
    }

    @Test fun `commitFace for different faces both contribute to model`() {
        val session = ColorSession()
        session.commitFace(Face.F, Array(9) { CubeColor.RED },  makeLabs(56f, 36f))
        session.commitFace(Face.R, Array(9) { CubeColor.BLUE }, makeLabs(16f, -50f))
        val (r, _) = session.classify(floatArrayOf(50f, 56f, 36f))
        val (b, _) = session.classify(floatArrayOf(50f, 16f, -50f))
        assertEquals(CubeColor.RED,  r)
        assertEquals(CubeColor.BLUE, b)
    }

    // ---- revertFace ----

    @Test fun `revertFace removes face measurements and model reverts to prior`() {
        val session = ColorSession()
        session.commitFace(Face.F, Array(9) { CubeColor.RED }, makeLabs(30f, 10f))
        session.revertFace(Face.F)
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
        val (color, _) = session.classify(floatArrayOf(50f, 56f, 36f))
        assertEquals(CubeColor.RED, color)
    }

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
}
