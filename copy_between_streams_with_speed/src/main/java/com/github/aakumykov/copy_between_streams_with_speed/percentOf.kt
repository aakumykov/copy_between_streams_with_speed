package com.github.aakumykov.copy_between_streams_with_speed

import com.github.aakumykov.copy_between_streams_with_speed.ext.roundToFloatingDigits

fun percentOf(actual: Long, base: Long, digitsAfterComma: Int = 3): String {
    return percentOf(actual.toFloat(), base.toFloat(), digitsAfterComma)
}

fun percentOf(actual: Float, base: Float, digitsAfterComma: Int = 3): String {
    return ((actual / base)*100).roundToFloatingDigits(digitsAfterComma)
}