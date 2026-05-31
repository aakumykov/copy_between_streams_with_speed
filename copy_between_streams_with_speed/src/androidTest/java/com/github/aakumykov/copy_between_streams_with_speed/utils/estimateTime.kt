package com.github.aakumykov.copy_between_streams_with_speed.utils

import kotlin.math.roundToLong

fun estimateTimeMs(sizeBytes: Int, speedBytePerSec: Int): Long {
    return ((sizeBytes.toFloat() / speedBytePerSec) * 1000).roundToLong()
}