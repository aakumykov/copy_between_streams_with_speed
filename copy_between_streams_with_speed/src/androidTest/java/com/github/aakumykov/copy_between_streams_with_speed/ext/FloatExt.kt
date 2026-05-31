package com.github.aakumykov.copy_between_streams_with_speed.ext

import java.util.Locale

fun Float.roundToDigits(n: Int): String {
    return String.format(
        Locale.getDefault(),
        "%.${n}f",
        this
    )
}