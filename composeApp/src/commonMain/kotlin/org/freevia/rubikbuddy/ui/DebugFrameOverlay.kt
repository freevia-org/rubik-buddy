package org.freevia.rubikbuddy.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.freevia.rubikbuddy.vision.FrameAnalyzer

@Composable
expect fun DebugFrameOverlay(analyzer: FrameAnalyzer, modifier: Modifier)
