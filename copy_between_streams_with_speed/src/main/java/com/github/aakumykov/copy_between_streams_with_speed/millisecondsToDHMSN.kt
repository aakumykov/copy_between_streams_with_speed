package com.github.aakumykov.copy_between_streams_with_speed

import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

fun millisecondsToDHMSN(time: Long): String = time.milliseconds.toComponents { days, hours, minutes, seconds, nanoseconds ->
    String.Companion.format(Locale.getDefault(), "%02dд, %02dч, %02dм, %02dс (%02dнс)", days, hours, minutes, seconds, nanoseconds)
}