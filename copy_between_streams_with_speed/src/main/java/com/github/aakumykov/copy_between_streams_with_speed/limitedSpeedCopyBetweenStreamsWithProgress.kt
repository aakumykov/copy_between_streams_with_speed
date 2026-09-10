package com.github.aakumykov.copy_between_streams_with_speed

import kotlinx.coroutines.FlowPreview
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.roundToInt
import kotlin.math.roundToLong


class LimitedStreamCopier: BasicStreamCopier() {

    /**
     * @param inputStream
     * @param outputStream
     * @param speedBytesPerSecond
     * @param stepsPerSecond Не может быть больше, чем [speedBytesPerSecond].
     */
    @OptIn(FlowPreview::class)
    @Throws(IllegalArgumentException::class, IOException::class)
    override suspend fun copyFromStreamToStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        speedBytesPerSecond: Int, // TODO: сделать Long
        stepsPerSecond: Int
    ) {

        if (speedBytesPerSecond <= 0)
            throw IllegalArgumentException("Speed must be greater than zero.")

        if (stepsPerSecond > speedBytesPerSecond)
            throw IllegalArgumentException("StepsPerSecond cannot be greater than speedBytesPerSecond.")

        val timeForStepMs = (1000F / stepsPerSecond).roundToLong()
        val dataSizeToBeCopiedByStep = (1f * speedBytesPerSecond / stepsPerSecond).roundToInt()
        // Если размер данных, который нужно скопировать за один шаг, больше размера буфера,
        // черпаю данные меньшим объёмом.
        val operatingPortionSize = if (dataSizeToBeCopiedByStep > DEFAULT_BUFFER_SIZE) DEFAULT_BUFFER_SIZE else dataSizeToBeCopiedByStep

        val dataBuffer = ByteArray(operatingPortionSize)
        var totalDataRead: Long = 0
        var thisStepDataRead: Long = 0

        while(true) {
            val readBytes = inputStream.read(dataBuffer, 0, operatingPortionSize)

            // Данные закончились.
            if (-1 == readBytes) {
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
                publishProgress(totalDataRead)
            }
            else if (readBytes < dataSizeToBeCopiedByStep) {
                publishProgress(totalDataRead)
            }
            else if (thisStepDataRead >= dataSizeToBeCopiedByStep) {
                publishProgress(totalDataRead)
                thisStepDataRead = 0
            }
        }
    }
}
