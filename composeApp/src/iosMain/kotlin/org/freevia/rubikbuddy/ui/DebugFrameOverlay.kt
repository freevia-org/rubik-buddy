package org.freevia.rubikbuddy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.freevia.rubikbuddy.vision.FrameAnalyzer

@Composable
actual fun DebugFrameOverlay(analyzer: FrameAnalyzer, modifier: Modifier) {
    val colors by analyzer.detectedColors.collectAsState()
    val labs by analyzer.detectedLabs.collectAsState()
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth(0.66f).aspectRatio(1f)) {
            repeat(3) { row ->
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    repeat(3) { col ->
                        val i = row*3+col
                        Box(Modifier.weight(1f).fillMaxHeight().padding(2.dp).background(Color.Black.copy(alpha=0.65f)), contentAlignment = Alignment.Center) {
                            Text("${colors.getOrNull(i)?.label ?: '?'}\nL ${labs[i][0].toInt()}\na ${labs[i][1].toInt()}\nb ${labs[i][2].toInt()}", color = Color.White, fontSize = 9.sp, lineHeight = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
