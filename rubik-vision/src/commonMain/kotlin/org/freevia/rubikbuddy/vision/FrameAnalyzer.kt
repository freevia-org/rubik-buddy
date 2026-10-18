package org.freevia.rubikbuddy.vision

import org.freevia.rubikbuddy.model.CubeColor
import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-agnostic interface for frame-by-frame cube color detection.
 * Android: implemented by CubeFrameAnalyzer using ImageProxy / CameraX.
 * iOS: implemented by CubeFrameAnalyzer using CVPixelBuffer / AVFoundation.
 */
interface FrameAnalyzer {
    val detectedColors: StateFlow<List<CubeColor>>
    val detectedWbRgbs: StateFlow<IntArray>
    val detectedRgbs: StateFlow<IntArray>
    val detectedLabs: StateFlow<Array<FloatArray>>
    val confidence: StateFlow<FloatArray>
    val centerStable: StateFlow<Boolean>
    var debugMode: Boolean

    /** Expected center color for the face currently being scanned. */
    var expectedCenterColor: CubeColor?

    /** Resets temporal smoothing buffers between faces. */
    fun resetTemporalBuffers()
}

expect fun createFrameAnalyzer(colorSession: ColorSession): FrameAnalyzer
