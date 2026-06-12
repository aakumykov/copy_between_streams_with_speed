package com.github.aakumykov.copy_between_streams_with_speed

import java.io.InputStream
import java.io.OutputStream
import kotlin.math.roundToLong

/**
 * @param inputStream
 * @param outputStream
 * @param speedBytesPerSec Скорость в байт/с. Не ограничена, если меньше или равна нулю.
 * @param dataTransferStepsPerSecond Количество шагов в секунду, за которое нужно передать данные.
 * Это количество раз в секунду будет вызван [progressCallback].
 * Количество выдерживается не на 100% строго.
 * @param progressCallback
 * @param finishCallback
 */
@Throws(IllegalArgumentException::class)
fun copyBetweenStreamsWithSpeed(
    inputStream: InputStream,
    outputStream: OutputStream,
    speedBytesPerSec: Int = -1,
    dataTransferStepsPerSecond: Int = 100,
    progressCallback: ((transferredBytes:Long, speedBytesPerSec:Long) -> Unit)? = null,
    finishCallback: ((transferredBytes:Long, timeElapsedMs:Long, speedBytesPerSec:Long) -> Unit)? = null,
) {
    val isSpeedLimited: Boolean = speedBytesPerSec > 0

    if (isSpeedLimited && dataTransferStepsPerSecond > speedBytesPerSec)
        throw IllegalArgumentException("dataTransferStepsPerSecond ($dataTransferStepsPerSecond) cannot be grater than speedBytesPerSec ($speedBytesPerSec)")

    val timeForStepMs = 1000 / dataTransferStepsPerSecond
    val dataSizeForStepBytes = if (isSpeedLimited) (speedBytesPerSec / dataTransferStepsPerSecond) else DEFAULT_BUFFER_SIZE
    val copyingPieceSizeBytes = dataSizeForStepBytes.let { if (it > DEFAULT_BUFFER_SIZE) DEFAULT_BUFFER_SIZE else it }

    var bytesCopiedTotal: Long = 0
    var bytesCopiedForStep: Long = 0
    var readBytes: Int
    val buffer = ByteArray(copyingPieceSizeBytes)

    val fullCopyingStartTimeMs = System.currentTimeMillis()

    while (true) {
        val stepStartTimeMs = System.currentTimeMillis()

        readBytes = inputStream.read(buffer, 0, copyingPieceSizeBytes)
        if (-1 == readBytes) break
        outputStream.write(readBytes)

        bytesCopiedForStep += readBytes
        bytesCopiedTotal += readBytes

        if (bytesCopiedForStep >= dataSizeForStepBytes) {

            val bytesOverrunPercentage: Float = (bytesCopiedForStep.toFloat() / dataSizeForStepBytes)

            val stepFinishTimeMs = System.currentTimeMillis()
            val stepDurationMs = stepFinishTimeMs - stepStartTimeMs
            val sleepingLackTimeMs = (bytesOverrunPercentage * timeForStepMs - stepDurationMs).roundToLong()
            if (sleepingLackTimeMs > 0) {
                Thread.sleep(sleepingLackTimeMs)
            }

            // FIXME: вместо stepTimeMs должно быть stepDurationMs
            val stepSpeedBytesPerSec:Long = (bytesCopiedForStep.toFloat() / stepFinishTimeMs).roundToLong()
            progressCallback?.invoke(bytesCopiedTotal, stepSpeedBytesPerSec)

            bytesCopiedForStep = 0
        }
    }

    val realCopyingDurationMs: Long = System.currentTimeMillis() - fullCopyingStartTimeMs

    val realSpeedBytesPerSec: Long = if (0L == bytesCopiedTotal) 0L
    else (bytesCopiedTotal / (realCopyingDurationMs.toFloat()/1000)).roundToLong()

    finishCallback?.invoke(
        bytesCopiedTotal,
        realCopyingDurationMs,
        realSpeedBytesPerSec
    )
}

