package org.freevia.rubikbuddy.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.freevia.rubikbuddy.vision.FrameAnalyzer

/**
 * Platform-specific camera preview layer.
 * Android: full CameraX preview with debug overlay.
 * iOS: placeholder until native camera integration is implemented.
 */
@Composable
expect fun CameraPreviewLayer(
    analyzer: FrameAnalyzer,
    debugMode: Boolean,
    onDebugToggle: () -> Unit,
    modifier: Modifier = Modifier
)
