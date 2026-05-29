package com.github.aakumykov.copy_between_streams_with_counting_demo.utils

import kotlin.random.Random

fun time2invoke(chancePercent: Int, block: (() -> Unit)? = null): Boolean {
    return if (Random.nextInt(1,101) <= chancePercent) {
        block?.invoke()
        true
    } else false
}