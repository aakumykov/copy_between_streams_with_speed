package com.github.aakumykov.copy_between_streams_with_speed.utils

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

val Long.humanDecimalPlaces: String get() {
    val symbols = DecimalFormatSymbols(Locale.getDefault()).apply { groupingSeparator = '_' }
    return (DecimalFormat("#,##0", symbols)).format(this)
}

val Int.humanDecimalPlaces: String get() {
    val symbols = DecimalFormatSymbols(Locale.getDefault()).apply { groupingSeparator = '_' }
    return (DecimalFormat("#,##0", symbols)).format(this)
}