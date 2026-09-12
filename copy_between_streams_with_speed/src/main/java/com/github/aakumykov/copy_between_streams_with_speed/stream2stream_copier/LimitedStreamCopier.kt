package com.github.aakumykov.copy_between_streams_with_speed.stream2stream_copier

import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * @param initialSpeedBytesPerSecond
 * @param stepsPerSecond Не может быть больше, чем [initialSpeedBytesPerSecond].
 */
class LimitedStreamCopier(
    private val initialSpeedBytesPerSecond: Int, // TODO: сделать Long
    private val stepsPerSecond: Int = 10
): Stream2StreamCopier {

    private var speedBytesPerSecond: Int = initialSpeedBytesPerSecond

    // get() для динамического изменения скорости (получится ли?)
    private val dataSizeToBeCopiedByStep: Int
        get() = (1f * speedBytesPerSecond / stepsPerSecond).roundToInt()

    // Если размер данных, который нужно скопировать за один шаг, больше размера буфера,
    // черпаю данные меньшим объёмом.
    private val operatingPortionSize =
        if (dataSizeToBeCopiedByStep > DEFAULT_BUFFER_SIZE) DEFAULT_BUFFER_SIZE
        else dataSizeToBeCopiedByStep

    private val timeForStepMs: Long = (1000F / stepsPerSecond).roundToLong()

    private val dataBuffer = ByteArray(operatingPortionSize)


    @Throws(IllegalStateException::class, IllegalArgumentException::class, IOException::class)
    override fun copyFromStreamToStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        progressCallback: ((transferredBytes: Long) -> Unit)?,
        progressCallbackRatePerSecond: Int,
        finishCallback: ((transferredBytes: Long) -> Unit)?,
    ) {
        val minimumProgressCallbackPeriodMs = (1000f / progressCallbackRatePerSecond).roundToLong()
        var lastProgressPublishTimeMs: Long = 0

        fun publishProgressIfItsTime(totalDataRead: Long, force: Boolean = false) {
            val interval = System.currentTimeMillis() - lastProgressPublishTimeMs
            if (interval >= minimumProgressCallbackPeriodMs || force) {
                progressCallback?.invoke(totalDataRead)
                lastProgressPublishTimeMs = System.currentTimeMillis()
            }
        }

        fun sleepIfNeeded(
            stepDurationMs: Long,
            timeAllocatedForStep: Long,
            bytesRealCopiedInStep: Long,
            bytesNeedToBeCopiedInStep: Long
        ) {
            if (bytesRealCopiedInStep >= bytesNeedToBeCopiedInStep) {

                val bytesOverrunPercentage: Float = (bytesRealCopiedInStep.toFloat() / bytesNeedToBeCopiedInStep)

                val sleepingLackTimeMs = (bytesOverrunPercentage * timeAllocatedForStep - stepDurationMs).roundToLong()

                if (sleepingLackTimeMs > 0) {
                    Thread.sleep(sleepingLackTimeMs)
                }
            }
        }

        if (initialSpeedBytesPerSecond <= 0)
            throw IllegalArgumentException("Speed must be greater than zero.")

        if (stepsPerSecond > speedBytesPerSecond)
            throw IllegalArgumentException("StepsPerSecond cannot be greater than speedBytesPerSecond.")

        var totalDataRead: Long = 0
        var thisStepDataRead: Long = 0

        publishProgressIfItsTime(0, true)

        while(true) {
            val startTime = System.currentTimeMillis()

            val readBytes = inputStream.read(dataBuffer, 0, operatingPortionSize)

            // Данные закончились.
            if (-1 == readBytes) {
                finishCallback?.invoke(totalDataRead)
                break
            }

            outputStream.write(dataBuffer, 0, readBytes)

            thisStepDataRead += readBytes
            totalDataRead += readBytes

            // Объёмы данных в порядке уменьшения:
            // Полный размер данных.
            // Размер данных, которые должны быть скопировать за шаг.
            // Размер данных, которыми оперируют в процессе перекидывания данных.

            if (readBytes < operatingPortionSize) {
                publishProgressIfItsTime(totalDataRead, true)
            }
            else if (readBytes < dataSizeToBeCopiedByStep) {
                publishProgressIfItsTime(totalDataRead, true)
            }
            else if (thisStepDataRead >= dataSizeToBeCopiedByStep) {
                publishProgressIfItsTime(totalDataRead)
                sleepIfNeeded(
                    System.currentTimeMillis() - startTime,
                    timeForStepMs,
                    thisStepDataRead,
                    dataSizeToBeCopiedByStep.toLong()
                )
                thisStepDataRead = 0
            }
        }
    }

    override fun setSpeedBytesPerSec(value: Int) {
        if (value >= stepsPerSecond) {
            speedBytesPerSecond = value
        } else {
            Log.w(TAG, "Speed bytes per second ($value) cannot be greater than steps per second ($stepsPerSecond) value.")
        }
    }

    companion object {
        val TAG: String = LimitedStreamCopier::class.java.simpleName
    }
}
