package com.github.aakumykov.copy_between_streams_with_speed.utils

fun percent(value: Long, base: Long): Double {
    return (value.toDouble() / base)*100
}