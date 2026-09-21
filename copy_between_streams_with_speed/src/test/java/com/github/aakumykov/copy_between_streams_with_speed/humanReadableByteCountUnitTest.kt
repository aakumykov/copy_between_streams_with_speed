package com.github.aakumykov.copy_between_streams_with_speed

import com.github.aakumykov.copy_between_streams_with_speed.utils.humanReadableByteCount
import com.github.aakumykov.copy_between_streams_with_speed.utils.random
import org.junit.Assert
import org.junit.Test

class humanReadableByteCountUnitTest {

    @Test
    fun human_readable_byte_count_test() {

        fun work(isDecimalBase: Boolean) {
            val size = random.nextLong(1, 1000)
            val humanSize = humanReadableByteCount(size, decimalNotation = isDecimalBase)
            println("size: $size, human: $humanSize")
            Assert.assertEquals("$size B", humanSize)
        }

        println("байты десятичные:")
        repeat(10) { work(true) }

        println("байты двоичные:")
        repeat(10) { work(false) }
    }

    /*@Test
    fun test_1000(){

    }*/

    @Test
    fun human_readable_kilobyte_count_test(){
        fun work(decimalNotation: Boolean, checkingBlock: ((size:Int, hSize:String) -> Unit)? = null) {
            repeat(10) { i ->
                val size = (2+i) * 1000
                val hSize = humanReadableByteCount(size, decimalNotation = decimalNotation)
                println("size: $size, hSize: $hSize")
                checkingBlock?.invoke(size,hSize)
            }
        }
        println("----- десятичные -----")
        work(true) { size, hSize ->
            Assert.assertEquals("${size/1000},000 Kbit", hSize)
        }
        println("----- двоичные -----")
        work(false)
    }
}