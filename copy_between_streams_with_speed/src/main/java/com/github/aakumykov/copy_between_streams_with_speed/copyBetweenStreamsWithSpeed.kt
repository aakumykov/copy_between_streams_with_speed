package com.github.aakumykov.copy_between_streams_with_speed

import java.io.InputStream
import java.io.OutputStream
import kotlin.math.roundToInt
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
    progressCallback: ((transferredBytes:Long, speedBytesPerSec:Long) -> Unit)? = null,
    finishCallback: ((transferredBytes:Long, timeElapsedMs:Long, speedBytesPerSec:Long) -> Unit)? = null,
) {
    // Задаю жёстко для упрощения.
    val STEPS_PER_SECOND = 100

    val speedIsLimited: Boolean = speedBytesPerSec > 0

    if (speedIsLimited) {
        if (STEPS_PER_SECOND > speedBytesPerSec) {
            throw IllegalArgumentException("Steps per second ($STEPS_PER_SECOND) cannot be greater than speed bytes per second ($speedBytesPerSec)")
        }
    }

    if (0 == speedBytesPerSec)
        throw IllegalArgumentException("Speed cannot be zero")

    val timeForStepMs: Int = if (speedIsLimited) (1000f / STEPS_PER_SECOND).roundToInt() else 0

    // Размер данных, который следует "обрабатывать" за один шаг.
    // Если скорость задана, рассчитывается, исходя из скорости, иначе принимается равным DEFAULT_BUFFER_SIZE.
    val dataSizeForStep: Int = if (speedIsLimited) (1f * speedBytesPerSec / STEPS_PER_SECOND).roundToInt() else DEFAULT_BUFFER_SIZE

    // Порциями какого размера данные будут читаться из входного потока.
    // Если (при большой заданной скорости) [dataSizeForStep] больше размера буфера,
    // черпаем ковшичком, равным размеру стандартного буфера, если меньше, этим размером.
    val dataReadingPieceSize = if (dataSizeForStep > DEFAULT_BUFFER_SIZE) DEFAULT_BUFFER_SIZE else dataSizeForStep

    var bytesCopiedTotal: Long = 0
    var bytesCopiedThisStep: Long = 0
    var readBytesCount: Int
    val dataBuffer = ByteArray(dataReadingPieceSize)

    val fullCopyingStartTimeMs = System.currentTimeMillis()

    while (true) {
        val stepStartTimeMs = System.currentTimeMillis()

        readBytesCount = inputStream.read(dataBuffer, 0, dataReadingPieceSize)
        if (-1 == readBytesCount) {
            progressCallback?.invoke(-1, -1)
            break
        }

        outputStream.write(dataBuffer, 0, readBytesCount)

        bytesCopiedThisStep += readBytesCount
        bytesCopiedTotal += readBytesCount

        if (bytesCopiedThisStep >= dataSizeForStep) {

            val bytesOverrunPercentage: Float = (bytesCopiedThisStep.toFloat() / dataSizeForStep)

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

