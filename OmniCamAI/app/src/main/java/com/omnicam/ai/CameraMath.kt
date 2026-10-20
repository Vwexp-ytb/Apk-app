package com.omnicam.ai

import kotlin.math.roundToInt

/** Pure camera math kept separate so exposure-bracket logic can be tested on the JVM. */
internal object CameraMath {
    fun exposureBracketIndices(current: Int, lower: Int, upper: Int, stepEv: Float): List<Int> {
        require(lower <= upper) { "Invalid exposure compensation range" }
        val step = stepEv.takeIf { it > 0f } ?: 1f
        val offset = (2f / step).roundToInt()
        return listOf((current - offset).coerceIn(lower, upper), current.coerceIn(lower, upper), (current + offset).coerceIn(lower, upper))
    }

    fun normalizedSliderValue(progress: Int, lower: Long, upper: Long): Long {
        require(lower <= upper) { "Invalid range" }
        return lower + ((upper - lower) * progress.coerceIn(0, 1000) / 1000)
    }
}
