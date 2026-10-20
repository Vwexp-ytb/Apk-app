package com.omnicam.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraMathTest {
    @Test fun bracketUsesThreeOrderedExposureLevels() {
        assertEquals(listOf(-6, 0, 6), CameraMath.exposureBracketIndices(0, -12, 12, 1f / 3f))
    }

    @Test fun bracketClampsToDeviceRange() {
        assertEquals(listOf(-2, 2, 4), CameraMath.exposureBracketIndices(2, -2, 4, 0.5f))
    }

    @Test fun sliderClampsProgress() {
        assertEquals(100L, CameraMath.normalizedSliderValue(-20, 100, 1100))
        assertEquals(1100L, CameraMath.normalizedSliderValue(1200, 100, 1100))
        assertEquals(600L, CameraMath.normalizedSliderValue(500, 100, 1100))
    }

    @Test fun bracketHandlesSingleSupportedExposureValue() {
        assertEquals(listOf(0, 0, 0), CameraMath.exposureBracketIndices(0, 0, 0, 0f))
    }

    @Test fun bracketClampsCurrentIndexBeforeApplyingOffsets() {
        assertEquals(listOf(2, 4, 4), CameraMath.exposureBracketIndices(8, 0, 4, 1f))
    }

    @Test(expected = IllegalArgumentException::class)
    fun bracketRejectsInvalidRange() {
        CameraMath.exposureBracketIndices(0, 3, -3, 1f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun sliderRejectsInvalidRange() {
        CameraMath.normalizedSliderValue(500, 1000, 100)
    }
}
