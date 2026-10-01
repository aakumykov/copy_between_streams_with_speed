package com.github.aakumykov.copy_between_streams_with_speed

import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

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

        val bufferSize = DEFAULT_BUFFER_SIZE //min(speedBytesPerSec, DEFAULT_BUFFER_SIZE)
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
//            logD("продолжительность записи $readBytes байт: ${dataCopyDurationNs.humanDecimalPlaces} нс")

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


    fun copyWithRateLimitAndProgressAI(
        input: InputStream,
        output: OutputStream,
        bytesPerSecond: Int,
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