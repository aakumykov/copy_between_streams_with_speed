package com.github.aakumykov.copy_between_streams_with_speed.utils

import java.util.Locale
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds

fun millisecondsToDHMSN(time: Double): String = time.milliseconds.toComponents { days, hours, minutes, seconds, nanoseconds ->
    String.format(
        Locale.getDefault(),
        "%02dд, %02dч, %02dм, %02dс, %02dмc",
        days,
        hours,
        minutes,
        seconds,
        (nanoseconds.toDouble() / 1_000_000).roundToLong()
    )
}
