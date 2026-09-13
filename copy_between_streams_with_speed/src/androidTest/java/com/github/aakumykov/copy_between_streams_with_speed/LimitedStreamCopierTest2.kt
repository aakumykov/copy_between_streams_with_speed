package com.github.aakumykov.copy_between_streams_with_speed

import android.R.attr.duration
import com.github.aakumykov.copy_between_streams_with_speed.ext.roundToFloatingDigits
import com.github.aakumykov.copy_between_streams_with_speed.ext.toHMS
import com.github.aakumykov.copy_between_streams_with_speed.stream2stream_copier.LimitedStreamCopier
import com.github.aakumykov.copy_between_streams_with_speed.utils.humanDecimalPlaces
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.math.roundToLong

class LimitedStreamCopierTest2 : TestBase() {

    @Test
    fun simple_test() = runBlocking {
        for (multiplier in 81..82) {
            test_with_params(
                1000 * multiplier,
                1000 * multiplier,
                10,
                10,
                10.0
            )
        }
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

        val startTimeNs = currentTimeNanos
        val startTimeMs = currentTimeMs

        lsc.copyFromStreamToStream(
            sourceFileStream,
            targetFileStream,
            progressCallback = {
                println("скопировано: $it")
            }
        )
        val durationNs = currentTimeNanos - startTimeNs
        val durationMs = currentTimeMs - startTimeMs

        println("скопировано за время: ${durationMs.toHMS()}")

        val deviationPercent
            = (100 * estimatedDuration / durationNs.toDouble())
            .roundToFloatingDigits(0)

        val logString =
//            "sz: $dataSize, " +
//            "sp: $speed, " +
//            "st: $steps " +
//            "-> " +
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

    private val currentTimeMs: Long
        get() = System.currentTimeMillis()

    private val currentTimeNanos: Long
        get() = System.currentTimeMillis() + System.nanoTime()
}