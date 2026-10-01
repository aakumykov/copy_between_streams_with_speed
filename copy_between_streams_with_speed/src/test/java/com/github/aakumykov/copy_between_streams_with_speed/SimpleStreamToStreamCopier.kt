package com.github.aakumykov.copy_between_streams_with_speed

import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.min

class SimpleStreamToStreamCopier {

    fun copy(
        inputStream: InputStream,
        outputStream: OutputStream,
        speedBytesPerSecond: Int,
        progressIntervalMs: Long = 1000,
        onProgress: ((byteTransferred: Long) -> Unit)? = null
    ) {
        require(speedBytesPerSecond > 0) { "Speed must be greater then zero ($speedBytesPerSecond)." }
        require(progressIntervalMs >= 0) { "Интервал прогресса не может быть отрицательным" }

        val bufferSize = min(speedBytesPerSecond, DEFAULT_BUFFER_SIZE)
        val dataBuffer = ByteArray(bufferSize)
        val progressIntervalNs = progressIntervalMs * 1000_000
        val startTime = System.nanoTime()

        var totalBytesWritten: Long = 0
        var lastProgressTimeNs = System.nanoTime()

        while (true) {
            val readBytes = inputStream.read(dataBuffer)
            if (-1 == readBytes) break

            outputStream.write(dataBuffer, 0, readBytes)
            totalBytesWritten += readBytes

            val expectedNanos = (totalBytesWritten.toDouble() * 1_000_000_000.0 / speedBytesPerSecond).toLong()
            val actualNanos = System.nanoTime() - startTime
            val delayNanos = expectedNanos - actualNanos

            if (delayNanos > 0) {
                TimeUnit.NANOSECONDS.sleep(expectedNanos - actualNanos)
            }

            if ((System.nanoTime() - lastProgressTimeNs) > progressIntervalNs) {
                onProgress?.invoke(totalBytesWritten)
                lastProgressTimeNs = System.nanoTime()
            }
        }

        outputStream.flush()
        onProgress?.invoke(totalBytesWritten)
    }
}