@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package org.freevia.rubikbuddy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.unit.dp
import kotlinx.cinterop.*
import org.freevia.rubikbuddy.platformIsSimulator
import org.freevia.rubikbuddy.platformTimeMillis
import org.freevia.rubikbuddy.model.*
import org.freevia.rubikbuddy.vision.*
import platform.AVFoundation.*
import platform.CoreMedia.*
import platform.CoreVideo.*
import platform.darwin.NSObject
import platform.UIKit.*
import platform.darwin.*

@Composable
actual fun CameraPreviewLayer(analyzer: FrameAnalyzer, debugMode: Boolean, onDebugToggle: () -> Unit, modifier: Modifier) {
    val pixels = analyzer as PixelFrameAnalyzer
    if (platformIsSimulator) {
        // Simulator has no physical camera. Feed a deterministic scrambled cube through
        // the same pixel classifier; never silently substitute it on a real device.
        Box(modifier, contentAlignment = Alignment.BottomCenter) {
            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Simulator · sample cube", color = Color.White)
                Button(onClick = {
                    val face = Face.entries.first { CubeColor.expectedCenter(it) == pixels.expectedCenterColor }
                    val cube = CubeState.solved().applyMoves(Move.parseSequence("R U F"))
                    val faceColors = (0..8).map { cube.colorAt(face, it)!! }
                    val cameraColors = if (face == Face.D) listOf(6,3,0,7,4,1,8,5,2).map { faceColors[it] } else faceColors
                    val palette = intArrayOf(0xFFF8F8F8.toInt(), 0xFFFFFF00.toInt(), 0xFFFF0000.toInt(), 0xFFFF8000.toInt(), 0xFF0033FF.toInt(), 0xFF00AA00.toInt())
                    pixels.resetTemporalBuffers()
                    repeat(10) {
                        pixels.process(300,300,1.0) { x,y ->
                            val col = ((x-51)/66).coerceIn(0,2)
                            val row = ((y-51)/66).coerceIn(0,2)
                            palette[cameraColors[row*3+col].ordinal]
                        }
                    }
                }) { Text("Load sample face") }
            }
        }
    } else {
        var error by remember { mutableStateOf<String?>(null) }
        var retry by remember { mutableIntStateOf(0) }
        Box(modifier) {
            key(retry) {
                UIKitView(factory = { CameraView(pixels) { error = it } },
                    modifier = Modifier.fillMaxSize(), onRelease = { it.close() },
                    properties = UIKitInteropProperties(interactionMode = null))
            }
            error?.let { message ->
                Column(Modifier.align(Alignment.BottomCenter).background(MaterialTheme.colorScheme.errorContainer).padding(12.dp)) {
                    Text(message)
                    Button(onClick = { error = null; retry++ }) { Text("Retry camera") }
                }
            }
        }
    }
}
