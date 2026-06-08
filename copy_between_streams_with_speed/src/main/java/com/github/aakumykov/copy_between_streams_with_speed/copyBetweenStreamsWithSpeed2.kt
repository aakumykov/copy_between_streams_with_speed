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

    val timeForStep = 1000 / dataTransferStepsPerSecond
    val dataSizeForStep = if (isSpeedLimited) (speedBytesPerSec / dataTransferStepsPerSecond) else DEFAULT_BUFFER_SIZE
    val copyingPieceSize = dataSizeForStep.let { if (it > DEFAULT_BUFFER_SIZE) DEFAULT_BUFFER_SIZE else it }

    var bytesCopiedTotal: Long = 0
    var bytesCopiedForStep: Long = 0
    var readBytes: Int
    val buffer = ByteArray(copyingPieceSize)

    val fullCopyingStartTime = System.currentTimeMillis()

    while (true) {
        val stepStartTime = System.currentTimeMillis()

        readBytes = inputStream.read(buffer, 0, copyingPieceSize)
        if (-1 == readBytes) break
        outputStream.write(readBytes)

        bytesCopiedForStep += readBytes
        bytesCopiedTotal += readBytes

        if (isSpeedLimited && bytesCopiedForStep >= dataSizeForStep) {

            val bytesOverrunPercentage: Float = (bytesCopiedForStep.toFloat() / dataSizeForStep)

            val stepTime = System.currentTimeMillis()
            val stepDuration = stepTime - stepStartTime
            val sleepingLackTime = (bytesOverrunPercentage * timeForStep - stepDuration).roundToLong()
            if (sleepingLackTime > 0) {
                Thread.sleep(sleepingLackTime)
            }

            val stepSpeedBytesPerSec:Long = (bytesCopiedForStep.toFloat() / stepTime).roundToLong()
            progressCallback?.invoke(bytesCopiedTotal, stepSpeedBytesPerSec)

            bytesCopiedForStep = 0
        }
    }

    val realCopyingDurationMs: Long = System.currentTimeMillis() - fullCopyingStartTime

    val realSpeedBytesPerSec: Long = (
            bytesCopiedTotal / (realCopyingDurationMs.toFloat()/1000)
    ).roundToLong()

    finishCallback?.invoke(
        bytesCopiedTotal,
        realCopyingDurationMs,
        realSpeedBytesPerSec
    )
}

