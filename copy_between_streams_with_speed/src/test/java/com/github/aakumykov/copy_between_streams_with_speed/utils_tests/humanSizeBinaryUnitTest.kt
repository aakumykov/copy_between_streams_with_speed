package com.github.aakumykov.copy_between_streams_with_speed.utils_tests

import com.github.aakumykov.copy_between_streams_with_speed.utils.MEGABYTES
import com.github.aakumykov.copy_between_streams_with_speed.utils.humanSizeBinary
import org.junit.Test

class humanSizeBinaryUnitTest {

    @Test
    fun bytes_test() {
        listOf(
            1000.MEGABYTES,
            2000.MEGABYTES,
            3000.MEGABYTES,
            4000.MEGABYTES
        ).forEach { size ->
            val hs = size.humanSizeBinary(2)
            println(hs)
        }
    }
}