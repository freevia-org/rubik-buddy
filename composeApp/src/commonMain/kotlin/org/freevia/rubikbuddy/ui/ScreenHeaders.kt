package org.freevia.rubikbuddy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.freevia.rubikbuddy.generated.resources.Res
import org.freevia.rubikbuddy.generated.resources.scan_clear_cd
import org.freevia.rubikbuddy.generated.resources.scan_face_edit_hint
import org.freevia.rubikbuddy.generated.resources.scan_face_hint
import org.freevia.rubikbuddy.generated.resources.scan_face_title
import org.freevia.rubikbuddy.generated.resources.scan_tile_color_warning
import org.freevia.rubikbuddy.generated.resources.solve_direction_180deg
import org.freevia.rubikbuddy.generated.resources.solve_direction_clockwise
import org.freevia.rubikbuddy.generated.resources.solve_direction_counterclockwise
import org.freevia.rubikbuddy.generated.resources.solve_dir_180
import org.freevia.rubikbuddy.generated.resources.solve_dir_ccw
import org.freevia.rubikbuddy.generated.resources.solve_dir_cw
import org.freevia.rubikbuddy.generated.resources.solve_error_hint
import org.freevia.rubikbuddy.generated.resources.solve_error_title
import org.freevia.rubikbuddy.generated.resources.solve_face_back
import org.freevia.rubikbuddy.generated.resources.solve_face_bottom
import org.freevia.rubikbuddy.generated.resources.solve_face_front
import org.freevia.rubikbuddy.generated.resources.solve_face_left
import org.freevia.rubikbuddy.generated.resources.solve_face_right
import org.freevia.rubikbuddy.generated.resources.solve_face_top
import org.freevia.rubikbuddy.generated.resources.solve_initial_subtitle
import org.freevia.rubikbuddy.generated.resources.solve_initial_title
import org.freevia.rubikbuddy.generated.resources.solve_move_subtitle
import org.freevia.rubikbuddy.generated.resources.solve_move_title
import org.freevia.rubikbuddy.generated.resources.solve_solved_subtitle
import org.freevia.rubikbuddy.generated.resources.solve_solved_title
import org.freevia.rubikbuddy.generated.resources.solver_error_1
import org.freevia.rubikbuddy.generated.resources.solver_error_2
import org.freevia.rubikbuddy.generated.resources.solver_error_3
import org.freevia.rubikbuddy.generated.resources.solver_error_4
import org.freevia.rubikbuddy.generated.resources.solver_error_5
import org.freevia.rubikbuddy.generated.resources.solver_error_6
import org.freevia.rubikbuddy.generated.resources.solver_error_7
import org.freevia.rubikbuddy.generated.resources.solver_error_8
import org.freevia.rubikbuddy.generated.resources.solver_error_exception
import org.freevia.rubikbuddy.generated.resources.solver_error_timeout
import org.freevia.rubikbuddy.model.CubeColor
import org.freevia.rubikbuddy.model.Face
import org.freevia.rubikbuddy.model.Move
import org.freevia.rubikbuddy.solver.SolveResult
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ScanHeader(
    face:                Face,
    isAwaitingAlignment: Boolean,
    tileWarningColor:    CubeColor?,
    canClear:            Boolean,
    onClear:             () -> Unit,
    modifier:            Modifier = Modifier
) {
    val colorName = stringResource(face.colorNameRes())
    Column(modifier) {
        Row(Modifier, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.scan_face_title, colorName),
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (isAwaitingAlignment) stringResource(Res.string.scan_face_hint, colorName.lowercase())
                    else stringResource(Res.string.scan_face_edit_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2)
            }
            IconButton(onClick = onClear, enabled = canClear, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Default.Delete, stringResource(Res.string.scan_clear_cd),
                    tint = if (canClear) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            }
        }
        if (tileWarningColor != null) {
            val warnColorName = stringResource(tileWarningColor.colorNameRes())
            Text(
                stringResource(Res.string.scan_tile_color_warning, warnColorName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun solverErrorMessage(errorCode: String): String = when (errorCode) {
    "Error 1"         -> stringResource(Res.string.solver_error_1)
    "Error 2"         -> stringResource(Res.string.solver_error_2)
    "Error 3"         -> stringResource(Res.string.solver_error_3)
    "Error 4"         -> stringResource(Res.string.solver_error_4)
    "Error 5"         -> stringResource(Res.string.solver_error_5)
    "Error 6"         -> stringResource(Res.string.solver_error_6)
    "Error 7"         -> stringResource(Res.string.solver_error_7)
    "Error 8"         -> stringResource(Res.string.solver_error_8)
    "Error timeout"   -> stringResource(Res.string.solver_error_timeout)
    "Error exception" -> stringResource(Res.string.solver_error_exception)
    else              -> errorCode
}

@Composable
internal fun SolveHeader(
    solveResult: SolveResult?,
    currentStep: Int,
    totalMoves:  Int,
    moves:       List<Move>,
    modifier:    Modifier = Modifier
) {
    val errorResult = solveResult as? SolveResult.Error
    val (title, subtitle) = when {
        errorResult != null -> stringResource(Res.string.solve_error_title) to
            solverErrorMessage(errorResult.reason)
        currentStep == 0 -> stringResource(Res.string.solve_initial_title) to
            stringResource(Res.string.solve_initial_subtitle)
        currentStep <= totalMoves -> {
            val move = moves[currentStep - 1]
            val fmt  = move.notation
            val face = when (move.face) {
                Face.U -> stringResource(Res.string.solve_face_top)
                Face.D -> stringResource(Res.string.solve_face_bottom)
                Face.L -> stringResource(Res.string.solve_face_left)
                Face.R -> stringResource(Res.string.solve_face_right)
                Face.F -> stringResource(Res.string.solve_face_front)
                Face.B -> stringResource(Res.string.solve_face_back)
            }
            val short = when {
                fmt.endsWith("'") -> stringResource(Res.string.solve_dir_ccw)
                fmt.endsWith("2") -> stringResource(Res.string.solve_dir_180)
                else              -> stringResource(Res.string.solve_dir_cw)
            }
            val full = when {
                fmt.endsWith("'") -> stringResource(Res.string.solve_direction_counterclockwise)
                fmt.endsWith("2") -> stringResource(Res.string.solve_direction_180deg)
                else              -> stringResource(Res.string.solve_direction_clockwise)
            }
            stringResource(Res.string.solve_move_title, fmt, face, short) to
                stringResource(Res.string.solve_move_subtitle, face.uppercase(), full)
        }
        else -> stringResource(Res.string.solve_solved_title) to
            stringResource(Res.string.solve_solved_subtitle)
    }
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
            color = if (errorResult != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium,
            color = if (errorResult != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            minLines = 2)
        if (errorResult != null) {
            Text(
                stringResource(Res.string.solve_error_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
