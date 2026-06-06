package com.github.aakumykov.copy_between_streams_with_speed.utils

import kotlin.math.roundToLong

fun estimateTimeMs(sizeBytes: Int, speedBytePerSec: Int): Long {
    return (sizeBytes.toFloat() * 1000 / speedBytePerSec).roundToLong()
}

fun estimateTimeMs(sizeBytes: Long, speedBytePerSec: Long): Long {
    return (sizeBytes.toFloat() * 1000 / speedBytePerSec).roundToLong()
}