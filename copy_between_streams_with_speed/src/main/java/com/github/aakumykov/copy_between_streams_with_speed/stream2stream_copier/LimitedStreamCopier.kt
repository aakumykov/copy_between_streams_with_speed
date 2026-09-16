package com.github.aakumykov.copy_between_streams_with_speed.stream2stream_copier

import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * @param progressRatePerSecond Частота срабатывания коллбека прогресса.
 */
class LimitedStreamCopier(
    private val speedBytesPerSecond: Int,
    private val progressRatePerSecond: Int, // TODO: перенести в функцию?
): Stream2StreamCopier {

    init {
        if (speedBytesPerSecond <= 0)
            throw IllegalArgumentException("Speed must be greater than zero.")

        if (progressRatePerSecond <= 0)
            throw IllegalArgumentException("progressRatePerSecond cannot be zero")

        if (progressRatePerSecond > speedBytesPerSecond)
            throw IllegalArgumentException("progress rate cannot be greater than speed")
    }

    //
    // Скорость может быть задана огромная, параметр "количество данных, которые должны быть
    // переданы за шаг [steps]", потенциально (но не всегда!) самый большой.
    //
    // get() - для динамического изменения скорости
    //
    private val dataSizeToBeCopiedByStep: Int
        get() = (1f * speedBytesPerSecond / progressRatePerSecond).roundToInt()

    //
    // Оперирую данными (черпаю данные) в размере, равном размеру данных "на шаг", или
    // если он больше размера буфера по умолчанию, размеру буфера по умолчанию.
    // Смысл: скорость может быть задана такой большой, что данные такого размера
    // исчерпают память.
    //
    private val operatingPortionSize = min(dataSizeToBeCopiedByStep, DEFAULT_BUFFER_SIZE)

    private val timeForStepMs: Long = (1000f / progressRatePerSecond).roundToLong()

    private val dataBuffer = ByteArray(operatingPortionSize)


    @Throws(IllegalStateException::class, IllegalArgumentException::class, IOException::class)
    override fun copyFromStreamToStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        progressCallback: ((transferredBytes: Long) -> Unit)?,
        finishCallback: ((transferredBytes: Long) -> Unit)?,
    ) {
//        logD( "copyFromStreamToStream() called with: inputStream = $inputStream, outputStream = $outputStream, progressCallback = $progressCallback, finishCallback = $finishCallback")

        fun publishProgress(totalDataRead: Long) {
            logD("publishProgress(), totalDataRead: $totalDataRead")
            progressCallback?.invoke(totalDataRead)
        }

        fun sleepIfNeeded(stepDurationMs: Long, timeAllocatedForStep: Long,
                          bytesRealCopiedInStep: Long, bytesNeedToBeCopiedInStep: Long) {

            logD( "sleepIfNeeded(): " +
                    "stepDurationMs = $stepDurationMs, " +
                    "timeAllocatedForStep = $timeAllocatedForStep, " +
                    "bytesRealCopiedInStep = $bytesRealCopiedInStep, " +
                    "bytesNeedToBeCopiedInStep = $bytesNeedToBeCopiedInStep," +
                    "total")

            if (bytesRealCopiedInStep >= bytesNeedToBeCopiedInStep) {

                val bytesOverrunPercentage: Float = (bytesRealCopiedInStep.toFloat() / bytesNeedToBeCopiedInStep)

                val sleepingLackTimeMs = (bytesOverrunPercentage * timeAllocatedForStep - stepDurationMs).roundToLong()

                if (sleepingLackTimeMs > 0) {
                    Thread.sleep(sleepingLackTimeMs)
                }
            } else {
                logD("спать не нужно")
            }
        }

        var totalDataRead: Long = 0
        var thisStepDataRead: Long = 0

        logD( "speed: $speedBytesPerSecond, rate: $progressRatePerSecond, operatingPortionSize: $operatingPortionSize")


        var lastPieceOfDataSize = 0

        /**
         * Данные копируются порциями, большими единице,
         * и последний кусочек прочитанных данных меньше этой порции.
         */
        fun dataEndsWithSmallAppendix(): Boolean {
            val stepGreaterThanOne = dataSizeToBeCopiedByStep > 1
            val lastPieceIsSmaller = operatingPortionSize != lastPieceOfDataSize
            val result = stepGreaterThanOne && lastPieceIsSmaller
            return result
        }

        while(true) {

            val startTime = System.currentTimeMillis()

            val readBytes = inputStream.read(dataBuffer, 0, operatingPortionSize)

            // Данные закончились.
            if (-1 == readBytes) {
                logD( "прочитано, -1 == readBytes")
//                if (dataEndsWithSmallAppendix())
//                    publishProgress(totalDataRead)
                finishCallback?.invoke(totalDataRead)
                break
            }

            outputStream.write(dataBuffer, 0, readBytes)

            thisStepDataRead += readBytes
            totalDataRead += readBytes
            lastPieceOfDataSize = readBytes

            // Объёмы данных в порядке уменьшения:
            // Полный размер данных.
            // Размер данных, которые должны быть скопировать за шаг.
            // Размер данных, которыми оперируют в процессе перекидывания данных.

            if (readBytes < operatingPortionSize) {
                logD( "прочитано, readBytes ($readBytes) < operatingPortionSize ($operatingPortionSize)")

                sleepIfNeeded(
                    System.currentTimeMillis() - startTime,
                    timeForStepMs,
                    thisStepDataRead,
                    dataSizeToBeCopiedByStep.toLong()
                )

                publishProgress(totalDataRead)
            }
            else if (readBytes < dataSizeToBeCopiedByStep) {
                logD( "прочитано, readBytes ($readBytes) < dataSizeToBeCopiedByStep ($dataSizeToBeCopiedByStep)")

                sleepIfNeeded(
                    System.currentTimeMillis() - startTime,
                    timeForStepMs,
                    thisStepDataRead,
                    dataSizeToBeCopiedByStep.toLong()
                )

                publishProgress(totalDataRead)
            }
            else if (thisStepDataRead >= dataSizeToBeCopiedByStep) {
                logD( "прочитано, thisStepDataRead ($thisStepDataRead) >= dataSizeToBeCopiedByStep ($dataSizeToBeCopiedByStep)")

                sleepIfNeeded(
                    System.currentTimeMillis() - startTime,
                    timeForStepMs,
                    thisStepDataRead,
                    dataSizeToBeCopiedByStep.toLong()
                )

                publishProgress(totalDataRead)

                thisStepDataRead = 0
            }
        }
    }


    private fun logD(text: String) {
//        Log.d(TAG, "[$uniqueId] $text")
    }

    // Чтобы logcat не скрывал повторяющиеся записи.
    private val uniqueId: String get() = UUID.randomUUID().toString().split("-").first()

    companion object {
        val TAG: String = LimitedStreamCopier::class.java.simpleName
    }
}
