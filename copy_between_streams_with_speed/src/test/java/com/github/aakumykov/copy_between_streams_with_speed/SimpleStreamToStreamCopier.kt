package com.github.aakumykov.copy_between_streams_with_speed

import com.github.aakumykov.copy_between_streams_with_speed.utils.humanDecimalPlaces
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.min

class SimpleStreamToStreamCopier {

    fun copy(
        inputStream: InputStream,
        outputStream: OutputStream,
        speedBytesPerSec: Int,
        progressIntervalMs: Int = 1000,
        onProgress: ((byteTransferred: Long) -> Unit)? = null
    ) {
        val bufferSize = min(speedBytesPerSec, DEFAULT_BUFFER_SIZE)
        val dataBuffer = ByteArray(bufferSize)

        var readBytes: Int
        var totalBytesWritten: Long = 0
        var lastProgressTimeNs = System.nanoTime()

        while (true) {
            readBytes = inputStream.read(dataBuffer)

            if (-1 == readBytes)
                break

            val startTimeNs = System.nanoTime()

            outputStream.write(dataBuffer, 0, readBytes)
            totalBytesWritten += readBytes

            val dataCopyDurationNs = System.nanoTime() - startTimeNs
            val expectedDataCopyDurationNs = (readBytes.toDouble() * 1_000_000_000.0 / speedBytesPerSec).toLong()
//            logD("продолжительность записи $readBytes байт: ${dataCopyDurationNs.humanDecimalPlaces} нс")
            if (dataCopyDurationNs < expectedDataCopyDurationNs) {
                TimeUnit.NANOSECONDS.sleep(expectedDataCopyDurationNs - dataCopyDurationNs)
            }

            if ((System.nanoTime() - lastProgressTimeNs) > progressIntervalMs * 1000_000) {
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