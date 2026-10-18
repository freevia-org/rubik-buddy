package org.freevia.rubikbuddy.vision

import kotlin.test.Test
import kotlin.test.assertEquals

class DebugFormattingTest {
    @Test fun fixedPrecisionDiagnostics() {
        assertEquals("12", 12.4f.debugFixed(0))
        assertEquals("13", 12.5f.debugFixed(0))
        assertEquals("-13", (-12.5f).debugFixed(0))
        assertEquals("2.0", 1.96f.debugFixed(1))
        assertEquals("4.0", 4f.debugFixed(1))
        assertEquals("-0.0", (-0.01f).debugFixed(1))
        assertEquals("NaN", Float.NaN.debugFixed(1))
    }
}
