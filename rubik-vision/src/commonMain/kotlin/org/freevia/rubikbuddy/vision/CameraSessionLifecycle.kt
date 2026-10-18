package org.freevia.rubikbuddy.vision

/** Main-thread policy shared by the iOS capture adapter and hardware-free tests. */
class CameraSessionLifecycle {
    var configured = false
    var foreground = false
    var interrupted = false
    private var closed = false
    val shouldRun: Boolean get() = configured && foreground && !interrupted && !closed
    fun close() { closed = true }
}
