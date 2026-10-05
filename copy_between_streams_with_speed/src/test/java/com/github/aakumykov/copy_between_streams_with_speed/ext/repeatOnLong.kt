package com.github.aakumykov.copy_between_streams_with_speed.ext

import com.github.aakumykov.copy_between_streams_with_speed.utils.random
import org.junit.Assert
import org.junit.Test

inline fun repeat(times: Long, action: (Long) -> Unit) {
    for (index in 0 until times) {
        action(index)
    }
}

class repeatOnLongUnitTest() {
    @Test
    fun test_count() {
        var count: Long = 0
        repeat(1024) {
            val n = random.nextLong(1,Long.MAX_VALUE)
            repeat(n) {
                count++
            }
            Assert.assertEquals(n,count)
        }
    }
}