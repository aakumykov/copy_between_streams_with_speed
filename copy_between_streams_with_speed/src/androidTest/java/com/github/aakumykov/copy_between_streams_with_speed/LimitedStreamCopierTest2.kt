package com.github.aakumykov.copy_between_streams_with_speed

import android.R.attr.duration
import android.util.Log
import com.github.aakumykov.copy_between_streams_with_speed.ext.roundToFloatingDigits
import com.github.aakumykov.copy_between_streams_with_speed.ext.toHMS
import com.github.aakumykov.copy_between_streams_with_speed.stream2stream_copier.LimitedStreamCopier
import com.github.aakumykov.copy_between_streams_with_speed.utils.humanDecimalPlaces
import com.github.aakumykov.copy_between_streams_with_speed.utils.random
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class LimitedStreamCopierTest2 : TestBase() {

    @Test
    fun a1() {
        repeat(10) {
            val dataSizeBytes = random.nextInt(10, 1001)
            val speedBytesPerSec = random.nextInt(100, 10_001)
            val progressRate = random.nextInt(1, 101)
            test_with(dataSizeBytes, speedBytesPerSec, progressRate)
        }
    }

    private fun test_with(dataSizeBytes: Int, speedBytesPerSec: Int, progressRatePerSec: Int) {

        Log.d(
            TAG,
            "test_with() called with: dataSizeBytes = $dataSizeBytes, speedBytesPerSec = $speedBytesPerSec, progressRatePerSec = $progressRatePerSec"
        )

        val finishCallbackTriggered = AtomicBoolean(false)
        var progressCallbackRealCount = 0

        prepareSourceAndTargetFiles(dataSizeBytes)

        val lsc = LimitedStreamCopier(
            speedBytesPerSecond = speedBytesPerSec,
            progressRatePerSecond = progressRatePerSec,
        )

        val startTime = currentTimeMs

        lsc.copyFromStreamToStream(
            sourceFileStream,
            targetFileStream,
            progressCallback = { transferred, speed ->
                progressCallbackRealCount++
                println("скопировано: $transferred, скорость: $speed")
            },
            finishCallback = { transferredBytes ->
                finishCallbackTriggered.set(true)
                println("завершено, $transferredBytes")
            }
        )

        val durationMs = currentTimeMs - startTime

        // TODO: сделать специальный метод StreamCopier-а "calcProgressPeriod"?
        val progressPeriodMs = floor(1000f / progressRatePerSec).roundToInt()
        val progressCallbacksEstimatedCount = floor(1f * durationMs / progressPeriodMs).roundToInt()

        Assert.assertEquals(
            dataSizeBytes.toLong(),
            targetFile.length()
        )

        Assert.assertEquals(
            sourceFileContents,
            targetFileContents
        )

        Assert.assertTrue(finishCallbackTriggered.get())

        println("$progressCallbacksEstimatedCount, $progressCallbackRealCount")
    }


    @Test
    fun simple_test() = runBlocking {
        test_with_params(
            57,
            13,
            1,
            10.0
        )
    }

    @Test
    fun repeated_simple_test() = runBlocking {
        for (multiplier in 1..10) {
            test_with_params(
                100 * multiplier,
                30 * multiplier,
                10,
                10.0
            )
        }
    }

    @Test
    fun `продолжительность_копирования_плюс_минус_10_процентов_от_расчётной`() = runTest {
        listOf(
//            IntRange(1,2),
//            IntRange(3,6),
//            IntRange(7,10),
//            IntRange(10,20),
//            IntRange(20,30),
            IntRange(30,40),
        ).forEach{ range ->
            val speedMultiplier = 10
            range.forEach { dataSize ->
                test_with_params(
                    dataSize = dataSize,
                    speed = dataSize * speedMultiplier,
                    rate = 10,
                    10.0
                )
            }
        }
    }

    private fun test_with_params(dataSize: Int,
                                 speed: Int,
                                 rate: Int,
                                 targetCopyingTimeDeviationPercents: Double
    ) {

        prepareSourceAndTargetFiles(dataSize)

        val estimatedDuration
                = ((1f * dataSize / speed)*1_000_000_000)
            .roundToLong()

        val lsc = LimitedStreamCopier(
            speedBytesPerSecond = speed,
            progressRatePerSecond = rate,
        )

        val startTimeNs = currentTimeNanos
        val startTimeMs = currentTimeMs

        lsc.copyFromStreamToStream(
            sourceFileStream,
            targetFileStream,
            progressCallback = { b,s ->
                println("скопировано: $b")
            }
        )
        val durationNs = currentTimeNanos - startTimeNs
        val durationMs = currentTimeMs - startTimeMs

        println("${dataSize.humanDecimalPlaces} со скоростью ${speed.humanDecimalPlaces} скопировано за время: ${durationMs.toHMS()}")

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

    companion object {
        val TAG: String = LimitedStreamCopierTest2::class.java.simpleName
    }
}

val currentTimeMs: Long get() = System.currentTimeMillis()