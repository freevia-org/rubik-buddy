package org.freevia.rubikbuddy.ui

import androidx.compose.ui.graphics.Color
import org.freevia.rubikbuddy.generated.resources.Res
import org.freevia.rubikbuddy.generated.resources.color_blue
import org.freevia.rubikbuddy.generated.resources.color_green
import org.freevia.rubikbuddy.generated.resources.color_orange
import org.freevia.rubikbuddy.generated.resources.color_red
import org.freevia.rubikbuddy.generated.resources.color_white
import org.freevia.rubikbuddy.generated.resources.color_yellow
import org.freevia.rubikbuddy.model.CubeColor
import org.freevia.rubikbuddy.model.Face
import org.jetbrains.compose.resources.StringResource

/**
 * Single source of truth for mapping [CubeColor] → Compose [Color].
 * Used by both the scanner overlay and the 3D solver view.
 */
fun CubeColor.toComposeColor(): Color = when (this) {
    CubeColor.WHITE  -> Color.White
    CubeColor.YELLOW -> Color(0xFFFFEB3B)
    CubeColor.RED    -> Color(0xFFF44336)
    CubeColor.ORANGE -> Color(0xFFFF9800)
    CubeColor.BLUE   -> Color(0xFF2196F3)
    CubeColor.GREEN  -> Color(0xFF4CAF50)
}

/**
 * Returns the CMP [StringResource] for the center color name of each face.
 * Resolve with [org.jetbrains.compose.resources.stringResource] in composables.
 */
fun Face.colorNameRes(): StringResource = when (this) {
    Face.U -> Res.string.color_white
    Face.F -> Res.string.color_red
    Face.R -> Res.string.color_blue
    Face.B -> Res.string.color_orange
    Face.L -> Res.string.color_green
    Face.D -> Res.string.color_yellow
}

fun CubeColor.colorNameRes(): StringResource = when (this) {
    CubeColor.WHITE  -> Res.string.color_white
    CubeColor.YELLOW -> Res.string.color_yellow
    CubeColor.RED    -> Res.string.color_red
    CubeColor.ORANGE -> Res.string.color_orange
    CubeColor.BLUE   -> Res.string.color_blue
    CubeColor.GREEN  -> Res.string.color_green
}
