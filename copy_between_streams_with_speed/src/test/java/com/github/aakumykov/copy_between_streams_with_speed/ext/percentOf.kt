package com.github.aakumykov.copy_between_streams_with_speed.ext

fun Long.percentOf(base: Double): Double {
    return 100.0 * this / base
}

fun Double.percentOf(base: Double): Double {
    return 100.0 * this / base
}

fun Float.percentOf(base: Float): Double {
    return 100.0 * this / base
}