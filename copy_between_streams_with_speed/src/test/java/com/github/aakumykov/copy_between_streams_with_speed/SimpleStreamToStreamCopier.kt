package com.github.aakumykov.copy_between_streams_with_speed

import java.io.InputStream
import java.io.OutputStream

class SimpleStreamToStreamCopier {

    fun copyWithRateLimitAndProgress(
        input: InputStream,
        output: OutputStream,
        bytesPerSecond: Long,
        progressIntervalMs: Long = 1000L,
        bufferSize: Int = 8192,
        onProgress: (bytesTransferred: Long) -> Unit
    ) {
        require(bytesPerSecond > 0) { "Скорость должна быть больше нуля" }
        require(progressIntervalMs >= 0) { "Интервал прогресса не может быть отрицательным" }

        val buffer = ByteArray(bufferSize)
        val startTime = System.nanoTime()
        var totalBytesRead = 0L
        var lastProgressTimeNs = 0L
        val progressIntervalNs = progressIntervalMs * 1_000_000L

        while (true) {
            val bytesRead = input.read(buffer)
            if (bytesRead == -1) break

            output.write(buffer, 0, bytesRead)
            totalBytesRead += bytesRead

            val expectedNanos = (totalBytesRead.toDouble() * 1_000_000_000.0 / bytesPerSecond).toLong()
            val actualNanos = System.nanoTime() - startTime
            val delayNanos = expectedNanos - actualNanos

            if (delayNanos > 0) {
                val millis = delayNanos / 1_000_000
                val nanos = (delayNanos % 1_000_000).toInt()
                Thread.sleep(millis, nanos)
            }

            val nowNs = System.nanoTime()
            if (nowNs - lastProgressTimeNs >= progressIntervalNs) {
                lastProgressTimeNs = nowNs
                onProgress(totalBytesRead)
            }
        }

        onProgress(totalBytesRead)
        output.flush()
    }
}