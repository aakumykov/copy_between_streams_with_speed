package com.github.aakumykov.copy_between_streams_with_speed

import android.util.Log
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

class LimitedStreamCopierNs {

    fun copyFromStreamToStreamNanos(
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

        // Количество шагов в секунду не может быть больше скорости в байтах.
        val dataCopyingSteps = min(stepsPerSecond, speedBytesPerSecond)
        logD("dataCopyingSteps: $dataCopyingSteps")

        val expectedStepDurationNanos = ceil(NANOS_IN_SECOND / dataCopyingSteps).roundToLong()
        logD("expectedStepDurationNanos: ${expectedStepDurationNanos.humanDecimalPlaces}")

        val dataSizeToBeCopiedByStep = ceil(1f * speedBytesPerSecond / dataCopyingSteps).roundToInt()
        logD("dataSizeToBeCopiedByStep: ${dataSizeToBeCopiedByStep.humanDecimalPlaces}")

        val operationPortionSize = min(DEFAULT_BUFFER_SIZE, dataSizeToBeCopiedByStep)
        logD("operationPortionSize: $operationPortionSize")

        var stepDataCopied = 0
        var totalDataCopied: Long = 0

        val dataBuffer = ByteArray(operationPortionSize)


        fun sleepIfNeeded(
            realDurationNanos: Long,
            expectedDurationNanos: Long,
        ) {
            logD("sleepIfNeeded(), " +
                    "rldr:$realDurationNanos, " +
                    "exdr:${expectedDurationNanos.humanDecimalPlaces}")

            val sleepDiffNanos = expectedDurationNanos - realDurationNanos
            logD("sleepDiffNanos: ${sleepDiffNanos.humanDecimalPlaces}")

            // Если данные скопировались за время, меньшее положенного,
            // делаем паузу.
            if (sleepDiffNanos > 0) {
                val sleepStartNs = currentTimeNanos
                logD("досыпаю ${sleepDiffNanos.humanDecimalPlaces} нанос.")
                TimeUnit.NANOSECONDS.sleep(sleepDiffNanos)
                val realSleepDurationNs = currentTimeNanos - sleepStartNs
                logD("доспал: ${realSleepDurationNs.humanDecimalPlaces} нанос.")
            } else {
                logD("Спать не нужно (timeFraction: ${sleepDiffNanos.humanDecimalPlaces} <= 0)")
            }

            // Если копирование данных заняло
        }


        var copySteps = 0

        while(true) {
            val readBytes = inputStream.read(dataBuffer, 0, operationPortionSize)
//            logD("readBytes: $readBytes")

            copySteps++

            if (-1 == readBytes) {
                logD("Данные закончились")
                break
            }

            val startTimeNanos = currentTimeNanos

            outputStream.write(dataBuffer, 0, readBytes)

            val realStepDurationNanos = currentTimeNanos - startTimeNanos

            stepDataCopied += readBytes
            totalDataCopied += readBytes

            if (readBytes < operationPortionSize) {
                logD("readBytes < operationPortionSize ($readBytes < $operationPortionSize)")

                val dataFraction: Float = 1f * readBytes / operationPortionSize
                logD("dataFraction: $dataFraction")

                val correctedExpectedStepDuration = (dataFraction * expectedStepDurationNanos).roundToLong()
                logD("correctedExpectedStepDuration: ${correctedExpectedStepDuration.humanDecimalPlaces}")

                logD("realDurationNanos: ${realStepDurationNanos.humanDecimalPlaces}")

                sleepIfNeeded(
                    realStepDurationNanos,
                    correctedExpectedStepDuration,
                )
                stepDataCopied = 0
            }
            else if (readBytes == operationPortionSize) {
                logD("readBytes == operationPortionSize ($readBytes == $operationPortionSize)")
                // Не последняя порция данных.
                if (stepDataCopied >= dataSizeToBeCopiedByStep) {
                    // Пора считать скорость.
                    sleepIfNeeded(
                        realStepDurationNanos,
                        expectedStepDurationNanos
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

    companion object {
        val TAG: String = LimitedStreamCopierNs::class.java.simpleName
        const val NANOS_IN_SECOND: Double = 1_000_000_000.0
    }
}


fun Long.percentOf(other: Long): Double {
    return 1.0 * this / other
}