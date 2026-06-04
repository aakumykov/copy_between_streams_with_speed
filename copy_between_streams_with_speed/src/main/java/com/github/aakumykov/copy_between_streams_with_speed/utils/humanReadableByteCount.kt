package com.github.aakumykov.copy_between_streams_with_speed.utils


import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

fun humanReadableByteCount(
    bytes: Long,
    floatingDigits: Int = 3,
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
        "%.${floatingDigits}f %c$suffix",
        bytes / base.toDouble().pow(exp.toDouble()),
        pre
    )
}

fun humanReadableByteCount(
    bytes: Int?,
    floatingDigits: Int = 3,
    locale: Locale = Locale.getDefault(),
    sizeNames: String = "BKMGTPE",
    decimalNotation: Boolean = false
): String {
    return if (null == bytes) "NO_DATA_SIZE"
    else humanReadableByteCount(bytes.toLong(), floatingDigits, locale, sizeNames, decimalNotation)
}