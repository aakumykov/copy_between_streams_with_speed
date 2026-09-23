package com.github.aakumykov.copy_between_streams_with_speed

import android.util.Log
import com.github.aakumykov.copy_between_streams_with_speed.ext.roundToFloatingDigits
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
        logD("")
        logD("------------------------------------------------------------------------------")
        logD("copyFromStreamToStream() called with: speedBytesPerSecond = $speedBytesPerSecond, progressRatePerSecond = $progressRatePerSecond, stepsPerSecond = $stepsPerSecond")
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

            logD("sleepIfNeeded(), rd:$realDurationMs, ed:$expectedDurationMs")
            logD("sleepIfNeeded(), rs:$realDataSize, es:$expectedDataSize")

            // Время, необходимое для копирования данных, пересчитывается
            // согласно их объёму, обработанному на этом шаге.
            val dataFraction: Float = realDataSize.toFloat() / expectedDataSize

            val correctedExpectedDurationMs = (dataFraction * expectedDurationMs).roundToLong()

            val timeFraction: Double = (1.toDouble() * realDurationMs / correctedExpectedDurationMs)
            logD("timeFraction: $timeFraction")

            // Если данные скопировались за время, меньшее положенного,
            // делаем паузу.
            if (timeFraction < 1.0) {
                val sleepDiff = correctedExpectedDurationMs - realDurationMs
                logD("досыпаю $sleepDiff мс")
                TimeUnit.MILLISECONDS.sleep(sleepDiff)
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

            val startTimeMs = System.currentTimeMillis()

            outputStream.write(dataBuffer, 0, readBytes)

            val stepDurationMs = System.currentTimeMillis() - startTimeMs

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
                }
                stepDataCopied = 0
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
        val TAG: String = LimitedStreamCopierNew::class.java.simpleName
    }
}