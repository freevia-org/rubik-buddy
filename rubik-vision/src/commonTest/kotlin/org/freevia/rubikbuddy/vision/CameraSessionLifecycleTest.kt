package org.freevia.rubikbuddy.vision

import kotlin.test.*

class CameraSessionLifecycleTest {
    @Test fun captureRequiresConfigurationAndForeground() {
        val state = CameraSessionLifecycle()
        state.foreground = true
        assertFalse(state.shouldRun)
        state.configured = true
        assertTrue(state.shouldRun)
        state.foreground = false
        assertFalse(state.shouldRun)
        state.foreground = true
        assertTrue(state.shouldRun)
    }
    @Test fun interruptionPausesAndCloseIsFinal() {
        val state = CameraSessionLifecycle().apply { foreground = true; configured = true }
        state.interrupted = true
        assertFalse(state.shouldRun)
        state.interrupted = false
        assertTrue(state.shouldRun)
        state.close()
        state.foreground = false
        state.foreground = true
        state.configured = true
        assertFalse(state.shouldRun)
    }
}
