package com.github.aakumykov.copy_between_streams_with_speed.utils

fun repeatFromTo(
    from: Int,
    toUntil: Int,
    step: Int = 1,
    action: (n: Int) -> Unit
) {
    var i = from
    while (i < toUntil) {
        action.invoke(i)
        i += step
    }
}