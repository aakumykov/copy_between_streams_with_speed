package com.github.aakumykov.copy_between_streams_with_speed

import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.roundToLong

class SimpleStreamToStreamCopier {

    fun copyFromStreamToStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        speedBytesPerSecond: Int,
        progressRatePerSecond: Int = 1,
        finishCallback: ((byteTransferred: Long) -> Unit)? = null,
        progressCallback: ((byteTransferred: Long, speedBytesPerSecond: Long) -> Unit)? = null
    ) {
        require(speedBytesPerSecond > 0) {
            "Speed must be greater then zero ($speedBytesPerSecond)."
        }
        require(progressRatePerSecond > 0) {
            "Интервал прогресса не может быть отрицательным"
        }

        val bufferSize = min(speedBytesPerSecond, DEFAULT_BUFFER_SIZE)
        val dataBuffer = ByteArray(bufferSize)
        val progressIntervalNs = 1000f / progressRatePerSecond * 1000_000
        val startTime = System.nanoTime()

        var totalBytesWritten: Long = 0
        var lastProgressTimeNs = System.nanoTime()

        while (true) {
            val readBytes = inputStream.read(dataBuffer)

            if (-1 == readBytes) {
                break
            }

            outputStream.write(dataBuffer, 0, readBytes)

            totalBytesWritten += readBytes

            val expectedNanos = (totalBytesWritten.toDouble() * NANOS_IN_SECOND / speedBytesPerSecond).toLong()
            val actualNanos = System.nanoTime() - startTime
            val delayNanos = expectedNanos - actualNanos

            if (delayNanos > 0) {
                val seconds = delayNanos / NANOS_IN_SECOND
                TimeUnit.NANOSECONDS.sleep(delayNanos)
            }

            val durationWithSleep = System.nanoTime() - startTime
            val speedBytesPerSecond = (NANOS_IN_SECOND * totalBytesWritten / durationWithSleep).roundToLong()

            if ((System.nanoTime() - lastProgressTimeNs) > progressIntervalNs) {
                progressCallback?.invoke(totalBytesWritten, speedBytesPerSecond)
                lastProgressTimeNs = System.nanoTime()
            }
        }

        outputStream.flush()

        val finalDuration = System.nanoTime() - startTime
        val speedBytesPerSecond = (NANOS_IN_SECOND * totalBytesWritten / finalDuration).roundToLong()

        progressCallback?.invoke(totalBytesWritten, speedBytesPerSecond)
        finishCallback?.invoke(totalBytesWritten)
    }

    companion object {
        const val NANOS_IN_SECOND: Double = 1_000_000_000.0
        const val MILLIS_IN_SECOND: Int = 1_000
    }
}