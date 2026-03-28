package com.xmelon.rubik_solver.vision

import com.xmelon.rubik_solver.model.CubeColor
import com.xmelon.rubik_solver.model.Face
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
        // Expected mean_a = (8*56*1 + 1*49*4) / (8*1 + 1*4) = (448+196)/12 = 53.67
        // Expected mean_b = (8*36*1 + 1*54*4) / 12 = (288+216)/12 = 42.0
        val (c1, _) = session.classify(floatArrayOf(50f, 53.67f, 42.0f))
        val (c2, _) = session.classify(floatArrayOf(50f, 56f, 36f))
        assertEquals(CubeColor.RED, c1)
        assertEquals(CubeColor.RED, c2)
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
}
