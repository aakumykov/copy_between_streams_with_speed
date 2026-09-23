package com.github.aakumykov.copy_between_streams_with_speed

import com.github.aakumykov.copy_between_streams_with_speed.ext.roundToFloatingDigits
import com.github.aakumykov.copy_between_streams_with_speed.utils.KILOBYTES
import com.github.aakumykov.copy_between_streams_with_speed.utils.MEGABYTES
import com.github.aakumykov.copy_between_streams_with_speed.utils.humanDecimalPlaces
import com.github.aakumykov.copy_between_streams_with_speed.utils.humanSizeBinary
import com.github.aakumykov.copy_between_streams_with_speed.utils.random
import org.junit.Test
import kotlin.math.roundToInt

class LimitedStreamCopierNewUnitTest : StreamCopierTestBase() {

    @Test
    fun b() {
        /*repeat(100) {
            val size = random.nextInt(1, 10*1024*1024)
            val speed = random.nextInt(1, 10*1024*1024)
            val steps = 10
            doCopy(size, speed, steps)
        }*/
    }

    @Test
    fun anomaly_test() {
        doCopy((6.54 * 1.KILOBYTES).roundToInt(), (2.01 * 1.MEGABYTES).roundToInt(), 10)
        doCopy((232.88 * 1.KILOBYTES).roundToInt(), (8.68 * 1.MEGABYTES).roundToInt(), 10)
        doCopy((320.08 * 1.KILOBYTES).roundToInt(), (1.11 * 1.MEGABYTES).roundToInt(), 10)
    }


    private fun doCopy(dataSizeBytes: Int, speedBytesPerSec: Int, stepsPerSec: Int) {

        val expectedDurationMs = 1000.toDouble() * dataSizeBytes / speedBytesPerSec

        prepareSourceAndTargetFiles(dataSizeBytes)

        val startTime = System.currentTimeMillis()
        LimitedStreamCopierNew().copyFromStreamToStream(
            getSourceFileStream,
            getTargetFileStream,
            speedBytesPerSec,
            stepsPerSecond = stepsPerSec
        )
        val duration = System.currentTimeMillis() - startTime
        val durationPercent = (100 * duration / expectedDurationMs).roundToFloatingDigits(5)

        val resultMsg = "size:${dataSizeBytes.humanSizeBinary()}, " +
                "speed:${speedBytesPerSec.humanSizeBinary()}, " +
                "steps:$stepsPerSec, " +
                "время: ${duration.humanDecimalPlaces} мс / ${expectedDurationMs.humanDecimalPlaces} мс (${durationPercent}%)"

        println(resultMsg)
    }
}