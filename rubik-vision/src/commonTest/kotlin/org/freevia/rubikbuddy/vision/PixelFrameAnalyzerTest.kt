package org.freevia.rubikbuddy.vision

import kotlin.test.*
import org.freevia.rubikbuddy.model.*

class PixelFrameAnalyzerTest {
    private val palette = intArrayOf(0xFFF8F8F8.toInt(), 0xFFFFFF00.toInt(), 0xFFFF0000.toInt(), 0xFFFF8000.toInt(), 0xFF0033FF.toInt(), 0xFF00AA00.toInt())

    @Test fun sampleCubeSurvivesClassificationAndFaceOrientation() {
        val session = ColorSession()
        val analyzer = PixelFrameAnalyzer(session)
        val scans = ScanOrchestrator()
        val cube = CubeState.solved().applyMoves(Move.parseSequence("R U F"))
        for (face in ScanOrchestrator.SCAN_ORDER) {
            analyzer.expectedCenterColor = CubeColor.expectedCenter(face)
            analyzer.resetTemporalBuffers()
            val faceColors = (0..8).map { cube.colorAt(face,it)!! }
            val camera = if (face == Face.D) listOf(6,3,0,7,4,1,8,5,2).map { faceColors[it] } else faceColors
            repeat(10) {
                analyzer.process(300,300,1.0) { x,y ->
                    palette[camera[((y-51)/66).coerceIn(0,2)*3+((x-51)/66).coerceIn(0,2)].ordinal]
                }
            }
            assertTrue(analyzer.centerStable.value, "Center must align for $face")
            assertEquals(camera,analyzer.detectedColors.value, "Classified face $face")
            session.commitFace(face,camera.toTypedArray(),analyzer.detectedLabs.value)
            scans.commitCurrentFace(analyzer.detectedColors.value,analyzer.detectedWbRgbs.value)
        }
        assertTrue(scans.isScanComplete.value)
        assertContentEquals(cube.facelets, scans.buildCubeState().facelets)
    }

    @Test fun resetRequiresFreshStableFrames() {
        val analyzer = PixelFrameAnalyzer(ColorSession())
        analyzer.expectedCenterColor = CubeColor.YELLOW
        repeat(10) { analyzer.process(60,60,1.0) { _,_ -> palette[CubeColor.YELLOW.ordinal] } }
        assertTrue(analyzer.centerStable.value)
        analyzer.resetTemporalBuffers()
        assertFalse(analyzer.centerStable.value)
        assertTrue(analyzer.detectedColors.value.isEmpty())
        analyzer.process(60,60,1.0) { _,_ -> palette[CubeColor.BLUE.ordinal] }
        assertFalse(analyzer.centerStable.value)
    }
    @Test fun darkAndInvalidFramesCannotKeepOldAlignment() {
        val analyzer = PixelFrameAnalyzer(ColorSession())
        analyzer.expectedCenterColor = CubeColor.WHITE
        repeat(10) { analyzer.process(60,60,1.0) { _,_ -> palette[CubeColor.WHITE.ordinal] } }
        assertTrue(analyzer.centerStable.value)
        analyzer.process(60,60,1.0) { _,_ -> 0xFF000000.toInt() }
        assertFalse(analyzer.centerStable.value)
        assertTrue(analyzer.detectedColors.value.isEmpty())
        analyzer.process(60,60,Double.NaN) { _,_ -> error("No pixels expected") }
        assertFalse(analyzer.centerStable.value)
    }

    @Test fun aspectFillSamplesTheVisibleCentralSquare() {
        val analyzer = PixelFrameAnalyzer(ColorSession())
        analyzer.expectedCenterColor = CubeColor.YELLOW
        repeat(10) {
            analyzer.process(640,480,0.5) { x,y ->
                // Preview's short edge is 240 pixels, so its 66% guide is 158.4 pixels.
                assertTrue(x in 240..400 && y in 160..320, "Unexpected sample $x,$y")
                palette[CubeColor.YELLOW.ordinal]
            }
        }
        assertTrue(analyzer.centerStable.value)
    }

}
