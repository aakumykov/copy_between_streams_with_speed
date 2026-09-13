package com.github.aakumykov.copy_between_streams_with_speed.ext

import java.text.SimpleDateFormat

fun Long.toHMS(format: String = "HH:mm:ss"): String {
    return SimpleDateFormat(format).format(this)
}