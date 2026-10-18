package org.freevia.rubikbuddy.vision

actual fun createFrameAnalyzer(colorSession: ColorSession): FrameAnalyzer = PixelFrameAnalyzer(colorSession)
