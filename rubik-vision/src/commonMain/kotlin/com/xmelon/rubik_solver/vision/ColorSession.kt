package com.xmelon.rubik_solver.vision

import com.xmelon.rubik_solver.model.CubeColor
import com.xmelon.rubik_solver.model.Face
import kotlin.math.sqrt

/**
 * Session-adaptive color classifier for Rubik's Cube facelets.
 *
 * Classification uses Euclidean distance in the (a,b) plane only — L is excluded
 * as it is the most lighting-sensitive axis. WHITE is detected by low chroma before
 * any distance scoring.
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
        /** Chroma sqrt(a²+b²) below this → WHITE, regardless of (a,b) distance. */
        const val WHITE_CHROMA_THRESHOLD  = 15.0f
        /** (a,b) distance gap below which the top-2 colors are considered ambiguous. */
        const val UNCERTAIN_GAP_THRESHOLD = 8.0f
        /** Gap of this many (a,b) units maps to confidence 1.0. */
        private const val CONFIDENCE_SCALE = 20.0f
        private const val CENTER_TILE_INDEX = 4
    }

    // Camera-tuned (a, b) priors — used before any face is confirmed.
    // WHITE is classified by chroma only; no (a,b) prior needed.
    private val PRIORS_AB: Map<CubeColor, FloatArray> = mapOf(
        CubeColor.YELLOW to floatArrayOf(  8f,  64f),
        CubeColor.RED    to floatArrayOf( 54f,  34f),
        CubeColor.ORANGE to floatArrayOf( 55f,  50f),
        CubeColor.BLUE   to floatArrayOf( 16f, -50f),
        CubeColor.GREEN  to floatArrayOf(-42f,  32f)
    )

    private val CHROMATIC_COLORS = CubeColor.entries.filter { it != CubeColor.WHITE }

    // Flat map: (face, tileIndex) → confirmed measurement
    private val measurements = mutableMapOf<Pair<Face, Int>, TileMeasurement>()

    // Rebuilt after every commitFace / revertFace
    private var colorModels = mutableMapOf<CubeColor, ColorMean>()

    // Append-only session history (never deleted, even on rescan)
    private val scanHistory = mutableListOf<ScanEvent>()

    // =========================================================================
    //  Public API
    // =========================================================================

    /**
     * Classifies [lab] (CIELAB float[3]).
     * Returns the best [CubeColor] and a confidence in [0, 1].
     *
     * Confidence = gap between top-1 and top-2 (a,b) scores / [CONFIDENCE_SCALE].
     * Tiles with gap < [UNCERTAIN_GAP_THRESHOLD] are ambiguous.
     */
    fun classify(lab: FloatArray): Pair<CubeColor, Float> {
        val a = lab[1]; val b = lab[2]
        val chroma = sqrt((a * a + b * b).toDouble()).toFloat()

        if (chroma < WHITE_CHROMA_THRESHOLD) return CubeColor.WHITE to 1.0f

        val ranked = CHROMATIC_COLORS.map { color ->
            val mean = abMean(color)
            val da = a - mean[0]; val db = b - mean[1]
            color to sqrt((da * da + db * db).toDouble()).toFloat()
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
     * Format: "RED[a=56 b=36 n=9] ORANGE[prior] ..."
     */
    fun modelSummaryStr(): String = buildString {
        for (color in CubeColor.entries) {
            if (color == CubeColor.WHITE) continue
            val m = colorModels[color]
            if (m != null) append("${color.name}[a=%.0f b=%.0f n=${m.sampleCount}] ".format(m.meanA, m.meanB))
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
                appendLine("  ${color.name.padEnd(6)} chroma < $WHITE_CHROMA_THRESHOLD (no (a,b) model)")
                continue
            }
            val m = colorModels[color]
            if (m == null) {
                val p = PRIORS_AB[color]!!
                appendLine("  ${color.name.padEnd(6)} prior a=%.1f b=%.1f (no session data)"
                    .format(p[0], p[1]))
            } else {
                appendLine("  ${color.name.padEnd(6)} mean_a=%.1f mean_b=%.1f  (n=${m.sampleCount} w=%.1f)"
                    .format(m.meanA, m.meanB, m.totalWeight))
            }
        }
    }

    /**
     * Returns the next color in the (a,b) ranking for [lab], cycling past [currentColor].
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

    /** All 6 colors sorted by (a,b) score ascending (best first). WHITE always sorted by chroma. */
    private fun allRanked(lab: FloatArray): List<CubeColor> {
        val a = lab[1]; val b = lab[2]
        val chroma = sqrt((a * a + b * b).toDouble()).toFloat()
        val chromatic = CHROMATIC_COLORS.sortedBy { color ->
            val mean = abMean(color)
            val da = a - mean[0]; val db = b - mean[1]
            sqrt((da * da + db * db).toDouble()).toFloat()
        }
        // Place WHITE at the front if low-chroma, else at the back
        return if (chroma < WHITE_CHROMA_THRESHOLD) listOf(CubeColor.WHITE) + chromatic
        else chromatic + listOf(CubeColor.WHITE)
    }

    /** For testing only: returns (mean_a, mean_b) of the session model for [color], or null if using prior. */
    internal fun modelMeanFor(color: CubeColor): Pair<Float, Float>? {
        val m = colorModels[color] ?: return null
        return m.meanA to m.meanB
    }

    // =========================================================================
    //  Private helpers (used in Tasks 2–4)
    // =========================================================================

    /** Returns the session (a,b) mean for [color], or the prior if no session data. */
    private fun abMean(color: CubeColor): FloatArray {
        val m = colorModels[color]
        return if (m != null) floatArrayOf(m.meanA, m.meanB)
        else PRIORS_AB[color] ?: floatArrayOf(0f, 0f)
    }

    /** (a,b) distance scores for [lab] against all 6 colors using the current model. */
    private fun scoresFor(lab: FloatArray): Map<CubeColor, Float> {
        val a = lab[1]; val b = lab[2]
        val chroma = sqrt((a * a + b * b).toDouble()).toFloat()
        return buildMap {
            put(CubeColor.WHITE, chroma)   // lower chroma = better match for WHITE
            for (color in CHROMATIC_COLORS) {
                val mean = abMean(color)
                val da = a - mean[0]; val db = b - mean[1]
                put(color, sqrt((da * da + db * db).toDouble()).toFloat())
            }
        }
    }

    /** Rebuilds per-color models from current measurements. */
    private fun rebuild() {
        val newModels = mutableMapOf<CubeColor, ColorMean>()
        for (color in CHROMATIC_COLORS) {
            val entries = measurements.values.filter { it.assignedColor == color }
            if (entries.isEmpty()) continue
            val totalWeight = entries.sumOf { it.weight.toDouble() }.toFloat()
            val meanA = entries.sumOf { (it.lab[1] * it.weight).toDouble() }.toFloat() / totalWeight
            val meanB = entries.sumOf { (it.lab[2] * it.weight).toDouble() }.toFloat() / totalWeight
            newModels[color] = ColorMean(meanA, meanB, totalWeight, entries.size)
        }
        colorModels = newModels
    }

    // =========================================================================
    //  Internal data
    // =========================================================================

    private data class ColorMean(
        val meanA: Float,
        val meanB: Float,
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
