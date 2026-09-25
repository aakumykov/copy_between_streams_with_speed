package com.github.aakumykov.copy_between_streams_with_speed

import com.github.aakumykov.copy_between_streams_with_speed.LimitedStreamCopierMs.Companion.NANOS_IN_SECOND
import com.github.aakumykov.copy_between_streams_with_speed.ext.roundToFloatingDigits
import com.github.aakumykov.copy_between_streams_with_speed.utils.KILOBYTES
import com.github.aakumykov.copy_between_streams_with_speed.utils.MEGABYTES
import com.github.aakumykov.copy_between_streams_with_speed.utils.currentTimeNanos
import com.github.aakumykov.copy_between_streams_with_speed.utils.humanDecimalPlaces
import com.github.aakumykov.copy_between_streams_with_speed.utils.humanSizeBinary
import com.github.aakumykov.copy_between_streams_with_speed.utils.random
import org.junit.Test
import kotlin.math.roundToInt

class LimitedStreamCopierMsUnitTest : StreamCopierTestBase() {

    @Test
    fun test_1mb_with_diff_speeds_in_kb() {
        for(speedBase in 1..100 step 10) {
            val speed = speedBase.KILOBYTES
            doCopy(
                1.MEGABYTES,
                speed,
                1000
            )
        }
        for(speedBase in 100..1001 step 100) {
            val speed = speedBase.KILOBYTES
            doCopy(
                1.MEGABYTES,
                speed,
                1000
            )
        }
    }


    // Как будет адекватно учитываться время,
    // если для копирования 1кб со скоростью
    // 1мб/с требуется 1мс, а минимальная
    // единица внутреннего учёта как раз 1мс?
    @Test
    fun test_1kb_with_1mbs_with_diff_steps() {
        doCopyNanos(1000,
            1_000_000,
            1)

        /*for(steps in 1..1 step 1) {
            doCopyNanos(1.KILOBYTES,
                1.MEGABYTES,
                1000)
        }*/
        /*for(steps in 1..100 step 10) {
            doCopyNanos(1.KILOBYTES,
                1.MEGABYTES,
                steps)
        }
        for(steps in 100..10001 step 100) {
            doCopyNanos(1.KILOBYTES,
                1.MEGABYTES,
                steps)
        }*/
    }


    @Test
    fun test_one_size_with_diff_speeds() {

        for (timeout in 1..10 step 1) {
            val m1 = System.currentTimeMillis()
            val n1 = System.nanoTime()
            Thread.sleep(timeout.toLong())
            val m2 = System.currentTimeMillis()
            val n2 = System.nanoTime()
            val mDiff = m2 - m1
            val nDiff = n2 - n1
            println()
        }

        for (timeout in 10..100 step 10) {
            val m1 = System.currentTimeMillis()
            val n1 = System.nanoTime()
            Thread.sleep(timeout.toLong())
            val m2 = System.currentTimeMillis()
            val n2 = System.nanoTime()
            val mDiff = m2 - m1
            val nDiff = n2 - n1
            println()
        }

        /*val steps = 10
        for (sizeBase in 1 until 101 step 10) {
            val size = sizeBase.KILOBYTES
            println("------------------- size $sizeBase kb ----------------------")
            for (speedBase in 1 until 101 step 10) {
                val speed = speedBase.MEGABYTES
                doCopy(size, speed, steps)
            }
        }*/
    }

    @Test
    fun a() {
        repeat(10) {
            val size = 100.KILOBYTES
            val speed = 2*size
            val steps = 1000
            doCopy(size, speed, steps)
        }
    }

    @Test
    fun b() {
        repeat(10) {
            val size = random.nextInt(1, 10) * 1.KILOBYTES
            val speed = random.nextInt(1, 10) * 1.KILOBYTES
            val steps = 1000
            doCopy(size, speed, steps)
        }
    }

    @Test
    fun anomaly_test_speed_mb() {
        doCopy((6.54 * 1.KILOBYTES).roundToInt(), (2.01 * 1.MEGABYTES).roundToInt(), 10)
        doCopy((232.88 * 1.KILOBYTES).roundToInt(), (8.68 * 1.MEGABYTES).roundToInt(), 10)
        doCopy((320.08 * 1.KILOBYTES).roundToInt(), (1.11 * 1.MEGABYTES).roundToInt(), 10)
    }

    @Test
    fun anomaly_test_speed_kb() {
        doCopy(
            (6.54 * 1.KILOBYTES).roundToInt(),
            (240.00 * 1.KILOBYTES).roundToInt(),
            100)
    }

    @Test
    fun anomaly_6_54() {
        listOf(10,20,30,40,50,60,70,80,90).forEach { base ->
            repeat(10) { i ->
                val speed = 1.KILOBYTES * (i+1) * base
                doCopy((6.54 * 1.KILOBYTES).roundToInt(), speed, 10)
            }
        }
        repeat(10) { i ->
            val speed = 1.KILOBYTES * (i+1) * 100
            doCopy((6.54 * 1.KILOBYTES).roundToInt(), speed, 10)
        }
    }


    private fun doCopy(dataSizeBytes: Int,
                       speedBytesPerSec: Int,
                       stepsPerSec: Int) {

        val expectedDurationMs = 1000.toDouble() * dataSizeBytes / speedBytesPerSec

        prepareSourceAndTargetFiles(dataSizeBytes)

        val startTime = System.currentTimeMillis()
        LimitedStreamCopierMs().copyFromStreamToStream(
            getSourceFileStream,
            getTargetFileStream,
            speedBytesPerSec,
            stepsPerSecond = stepsPerSec
        )
        val duration = System.currentTimeMillis() - startTime
        val durationPercent = (100 * duration / expectedDurationMs).roundToFloatingDigits(5)
        val durationPercentAlert = if (durationPercent >= 150.0) " <----!----" else ""

        val resultMsg = "size:${dataSizeBytes.humanSizeBinary()}, " +
                "speed:${speedBytesPerSec.humanSizeBinary()}/s, " +
                "steps:$stepsPerSec, " +
                "время: ${duration.humanDecimalPlaces} мс / ${expectedDurationMs.humanDecimalPlaces} мс (${durationPercent}%)$durationPercentAlert"

        println(resultMsg)
    }

    private fun doCopyNanos(dataSizeBytes: Int,
                       speedBytesPerSec: Int,
                       stepsPerSec: Int) {

        val expectedDurationSeconds: Double = 1.0 * dataSizeBytes / speedBytesPerSec
        val expectedDurationNanos: Double = expectedDurationSeconds * NANOS_IN_SECOND

        prepareSourceAndTargetFiles(dataSizeBytes)

        val startTime: Long = currentTimeNanos

        LimitedStreamCopierNs().copyFromStreamToStreamNanos(
            getSourceFileStream,
            getTargetFileStream,
            speedBytesPerSec,
            stepsPerSecond = stepsPerSec
        )

        val duration: Long = currentTimeNanos - startTime
        println("in test duration: ${duration.humanDecimalPlaces}")

        val durationPercent: Double = (100 * duration / expectedDurationNanos).roundToFloatingDigits(5)
        val durationPercentAlert = if (durationPercent >= 150.0) " <----!----" else ""

        val resultMsg = "size:${dataSizeBytes.humanSizeBinary()}, " +
                "speed:${speedBytesPerSec.humanSizeBinary()}/s, " +
                "steps:$stepsPerSec, " +
                "время: ${duration.humanDecimalPlaces} нс / ${expectedDurationNanos.humanDecimalPlaces} нс (${durationPercent}%)$durationPercentAlert"

        println(resultMsg)
    }
}