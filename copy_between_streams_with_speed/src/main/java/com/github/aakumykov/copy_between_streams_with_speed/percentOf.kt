package com.github.aakumykov.copy_between_streams_with_speed

import com.github.aakumykov.copy_between_streams_with_speed.ext.roundToFloatingDigits

fun percentOf(actual: Long, base: Long, digitsAfterComma: Int = 3): String {
    return percentOf(actual.toDouble(), base.toDouble(), digitsAfterComma)
}

fun percentOf(actual: Double, base: Double, digitsAfterComma: Int = 3): String {
    return ((actual / base)*100).roundToFloatingDigits(digitsAfterComma)
}