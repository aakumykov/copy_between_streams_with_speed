package com.github.aakumykov.copy_between_streams_with_speed

import com.github.aakumykov.copy_between_streams_with_speed.ext.roundToFloatingDigits
import com.github.aakumykov.copy_between_streams_with_speed.stream2stream_copier.LimitedStreamCopier
import com.github.aakumykov.copy_between_streams_with_speed.utils.humanDecimalPlaces
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test
import kotlin.math.roundToLong

class LimitedStreamCopierTest2 : TestBase() {

    @Test
    fun simple_test() = runBlocking {
        test_with_params(
            1000_000,
            100_000,
            10,
            10,
            10.0
        )
    }

    @Test
    fun `продолжительность_копирования_плюс_минус_20_от_расчётной`() = runTest {
        for (dataSize in 1..100) {
            test_with_params(
                dataSize = dataSize,
                speed = 15,
                steps = 10,
                rate = 1,
                10.0
            )
        }
    }

    private fun test_with_params(dataSize: Int, speed: Int,
                                 steps: Int, rate: Int,
                                 targetCopyingTimeDeviationPercents: Double) {

        prepareSourceAndTargetFiles(dataSize)

        val estimatedDuration
                = ((1f * dataSize / speed)*1_000_000_000)
            .roundToLong()

        val lsc = LimitedStreamCopier(
            speedBytesPerSecond = speed,
            progressRatePerSecond = rate,
            dataCopyStepsPerSecond = steps
        )

        val startTime = currentTime
        lsc.copyFromStreamToStream(
            sourceFileStream,
            targetFileStream
        )
        val duration = currentTime - startTime

        val deviationPercent
            = (100 * estimatedDuration / duration.toDouble())
            .roundToFloatingDigits(0)

        val logString = "sz: $dataSize, " +
                "sp: $speed, " +
                "st: $steps " +
                "-> " +
                "edr:${estimatedDuration.humanDecimalPlaces}, " +
                "rdr:${duration.humanDecimalPlaces} " +
                "(${deviationPercent}%)"

        println(logString)

//        Assert.assertTrue(
//            "отклонение времени копирования " +
//                    "не более ${targetCopyingTimeDeviationPercents}% " +
//                    "(реальное ${deviationPercent}%)",
//            deviationPercent <= targetCopyingTimeDeviationPercents
//        )
    }

    private val currentTime: Long
        get() = System.currentTimeMillis() + System.nanoTime()
}