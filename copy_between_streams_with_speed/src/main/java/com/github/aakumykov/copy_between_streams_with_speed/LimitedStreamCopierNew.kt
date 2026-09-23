package com.github.aakumykov.copy_between_streams_with_speed

import android.util.Log
import com.github.aakumykov.copy_between_streams_with_speed.utils.currentTimeNanos
import com.github.aakumykov.copy_between_streams_with_speed.utils.humanSizeBinary
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class LimitedStreamCopierNew {

    fun copyFromStreamToStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        speedBytesPerSecond: Int,
        progressRatePerSecond: Int = 1,
        stepsPerSecond: Int = 1000
    ) {
        if (speedBytesPerSecond <= 0)
            throw IllegalArgumentException("Speed must be greater than zero ($speedBytesPerSecond)")

        if (progressRatePerSecond <= 0)
            throw IllegalArgumentException("progress rate per second must be greater than zero ($progressRatePerSecond)")

        if (stepsPerSecond <= 0)
            throw IllegalArgumentException("Steps second must be greater than zero ($stepsPerSecond)")

        logD("")
        logD("------------------------------------------------------------------------------")
        logD("copyFromStreamToStream() called with: speedBytesPerSecond = ${speedBytesPerSecond.humanSizeBinary()}/s, progressRatePerSecond = $progressRatePerSecond, stepsPerSecond = $stepsPerSecond")
        logD("------------------------------------------------------------------------------")

        val dataCopyingSteps = min(speedBytesPerSecond, stepsPerSecond)
        logD("dataCopyingSteps: $dataCopyingSteps")

        val dataCopyingTimeQuantNs = ceil(1_000_000f / dataCopyingSteps).roundToLong()
        logD("dataCopyingTimeQuantNs: $dataCopyingTimeQuantNs")

        val dataSizeToBeCopiedByQuant = ceil(1f * speedBytesPerSecond / dataCopyingSteps).roundToInt()
        logD("dataSizeToBeCopiedByQuant: $dataSizeToBeCopiedByQuant")

        val operationPortionSize = min(DEFAULT_BUFFER_SIZE, dataSizeToBeCopiedByQuant)
        logD("operationPortionSize: $operationPortionSize")

        var stepDataCopied = 0
        var totalDataCopied: Long = 0

        val dataBuffer = ByteArray(operationPortionSize)


        fun sleepIfNeeded(realDurationNs: Long, expectedDurationNs: Long,
                          realDataSize: Int, expectedDataSize: Int) {

            logD("sleepIfNeeded(), rldr:$realDurationNs, exdr:$expectedDurationNs, rlsz:$realDataSize, exsz:$expectedDataSize")

            // Время, необходимое для копирования данных, пересчитывается
            // согласно их объёму, обработанному на этом шаге.
            val dataFraction: Float = realDataSize.toFloat() / expectedDataSize
            logD("dataFraction:$dataFraction")

            val correctedExpectedDurationNs = (dataFraction * expectedDurationNs).roundToLong()
            logD("correctedExpectedDurationNs:$correctedExpectedDurationNs")

            val timeFraction: Double = (1.toDouble() * realDurationNs / correctedExpectedDurationNs)
            logD("timeFraction: $timeFraction")

            // Если данные скопировались за время, меньшее положенного,
            // делаем паузу.
            if (timeFraction < 1.0) {
                val sleepDiffNs = correctedExpectedDurationNs - realDurationNs
                logD("досыпаю[$currentTimeNanos] $sleepDiffNs нс (timeFraction:$timeFraction < 1.0)")
                TimeUnit.MILLISECONDS.sleep(sleepDiffNs)
                logD("доспал [$currentTimeNanos]")
            } else {
                logD("Спать не нужно (timeFraction: $timeFraction >= 1.0)")
            }

            // Если копирование данных заняло
        }

        while(true) {
            val readBytes = inputStream.read(dataBuffer, 0, operationPortionSize)
//            logD("readBytes: $readBytes")

            if (-1 == readBytes) {
                logD("Данные закончились")
                break
            }

            val startTimeNs = currentTimeNanos

            outputStream.write(dataBuffer, 0, readBytes)

            val stepDurationNs = currentTimeNanos - startTimeNs

            stepDataCopied += readBytes
            totalDataCopied += readBytes

            if (readBytes < operationPortionSize) {
                sleepIfNeeded(
                    stepDurationNs, dataCopyingTimeQuantNs,
                    readBytes, operationPortionSize
                )
                stepDataCopied = 0
            }
            else if (readBytes == operationPortionSize) {
                // Не последняя порция данных.
                if (stepDataCopied >= dataSizeToBeCopiedByQuant) {
                    // Пора считать скорость.
                    sleepIfNeeded(
                        stepDurationNs, dataCopyingTimeQuantNs,
                        stepDataCopied, dataSizeToBeCopiedByQuant
                    )
                    stepDataCopied = 0
                }
            } else {
                throw RuntimeException("readBytes ($readBytes) > operationPortionSize ($operationPortionSize)")
            }
        }

        logD("------------------------------------------------------------------------------")
        logD("")
    }

    private fun logD(text: String) {
        Log.d(TAG, text)
    }

    private fun logDD(text: String) {
        Log.d(TAG, text)
    }

    companion object {
        val TAG: String = LimitedStreamCopierNew::class.java.simpleName
    }
}