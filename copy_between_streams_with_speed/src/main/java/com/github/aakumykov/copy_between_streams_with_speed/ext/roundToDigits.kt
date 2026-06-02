package com.github.aakumykov.copy_between_streams_with_speed.ext

import java.util.Locale

internal fun Float.roundToFloatingDigits(n: Int): String {
    return String.format(
        Locale.getDefault(),
        "%.${n}f",
        this
    )
}

internal fun Double.roundToFloatingDigits(n: Int): String {
    return String.format(
        Locale.getDefault(),
        "%.${n}f",
        this
    )
}