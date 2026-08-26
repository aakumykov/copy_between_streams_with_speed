package com.github.aakumykov.copy_between_streams_with_speed

import java.io.InputStream
import java.io.OutputStream
import kotlin.math.roundToLong

/**
 * @param inputStream
 * @param outputStream
 * @param speedBytesPerSec Скорость в байт/с. Не ограничена, если меньше или равна нулю.
 * @param bufferSizeMultiplierForSpeedCompensation Увеличитель размера буфера копирования.
 * Позволяет компенсировать просадку скорости при её подсчёте. В указанное количество
 * раз увеличивается [DEFAULT_BUFFER_SIZE].
 * @param stepsPerSecond Количество шагов в секунду, за которое нужно передать данные.
 * Не может быть больше [speedBytesPerSec].
 * Это количество раз в секунду будет вызван [progressCallback].
 * Количество выдерживается не на 100% строго.
 * @param progressCallback
 * @param finishCallback
 */
@Throws(IllegalArgumentException::class)
// TODO: нужны новые тесты
fun copyBetweenStreamsWithSpeed(
    inputStream: InputStream,
    outputStream: OutputStream,
    speedBytesPerSec: Int = -1,
    bufferSizeMultiplierForSpeedCompensation: Int = 2,
    stepsPerSecond: Int = 100,
    progressCallback: ((transferredBytes:Long, speedBytesPerSec:Long) -> Unit)? = null,
    finishCallback: ((transferredBytes:Long, timeElapsedMs:Long, speedBytesPerSec:Long) -> Unit)? = null,
) {
    val speedIsLimited: Boolean = speedBytesPerSec > 0

    val bufferSize = bufferSizeMultiplierForSpeedCompensation * DEFAULT_BUFFER_SIZE

    if (speedIsLimited) {
        if (stepsPerSecond > speedBytesPerSec) {
            throw IllegalArgumentException("Steps per second ($stepsPerSecond) cannot be greater than speed bytes per second ($speedBytesPerSec)")
        }
    }

    if (0 == speedBytesPerSec)
        throw IllegalArgumentException("Speed cannot be zero")

    val timeForStepMs = if (speedIsLimited) {
        1000 / stepsPerSecond } else { 0 }

    val dataSizeForStepBytes = if (speedIsLimited) (speedBytesPerSec / stepsPerSecond) else bufferSize
    val copyingPieceSizeBytes = dataSizeForStepBytes.let { if (it > bufferSize) bufferSize else it }

    var bytesCopiedTotal: Long = 0
    var bytesCopiedThisStep: Long = 0
    var readBytesCount: Int
    val dataBuffer = ByteArray(copyingPieceSizeBytes)

    val fullCopyingStartTimeMs = System.currentTimeMillis()

    while (true) {
        val stepStartTimeMs = System.currentTimeMillis()

        readBytesCount = inputStream.read(dataBuffer, 0, copyingPieceSizeBytes)
        if (-1 == readBytesCount) { break }

        outputStream.write(dataBuffer, 0, readBytesCount)

        bytesCopiedThisStep += readBytesCount
        bytesCopiedTotal += readBytesCount

        if (bytesCopiedThisStep >= dataSizeForStepBytes) {

            val bytesOverrunPercentage: Float = (bytesCopiedThisStep.toFloat() / dataSizeForStepBytes)

            val stepFinishTimeMs = System.currentTimeMillis()
            val stepDurationMs = stepFinishTimeMs - stepStartTimeMs
            val sleepingLackTimeMs = (bytesOverrunPercentage * timeForStepMs - stepDurationMs).roundToLong()
            if (sleepingLackTimeMs > 0) {
//                Log.d("copyBetweenStreamsWithSpeed", "sleeping $sleepingLackTimeMs ms")
                Thread.sleep(sleepingLackTimeMs)
            }

            // FIXME: вместо stepTimeMs должно быть stepDurationMs
            val stepSpeedBytesPerSec:Long = (bytesCopiedThisStep.toFloat() / stepDurationMs).roundToLong()
            progressCallback?.invoke(bytesCopiedTotal, stepSpeedBytesPerSec)

            bytesCopiedThisStep = 0
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

