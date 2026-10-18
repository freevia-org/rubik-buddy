package org.freevia.rubikbuddy.vision

import kotlin.math.abs
import kotlin.math.roundToLong

/** Locale-independent fixed precision for the small values in color diagnostics. */
internal fun Float.debugFixed(decimals: Int): String {
    require(decimals == 0 || decimals == 1)
    if (!isFinite()) return toString()
    val scale = if (decimals == 0) 1 else 10
    val rounded = (abs(toDouble()) * scale).roundToLong()
    val sign = if (toRawBits() < 0) "-" else ""
    return if (decimals == 0) "$sign$rounded"
    else "$sign${rounded / scale}.${rounded % scale}"
}
