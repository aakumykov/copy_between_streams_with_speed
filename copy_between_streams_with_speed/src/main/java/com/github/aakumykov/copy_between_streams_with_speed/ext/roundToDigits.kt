package com.github.aakumykov.copy_between_streams_with_speed.ext

import java.util.Locale

// FIXME: не тестировано, а зря
internal fun Float.roundToFloatingDigits(n: Int): Float {
    return if (0f == this) this
    else String.format(
        Locale.ROOT,
        "%.${n}f",
        this
    ).toFloat()
}

// FIXME: не тестировано, а зря
internal fun Double.roundToFloatingDigits(n: Int): Double {
    return if (0.0 == this) this
    else String.format(
        Locale.ROOT,
        "%.${n}f",
        this
    ).toDouble()
}