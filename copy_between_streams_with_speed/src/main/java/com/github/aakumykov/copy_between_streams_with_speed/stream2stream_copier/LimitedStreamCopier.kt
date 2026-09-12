package com.github.aakumykov.copy_between_streams_with_speed.stream2stream_copier

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * @param speedBytesPerSecond
 * @param stepsPerSecond Не может быть больше, чем [speedBytesPerSecond].
 */
class LimitedStreamCopier(
    private val speedBytesPerSecond: Int, // TODO: сделать Long
    private val stepsPerSecond: Int = 10
): Stream2StreamCopier {

    @Throws(IllegalStateException::class, IllegalArgumentException::class, IOException::class)
    override fun copyFromStreamToStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        bufferSize: Int,
        progressCallback: ((stepPortionOfData: Long, transferredBytes:Long) -> Unit)?,
        finishCallback: ((transferredBytes:Long) -> Unit)?,
    ) {
        fun publishProgress(stepPortionOfData: Long, totalDataRead: Long) {
            progressCallback?.invoke(stepPortionOfData,totalDataRead)
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

        if (speedBytesPerSecond <= 0)
            throw IllegalArgumentException("Speed must be greater than zero.")

        if (stepsPerSecond > speedBytesPerSecond)
            throw IllegalArgumentException("StepsPerSecond cannot be greater than speedBytesPerSecond.")

        val timeForStepMs = (1000F / stepsPerSecond).roundToLong()

        val dataSizeToBeCopiedByStep = (1f * speedBytesPerSecond / stepsPerSecond).roundToInt()

        // Если размер данных, который нужно скопировать за один шаг, больше размера буфера,
        // черпаю данные меньшим объёмом.
        val operatingPortionSize =
            if (dataSizeToBeCopiedByStep > DEFAULT_BUFFER_SIZE) DEFAULT_BUFFER_SIZE
            else dataSizeToBeCopiedByStep

        val dataBuffer = ByteArray(operatingPortionSize)

        var totalDataRead: Long = 0
        var thisStepDataRead: Long = 0

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
                publishProgress(thisStepDataRead, totalDataRead)
            }
            else if (readBytes < dataSizeToBeCopiedByStep) {
                publishProgress(thisStepDataRead, totalDataRead)
            }
            else if (thisStepDataRead >= dataSizeToBeCopiedByStep) {
                publishProgress(thisStepDataRead, totalDataRead)
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
}
