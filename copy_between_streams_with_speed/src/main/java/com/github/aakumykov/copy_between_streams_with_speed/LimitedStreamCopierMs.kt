package com.github.aakumykov.copy_between_streams_with_speed

import android.util.Log
import com.github.aakumykov.copy_between_streams_with_speed.utils.currentTimeMs
import com.github.aakumykov.copy_between_streams_with_speed.utils.currentTimeNanos
import com.github.aakumykov.copy_between_streams_with_speed.utils.humanDecimalPlaces
import com.github.aakumykov.copy_between_streams_with_speed.utils.humanSizeBinary
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class LimitedStreamCopierMs {

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

        val dataCopyingTimeQuantMs = ceil(1000f / dataCopyingSteps).roundToLong()
        logD("dataCopyingTimeQuantMs: $dataCopyingTimeQuantMs")

        val dataSizeToBeCopiedByQuant = ceil(1f * speedBytesPerSecond / dataCopyingSteps).roundToInt()
        logD("dataSizeToBeCopiedByQuant: $dataSizeToBeCopiedByQuant")

        val operationPortionSize = min(DEFAULT_BUFFER_SIZE, dataSizeToBeCopiedByQuant)
        logD("operationPortionSize: $operationPortionSize")

        var stepDataCopied = 0
        var totalDataCopied: Long = 0

        val dataBuffer = ByteArray(operationPortionSize)


        fun sleepIfNeeded(realDurationMs: Long, expectedDurationMs: Long,
                          realDataSize: Int, expectedDataSize: Int) {

            logD("sleepIfNeeded(), rldr:$realDurationMs, exdr:$expectedDurationMs, rlsz:$realDataSize, exsz:$expectedDataSize")

            // Время, необходимое для копирования данных, пересчитывается
            // согласно их объёму, обработанному на этом шаге.
            val dataFraction: Float = realDataSize.toFloat() / expectedDataSize
            logD("dataFraction:$dataFraction")

            val correctedExpectedDurationMs = (dataFraction * expectedDurationMs).roundToLong()
            logD("correctedExpectedDurationMs:$correctedExpectedDurationMs")

            val timeFraction: Double = (1.toDouble() * realDurationMs / correctedExpectedDurationMs)
            logD("timeFraction: $timeFraction")

            // Если данные скопировались за время, меньшее положенного,
            // делаем паузу.
            if (timeFraction < 1.0) {
                val sleepDiffMs = correctedExpectedDurationMs - realDurationMs
                logD("досыпаю[$currentTimeMs] $sleepDiffMs мс (timeFraction:$timeFraction < 1.0)")
                TimeUnit.MILLISECONDS.sleep(sleepDiffMs)
                logD("доспал [$currentTimeMs]")
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

            val startTimeMs = currentTimeMs

            outputStream.write(dataBuffer, 0, readBytes)

            val stepDurationMs = currentTimeMs - startTimeMs

            stepDataCopied += readBytes
            totalDataCopied += readBytes

            if (readBytes < operationPortionSize) {
                sleepIfNeeded(
                    stepDurationMs, dataCopyingTimeQuantMs,
                    readBytes, operationPortionSize
                )
                stepDataCopied = 0
            }
            else if (readBytes == operationPortionSize) {
                // Не последняя порция данных.
                if (stepDataCopied >= dataSizeToBeCopiedByQuant) {
                    // Пора считать скорость.
                    sleepIfNeeded(
                        stepDurationMs, dataCopyingTimeQuantMs,
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
//        Log.d(TAG, text)
    }

    companion object {
        val TAG: String = LimitedStreamCopierMs::class.java.simpleName
        const val NANOS_IN_SECOND: Double = 1_000_000_000.0
    }
}