package com.github.aakumykov.copy_between_streams_with_counting_demo.utils

import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

fun humanReadableByteCount(
    bytes: Long,
    locale: Locale = Locale.getDefault(),
    sizeNames: String = "BKMGTPE",
    decimalNotation: Boolean = false
): String {
    val base = if (decimalNotation) 1000 else 1024
    if (bytes < base) return "$bytes ${sizeNames[0]}"
    val exp = (ln(bytes.toDouble()) / ln(1024.0)).toInt()
    val pre = sizeNames[exp]
    val suffix = if (decimalNotation) "bit" else "B"
    return String.format(
        locale,
        "%.1f %c$suffix",
        bytes / base.toDouble().pow(exp.toDouble()),
        pre
    )
}