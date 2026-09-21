package com.github.aakumykov.copy_between_streams_with_speed.ext

import java.util.Locale

internal fun Float.roundToFloatingDigits(n: Int): Float {
    return String.format(
        Locale.getDefault(),
        "%.${n}f",
        this
    ).toFloat()
}

internal fun Double.roundToFloatingDigits(n: Int): Double {
    return String.format(
        Locale.getDefault(),
        "%.${n}f",
        this
    ).toDouble()
}