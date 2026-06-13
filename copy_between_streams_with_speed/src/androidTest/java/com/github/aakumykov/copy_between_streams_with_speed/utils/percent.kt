package com.github.aakumykov.copy_between_streams_with_speed.utils

// TODO: а может, Float?
fun percent(value: Long, base: Long): Double {
    return (value.toDouble() / base)*100
}