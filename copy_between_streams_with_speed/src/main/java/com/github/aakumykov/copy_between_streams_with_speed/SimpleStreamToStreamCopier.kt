package com.github.aakumykov.copy_between_streams_with_speed

import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

class SimpleStreamToStreamCopier {

    fun copyFromStreamToStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        speedBytesPerSecond: Int,
        progressRatePerSecond: Int = 1,
        progressCallback: ((byteTransferred: Long) -> Unit)? = null,
        finishCallback: ((byteTransferred: Long) -> Unit)? = null
    ) {
        require(speedBytesPerSecond > 0) {
            "Speed must be greater then zero ($speedBytesPerSecond)."
        }
        require(progressRatePerSecond > 0) {
            "Интервал прогресса не может быть отрицательным"
        }

        val bufferSize = DEFAULT_BUFFER_SIZE
        val dataBuffer = ByteArray(bufferSize)
        val progressIntervalNs = 1000f / progressRatePerSecond * 1000_000
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
                progressCallback?.invoke(totalBytesWritten)
                lastProgressTimeNs = System.nanoTime()
            }
        }

        outputStream.flush()
        progressCallback?.invoke(totalBytesWritten)
        finishCallback?.invoke(totalBytesWritten)
    }
}