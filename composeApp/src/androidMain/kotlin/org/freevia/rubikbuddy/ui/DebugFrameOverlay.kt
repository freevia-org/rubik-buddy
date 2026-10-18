package org.freevia.rubikbuddy.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.freevia.rubikbuddy.vision.CubeFrameAnalyzer
import org.freevia.rubikbuddy.vision.FrameAnalyzer

@Composable
actual fun DebugFrameOverlay(analyzer: FrameAnalyzer, modifier: Modifier) {
    val bitmap by (analyzer as CubeFrameAnalyzer).debugBitmap.collectAsState()
    bitmap?.let { bmp ->
        val image = bmp.asImageBitmap()
        Canvas(modifier) {
            val px = (minOf(size.width, size.height) * 0.66f).toInt()
            drawImage(image, IntOffset.Zero, IntSize(bmp.width, bmp.height),
                IntOffset(((size.width-px)/2).toInt(), ((size.height-px)/2).toInt()), IntSize(px, px))
        }
    }
}
