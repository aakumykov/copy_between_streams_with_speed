package com.github.aakumykov.copy_between_streams_with_speed.utils

internal fun percentOf(actual: Long, base: Long, digitsAfterComma: Int = 3): Float {
    return percentOf(actual.toFloat(), base.toFloat(), digitsAfterComma)
}

internal fun percentOf(actual: Float, base: Float, digitsAfterComma: Int = 3): Float {
    return ((actual / base)*100)
}