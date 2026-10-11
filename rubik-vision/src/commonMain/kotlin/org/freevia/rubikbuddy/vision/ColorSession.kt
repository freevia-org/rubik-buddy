package org.freevia.rubikbuddy.vision

import org.freevia.rubikbuddy.model.CubeColor
import org.freevia.rubikbuddy.model.Face
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Session-adaptive color classifier for Rubik's Cube facelets.
 *
 * Classification uses circular hue-angle distance (atan2(b,a)) — fully chroma-invariant.
 * WHITE is detected by low chroma before any hue scoring.
 *
 * Calibration: a flat map of confirmed tile measurements keyed by (Face, tileIndex).
 * [commitFace] replaces all 9 entries for a face and rebuilds color models.
 * [revertFace] removes a face's entries. [reset] clears all state.
 *
 * @param clock  Millisecond timestamp supplier. Inject a fixed clock in tests.
 */
class ColorSession(private val clock: () -> Long = { 0L }) {

    companion object {
        const val CENTER_TILE_WEIGHT      = 4.0f
        const val NON_CENTER_TILE_WEIGHT  = 1.0f
        /** Chroma sqrt(a²+b²) below this → WHITE, regardless of hue. */
        const val WHITE_CHROMA_THRESHOLD  = 15.0f
        /** Hue-angle gap (degrees) below which the top-2 colors are considered ambiguous. */
        const val UNCERTAIN_GAP_THRESHOLD = 4.0f
        /** Hue-angle gap (degrees) that maps to confidence 1.0. */
        private const val CONFIDENCE_SCALE = 15.0f
        private const val CENTER_TILE_INDEX = 4
    }

    // Camera-tuned hue-angle priors [degrees, 0–360). WHITE classified by chroma only.
    private val PRIORS_H: Map<CubeColor, Float> = mapOf(
        CubeColor.RED    to  32f,   // atan2(34, 54)
        CubeColor.ORANGE to  42f,   // atan2(50, 55)
        CubeColor.YELLOW to  83f,   // atan2(64,  8)
        CubeColor.GREEN  to 143f,   // atan2(32,-42)
        CubeColor.BLUE   to 288f    // atan2(-50,16) normalised
    )

    private val CHROMATIC_COLORS = CubeColor.entries.filter { it != CubeColor.WHITE }

    // Flat map: (face, tileIndex) → confirmed measurement
    private val measurements = mutableMapOf<Pair<Face, Int>, TileMeasurement>()

    // Rebuilt after every commitFace / revertFace
    private var colorModels = mutableMapOf<CubeColor, HueMean>()

    // Append-only session history (never deleted, even on rescan)
    private val scanHistory = mutableListOf<ScanEvent>()

    // =========================================================================
    //  Public API
    // =========================================================================

    /**
     * Classifies [lab] (CIELAB float[3]).
     * Returns the best [CubeColor] and a confidence in [0, 1].
     *
     * Confidence = gap between top-1 and top-2 hue scores (degrees) / [CONFIDENCE_SCALE].
     * Tiles with gap < [UNCERTAIN_GAP_THRESHOLD] are ambiguous.
     */
    fun classify(lab: FloatArray): Pair<CubeColor, Float> {
        val chroma = sqrt((lab[1] * lab[1] + lab[2] * lab[2]).toDouble()).toFloat()
        if (chroma < WHITE_CHROMA_THRESHOLD) return CubeColor.WHITE to 1.0f

        val tileH = hueDeg(lab)
        val ranked = CHROMATIC_COLORS.map { color ->
            color to angularDist(tileH, hueModel(color))
        }.sortedBy { it.second }

        val gap = ranked[1].second - ranked[0].second
        val confidence = (gap / CONFIDENCE_SCALE).coerceIn(0f, 1f)
        return ranked[0].first to confidence
    }

    /**
     * Stores confirmed tile measurements for [face], rebuilds color models, and appends a
     * [ScanEvent] to the session history. If this face was previously committed, its old
     * measurements are replaced (rescan behaviour).
     *
     * [colors] — 9 confirmed CubeColors; index 4 (center) is always authoritative.
     * [labs]   — 9 CIELAB float[3] arrays, one per tile (index matches [colors]).
     */
    fun commitFace(face: Face, colors: Array<CubeColor>, labs: Array<FloatArray>) {
        require(colors.size == 9) { "Expected 9 colors, got ${colors.size}" }
        require(labs.size == 9)   { "Expected 9 labs, got ${labs.size}" }

        val isRescan = (0 until 9).any { measurements.containsKey(face to it) }

        // Capture scores with the CURRENT model before this face changes it
        val scoresBefore: List<Map<CubeColor, Float>> = labs.map { scoresFor(it) }

        // Replace all 9 measurements for this face
        for (i in 0 until 9) {
            val weight = if (i == CENTER_TILE_INDEX) CENTER_TILE_WEIGHT else NON_CENTER_TILE_WEIGHT
            measurements[face to i] = TileMeasurement(
                face          = face,
                tileIndex     = i,
                assignedColor = colors[i],
                lab           = labs[i].copyOf(),
                weight        = weight
            )
        }

        rebuild()

        val tiles = (0 until 9).map { i ->
            TileRecord(
                tileIndex            = i,
                isCenter             = i == CENTER_TILE_INDEX,
                assignedColor        = colors[i],
                lab                  = labs[i].copyOf(),
                lch                  = LabConverter.labToLch(labs[i]),
                weight               = if (i == CENTER_TILE_INDEX) CENTER_TILE_WEIGHT
                                       else NON_CENTER_TILE_WEIGHT,
                scoresBeforeThisFace = scoresBefore[i]
            )
        }
        scanHistory.add(ScanEvent(clock(), face, isRescan, tiles))
    }

    /** Removes all measurements for [face] and rebuilds models. */
    fun revertFace(face: Face) {
        (0 until 9).forEach { measurements.remove(face to it) }
        rebuild()
    }

    /** Clears all measurements, models, and scan history. */
    fun reset() {
        measurements.clear()
        scanHistory.clear()
        rebuild()  // derives empty colorModels from empty measurements
    }

    /**
     * Returns a one-line model state string suitable for per-frame debug logging.
     * Format: "RED[H=32° n=9] ORANGE[prior] ..."
     */
    fun modelSummaryStr(): String = buildString {
        for (color in CubeColor.entries) {
            if (color == CubeColor.WHITE) continue
            val m = colorModels[color]
            if (m != null) append("${color.name}[H=%.0f° n=${m.sampleCount}] ".format(m.meanH))
            else append("${color.name}[prior] ")
        }
    }

    /**
     * Full session history dump: one [ScanEvent] per face confirmation (including rescans),
     * followed by the current model state. Append to logcat at face-confirm time.
     */
    fun debugLog(): String = buildString {
        appendLine("=== COLOR SESSION DEBUG LOG ===")
        for (event in scanHistory) {
            val ts   = event.timestamp
            val hh   = (ts / 3_600_000L) % 24
            val mm   = (ts / 60_000L)    % 60
            val ss   = (ts / 1_000L)     % 60
            val tag  = if (event.isRescan) "RESCAN" else "first scan"
            appendLine("[Face ${event.face.name} | %02d:%02d:%02d | $tag]".format(hh, mm, ss))
            for (tile in event.tiles) {
                val star   = if (tile.isCenter) "*" else " "
                val lab    = tile.lab
                val lch    = tile.lch
                val scores = tile.scoresBeforeThisFace.entries
                    .sortedBy { it.value }
                    .joinToString(" ") { "${it.key.label}=%.1f".format(it.value) }
                appendLine("  tile ${tile.tileIndex}$star ${tile.assignedColor.name.padEnd(6)} " +
                    "L=%.0f a=%.0f b=%.0f  C=%.0f H=%.0f°  w=%.1f  before:[$scores]"
                        .format(lab[0], lab[1], lab[2], lch[1], lch[2], tile.weight))
            }
        }
        appendLine()
        appendLine("=== CURRENT MODEL ===")
        for (color in CubeColor.entries) {
            if (color == CubeColor.WHITE) {
                appendLine("  ${color.name.padEnd(6)} chroma < $WHITE_CHROMA_THRESHOLD (no hue model)")
                continue
            }
            val m = colorModels[color]
            if (m == null) {
                val h = PRIORS_H[color]!!
                appendLine("  ${color.name.padEnd(6)} prior H=%.1f° (no session data)".format(h))
            } else {
                appendLine("  ${color.name.padEnd(6)} mean_H=%.1f°  (n=${m.sampleCount} w=%.1f)"
                    .format(m.meanH, m.totalWeight))
            }
        }
    }

    /**
     * Returns the next color in the hue ranking for [lab], cycling past [currentColor].
     * Used for manual tile-correction: each tap advances to the next best-ranked color.
     */
    fun cycle(lab: FloatArray, currentColor: CubeColor): CubeColor {
        val ranked = allRanked(lab)
        val idx = ranked.indexOf(currentColor)
        return ranked[(idx + 1) % ranked.size]
    }

    /**
     * Returns the 1-based rank of [color] for [lab].
     * 1 = best match, 6 = worst. Used for debug logging.
     */
    fun rankFor(lab: FloatArray, color: CubeColor): Int = allRanked(lab).indexOf(color) + 1

    /** All 6 colors sorted by hue-angle score ascending (best first). WHITE always sorted by chroma. */
    private fun allRanked(lab: FloatArray): List<CubeColor> {
        val chroma = sqrt((lab[1] * lab[1] + lab[2] * lab[2]).toDouble()).toFloat()
        val tileH = hueDeg(lab)
        val chromatic = CHROMATIC_COLORS.sortedBy { color -> angularDist(tileH, hueModel(color)) }
        return if (chroma < WHITE_CHROMA_THRESHOLD) listOf(CubeColor.WHITE) + chromatic
        else chromatic + listOf(CubeColor.WHITE)
    }

    /** For testing only: returns mean hue (degrees) from session model for [color], or null if using prior. */
    internal fun modelMeanFor(color: CubeColor): Float? = colorModels[color]?.meanH

    // =========================================================================
    //  Private helpers
    // =========================================================================

    /** Returns the session hue angle for [color], or the prior if no session data. */
    private fun hueModel(color: CubeColor): Float =
        colorModels[color]?.meanH ?: (PRIORS_H[color] ?: 0f)

    /** Hue-angle distance scores for [lab] against all 6 colors using the current model. */
    private fun scoresFor(lab: FloatArray): Map<CubeColor, Float> {
        val chroma = sqrt((lab[1] * lab[1] + lab[2] * lab[2]).toDouble()).toFloat()
        val tileH = hueDeg(lab)
        return buildMap {
            put(CubeColor.WHITE, chroma)
            for (color in CHROMATIC_COLORS) {
                put(color, angularDist(tileH, hueModel(color)))
            }
        }
    }

    /** Rebuilds per-color models from current measurements using circular weighted mean hue. */
    private fun rebuild() {
        val newModels = mutableMapOf<CubeColor, HueMean>()
        for (color in CHROMATIC_COLORS) {
            val entries = measurements.values.filter { it.assignedColor == color }
            if (entries.isEmpty()) continue
            val totalWeight = entries.sumOf { it.weight.toDouble() }.toFloat()
            var sumSin = 0.0
            var sumCos = 0.0
            for (entry in entries) {
                val hRad = hueDeg(entry.lab) * (kotlin.math.PI / 180.0)
                sumSin += entry.weight * sin(hRad)
                sumCos += entry.weight * cos(hRad)
            }
            var meanH = (atan2(sumSin, sumCos) * (180.0 / kotlin.math.PI)).toFloat()
            if (meanH < 0f) meanH += 360f
            newModels[color] = HueMean(meanH, totalWeight, entries.size)
        }
        colorModels = newModels
    }

    /** Hue angle of [lab] in degrees, normalised to [0, 360). */
    private fun hueDeg(lab: FloatArray): Float {
        val h = (atan2(lab[2].toDouble(), lab[1].toDouble()) * (180.0 / kotlin.math.PI)).toFloat()
        return if (h < 0f) h + 360f else h
    }

    /** Shortest circular distance between two hue angles in degrees. Result in [0, 180]. */
    private fun angularDist(h1: Float, h2: Float): Float {
        val d = abs(h1 - h2) % 360f
        return if (d > 180f) 360f - d else d
    }

    // =========================================================================
    //  Internal data
    // =========================================================================

    private data class HueMean(
        val meanH: Float,
        val totalWeight: Float,
        val sampleCount: Int
    )
}

// =============================================================================
//  Data classes (package-level — shared with CubeFrameAnalyzer)
// =============================================================================

data class TileMeasurement(
    val face: Face,
    val tileIndex: Int,
    val assignedColor: CubeColor,
    val lab: FloatArray,
    val weight: Float
) {
    // Equality by identity key only (face + index), not lab values
    override fun equals(other: Any?) =
        other is TileMeasurement && face == other.face && tileIndex == other.tileIndex
    override fun hashCode() = 31 * face.hashCode() + tileIndex
}

data class TileRecord(
    val tileIndex: Int,
    val isCenter: Boolean,
    val assignedColor: CubeColor,
    val lab: FloatArray,
    val lch: FloatArray,
    val weight: Float,
    val scoresBeforeThisFace: Map<CubeColor, Float>
)

data class ScanEvent(
    val timestamp: Long,
    val face: Face,
    val isRescan: Boolean,
    val tiles: List<TileRecord>
)
