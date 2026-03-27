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
}
