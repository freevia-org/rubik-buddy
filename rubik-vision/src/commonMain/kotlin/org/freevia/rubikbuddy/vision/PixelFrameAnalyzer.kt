package org.freevia.rubikbuddy.vision

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.freevia.rubikbuddy.model.CubeColor

/** Main-thread frame processor for upright pixels matching an aspect-fill preview. */
class PixelFrameAnalyzer(private val session: ColorSession) : FrameAnalyzer {
    override var expectedCenterColor: CubeColor? = null
    override var debugMode = false
    private val colors = MutableStateFlow<List<CubeColor>>(emptyList())
    override val detectedColors = colors.asStateFlow()
    private val rgbs = MutableStateFlow(IntArray(0))
    override val detectedWbRgbs = rgbs.asStateFlow()
    override val detectedRgbs get() = detectedWbRgbs
    private val labs = MutableStateFlow(Array(9) { FloatArray(3) })
    override val detectedLabs = labs.asStateFlow()
    private val confidences = MutableStateFlow(FloatArray(0))
    override val confidence = confidences.asStateFlow()
    private val stable = MutableStateFlow(false)
    override val centerStable = stable.asStateFlow()
    private var matches = 0
    private val history = Array(9) { ArrayDeque<FloatArray>() }

    override fun resetTemporalBuffers() {
        history.forEach { it.clear() }
        matches = 0
        stable.value = false
        colors.value = emptyList()
        rgbs.value = IntArray(0)
        labs.value = Array(9) { FloatArray(3) }
        confidences.value = FloatArray(0)
    }

    fun process(width: Int, height: Int, previewAspect: Double, pixel: (Int, Int) -> Int) {
        if (width < 12 || height < 12 || !previewAspect.isFinite() || previewAspect <= 0) {
            resetTemporalBuffers()
            return
        }
        val visibleW = minOf(width.toDouble(), height * previewAspect)
        val visibleH = minOf(height.toDouble(), width / previewAspect)
        val size = minOf(visibleW, visibleH) * 0.66
        val left = (width-size)/2
        val top = (height-size)/2
        val cell = size/3
        val raw = Array(9) { i ->
            val sum = FloatArray(3)
            var count = 0
            val x0 = (left + (i%3 + 0.15)*cell).toInt()
            val x1 = (left + (i%3 + 0.85)*cell).toInt()
            val y0 = (top + (i/3 + 0.15)*cell).toInt()
            val y1 = (top + (i/3 + 0.85)*cell).toInt()
            for (y in y0 until y1 step 2) for (x in x0 until x1 step 2) {
                val rgb = pixel(x,y)
                if (((rgb shr 16) and 255) + ((rgb shr 8) and 255) + (rgb and 255) > 30) {
                    val lab = LabConverter.sRgbToLab(rgb)
                    for (c in 0..2) sum[c] += lab[c]
                    count++
                }
            }
            if (count < 3) {
                // A covered lens / empty dark tile must not align as a white sticker.
                resetTemporalBuffers()
                return
            }
            FloatArray(3) { sum[it]/count }
        }
        val smoothed = Array(9) { i ->
            val lab = raw[i]
            val ring = history[i]
            ring.addLast(lab)
            if (ring.size > 8) ring.removeFirst()
            FloatArray(3) { c ->
                val sorted = ring.map { it[c] }.sorted()
                val n = sorted.size
                if (n%2 == 1) sorted[n/2] else (sorted[n/2-1]+sorted[n/2])/2
            }
        }
        val classified = smoothed.map { session.classify(it) }
        colors.value = classified.map { it.first }
        rgbs.value = IntArray(9) { LabConverter.labToSRgb(smoothed[it]) }
        labs.value = smoothed
        confidences.value = FloatArray(9) { classified[it].second }
        matches = if (classified[4].first == expectedCenterColor) minOf(matches+1,10) else 0
        stable.value = matches >= 10
    }
}
