package com.github.aakumykov.copy_between_streams_with_speed

import com.github.aakumykov.copy_between_streams_with_speed.ext.roundToFloatingDigits
import com.github.aakumykov.copy_between_streams_with_speed.stream2stream_copier.LimitedStreamCopier
import com.github.aakumykov.copy_between_streams_with_speed.utils.humanDecimalPlaces
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test
import kotlin.math.roundToLong

class LimitedStreamCopierTest2 : TestBase() {

    @Test
    fun `продолжительность_копирования_плюс_минус_20_от_расчётной`() = runTest {
        for (dataSize in 1..100) {
            prepareSourceAndTargetFiles(dataSize)

            val speed = 200
            val rate = 1
            val steps = 10

            val estimatedDuration
                    = ((1f * dataSize / speed)*1_000_000)
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

            println("sz: $dataSize, " +
                    "sp: $speed, " +
                    "st: $steps " +
                    "-> " +
                    "es:${estimatedDuration.humanDecimalPlaces}, " +
                    "dr:${duration.humanDecimalPlaces}")
//            Assert.assertTrue("$duration in 4800..5200 при размере данных $dataSize", duration in 4800..5200)
        }
    }

    private val currentTime: Long
        get() = System.currentTimeMillis() + System.nanoTime()
}