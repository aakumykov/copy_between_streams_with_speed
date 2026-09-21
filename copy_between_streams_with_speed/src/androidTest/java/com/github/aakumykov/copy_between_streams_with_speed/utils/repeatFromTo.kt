package com.github.aakumykov.copy_between_streams_with_speed.utils

import org.junit.Assert
import org.junit.Test

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


class RepeatFromToTest {

    @Test
    fun repeat_from_1_to_10() {
        val res = mutableListOf<Int>()
        repeatFromTo(1,10) {
            res.add(it)
        }
        Assert.assertEquals(
            listOf(1,2,3,4,5,6,7,8,9),
            res
        )
    }

    @Test
    fun repeat_from_0_to_10() {
        val res = mutableListOf<Int>()
        repeatFromTo(0,10) {
            res.add(it)
        }
        Assert.assertEquals(
            listOf(0,1,2,3,4,5,6,7,8,9),
            res
        )
    }
}