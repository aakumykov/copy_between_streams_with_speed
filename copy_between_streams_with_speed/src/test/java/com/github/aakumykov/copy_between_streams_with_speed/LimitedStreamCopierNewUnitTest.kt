package com.github.aakumykov.copy_between_streams_with_speed

import com.github.aakumykov.copy_between_streams_with_speed.ext.roundToFloatingDigits
import org.junit.Test

class LimitedStreamCopierNewUnitTest : StreamCopierTestBase() {

    @Test
    fun a1() {
        val dataSizeBytes = 1024 * 1024 * 5
        val speedBytesPerSec = 512 * 1024
        val stepsPerSec = 10

        val expectedDurationMs = 1000.toDouble() * dataSizeBytes / speedBytesPerSec

        prepareSourceAndTargetFiles(dataSizeBytes)

        val startTime = System.currentTimeMillis()
        LimitedStreamCopierNew().copyFromStreamToStream(
            sourceFileStream,
            targetFileStream,
            speedBytesPerSec,
            stepsPerSecond = stepsPerSec
        )
        val duration = System.currentTimeMillis() - startTime
        val durationPercent = (100 * duration / expectedDurationMs).roundToFloatingDigits(5)
        println( "Время копирования: $duration / $expectedDurationMs (${durationPercent}%)")
    }
}