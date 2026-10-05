package com.github.aakumykov.copy_between_streams_with_speed.utils

import java.util.Locale

fun Int.humanSizeBinary(
    floatingDigits: Int = 2,
    locale: Locale = Locale.getDefault(),
    sizeNames: String = "BKMGTPE",
    decimalNotation: Boolean = false
): String {
    return humanReadableByteCount(
        bytes = this,
        floatingDigits = 2,
        locale = Locale.getDefault(),
        sizeNames = "BKMGTPE",
        decimalNotation = false
    )
}

fun Long.humanSizeBinary(
    floatingDigits: Int = 2,
    locale: Locale = Locale.getDefault(),
    sizeNames: String = "BKMGTPE",
    decimalNotation: Boolean = false
): String {
    return humanReadableByteCount(
        bytes = this,
        floatingDigits = 2,
        locale = Locale.getDefault(),
        sizeNames = "BKMGTPE",
        decimalNotation = false
    )
}