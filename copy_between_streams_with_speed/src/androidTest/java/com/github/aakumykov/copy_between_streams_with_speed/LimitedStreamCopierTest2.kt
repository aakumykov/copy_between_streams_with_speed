package com.github.aakumykov.copy_between_streams_with_speed

import com.github.aakumykov.copy_between_streams_with_speed.stream2stream_copier.LimitedStreamCopier
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test

class LimitedStreamCopierTest2 : TestBase() {

    @Test
    fun `продолжительность_копирования_плюс_минус_20_от_расчётной`() = runTest {
        val dataSize = 1000
        prepareSourceAndTargetFiles(dataSize)

        val lsc = LimitedStreamCopier(
            speedBytesPerSecond = 200,
            progressRatePerSecond = 1,
            dataCopyStepsPerSecond = 10
        )

        val startTime = System.currentTimeMillis()
        lsc.copyFromStreamToStream(
            sourceFileStream,
            targetFileStream
        )
        val duration = System.currentTimeMillis() - startTime

        Assert.assertTrue("$duration in 4800..5200", duration in 4800..5200)
    }
}