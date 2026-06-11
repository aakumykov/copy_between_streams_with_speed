package com.github.aakumykov.copy_between_streams_with_speed

import java.io.InputStream
import java.io.OutputStream
import kotlin.math.roundToLong

fun copyBetweenStreamsWithSpeed2(
    inputStream: InputStream,
    outputStream: OutputStream,
    speedBytesPerSec: Int = -1,
    dataTransferStepsPerSecond: Int = 100,
    progressCallback: ((transferredBytes:Long, speedBytesPerSec:Long) -> Unit)? = null,
    finishCallback: ((transferredBytes:Long, timeElapsedMs:Long, speedBytesPerSec:Long) -> Unit)? = null,
) {
    val isSpeedLimited: Boolean = -1 != speedBytesPerSec

    val timeForStepMs = 1000 / dataTransferStepsPerSecond
    val dataSizeForStepBytes = if (isSpeedLimited) (speedBytesPerSec / dataTransferStepsPerSecond) else DEFAULT_BUFFER_SIZE
    val copyingPieceSizeBytes = dataSizeForStepBytes.let { if (it > DEFAULT_BUFFER_SIZE) DEFAULT_BUFFER_SIZE else it }

    var bytesCopiedTotal: Long = 0
    var bytesCopiedForStep: Long = 0
    var readBytes: Int
    val buffer = ByteArray(copyingPieceSizeBytes)

    val fullCopyingStartTimeMs = System.currentTimeMillis()

    while (true) {
        val stepStartTime = System.currentTimeMillis()

        readBytes = inputStream.read(buffer, 0, copyingPieceSizeBytes)
        if (-1 == readBytes) break
        outputStream.write(readBytes)

        bytesCopiedForStep += readBytes
        bytesCopiedTotal += readBytes

        if (isSpeedLimited && bytesCopiedForStep >= dataSizeForStepBytes) {

            val bytesOverrunPercentage: Float = (bytesCopiedForStep.toFloat() / dataSizeForStepBytes)

            val stepTimeMs = System.currentTimeMillis()
            val stepDurationMs = stepTimeMs - stepStartTime
            val sleepingLackTimeMs = (bytesOverrunPercentage * timeForStepMs - stepDurationMs).roundToLong()
            if (sleepingLackTimeMs > 0) {
                Thread.sleep(sleepingLackTimeMs)
            }

            // FIXME: вместо stepTimeMs должно быть stepDurationMs
            val stepSpeedBytesPerSec:Long = (bytesCopiedForStep.toFloat() / stepTimeMs).roundToLong()
            progressCallback?.invoke(bytesCopiedTotal, stepSpeedBytesPerSec)

            bytesCopiedForStep = 0
        }
    }

    val realCopyingDurationMs: Long = System.currentTimeMillis() - fullCopyingStartTimeMs

    val realSpeedBytesPerSec: Long = (
            bytesCopiedTotal / (realCopyingDurationMs.toFloat()/1000)
    ).roundToLong()

    finishCallback?.invoke(
        bytesCopiedTotal,
        realCopyingDurationMs,
        realSpeedBytesPerSec
    )
}

