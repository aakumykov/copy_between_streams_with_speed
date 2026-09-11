package com.github.aakumykov.copy_between_streams_with_speed

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.cancel
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.roundToInt
import kotlin.math.roundToLong


class LimitedStreamCopier(
    private val coroutineScope: CoroutineScope,
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO,
): BasicStreamCopier() {

    private var currentJob: Job? = null

    /**
     * @param inputStream
     * @param outputStream
     * @param speedBytesPerSecond
     * @param stepsPerSecond Не может быть больше, чем [speedBytesPerSecond].
     */
    @OptIn(FlowPreview::class)
    @Throws(IllegalStateException::class, IllegalArgumentException::class, IOException::class)
    override suspend fun copyFromStreamToStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        speedBytesPerSecond: Int, // TODO: сделать Long
        stepsPerSecond: Int
    ) {
        if (null != currentJob) // TODO: тестировать
            throw IllegalStateException("Current job field is not null, seems job is running...")

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

        // Любопытно: бзагодаря apply { join() } в конце код спасён от преждевременного завершения
        // с закрытием потока до завершения копирования, но переменная currentJob получает значение
        // тоже после фактического завершения работы "корутиной". В итоге после завершения работы,
        // когда currentJob должна стать null, она наоборот становится не-null.
        coroutineScope.launch (coroutineDispatcher) {

            while(true) {
                val readBytes = inputStream.read(dataBuffer, 0, operatingPortionSize)

                // Данные закончились.
                if (-1 == readBytes) {
                    currentJob?.cancel(CancellationException("Reached end of copying data."))
                    currentJob = null
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

        }.apply {
            currentJob = this
            join()
        }
    }
}
