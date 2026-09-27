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

// TODO: избавться от преобразований данных
class LimitedStreamCopierNs {

    fun copyFromStreamToStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        speedBytesPerSecond: Int,
        progressRatePerSecond: Int = 1,
        stepsPerSecond: Int = MILLIS_IN_SECOND,
        progressCallback: ((bytesTransferred: Long, speedBytesPerSec: Long) -> Unit)? = null,
        finishCallback: ((bytesTransferred: Long) -> Unit)? = null,
    ) {
        if (speedBytesPerSecond <= 0)
            throw IllegalArgumentException("Speed must be greater than zero ($speedBytesPerSecond)")

        if (progressRatePerSecond <= 0)
            throw IllegalArgumentException("progress rate per second must be greater than zero ($progressRatePerSecond)")

        if (stepsPerSecond <= 0)
            throw IllegalArgumentException("Steps second must be greater than zero ($stepsPerSecond)")

//        logD("1")
        logD("-----------------------------------------")
        logD("copyFromStreamToStream() called with: speedBytesPerSecond = ${speedBytesPerSecond.humanSizeBinary()}/s, progressRatePerSecond = $progressRatePerSecond, stepsPerSecond = $stepsPerSecond")
        logD("-----------------------------------------")

        // Количество шагов в секунду не может быть больше скорости в байтах.
        val dataCopyingSteps = min(stepsPerSecond, speedBytesPerSecond)
        logD("dataCopyingSteps: $dataCopyingSteps")

        val expectedStepDurationNanos = ceil(NANOS_IN_SECOND / dataCopyingSteps).roundToLong()
        logD("expectedStepDurationNanos: ${expectedStepDurationNanos.humanDecimalPlaces}")

        val maxDataSizeCanCopiedByStep = ceil(1f * speedBytesPerSecond / dataCopyingSteps).roundToInt()
        logD("maxDataSizeCanCopiedByStep: ${maxDataSizeCanCopiedByStep.humanDecimalPlaces}")

        val operationPortionSize = min(DEFAULT_BUFFER_SIZE, maxDataSizeCanCopiedByStep)
        logD("operationPortionSize: $operationPortionSize")

        val progressPeriodMs: Long = (MILLIS_IN_SECOND / progressRatePerSecond).toLong()
        logD("progressPeriodMs: $progressPeriodMs")


        val dataBuffer = ByteArray(operationPortionSize)


        var totalDataCopied: Long = 0
        var stepDataCopied = 0
        var lastProgressShowTimeMs: Long = currentTimeMs


        fun sleepIfNeeded(realDurationNanos: Long, expectedDurationNanos: Long, ) {

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


        fun showProgressIfNeeded(
            totalDataSize: Long,
            stepDataSize: Int,
            stepWithSleepDurationNanos: Long,
            force: Boolean = false
        ) {
            progressCallback?.also {

                val currentTime = currentTimeMs
                val timeElapsed = currentTime - lastProgressShowTimeMs

                // TODO: избавиться от округления
                val speed = if (!force) {
                    (1f * stepDataSize / stepWithSleepDurationNanos).roundToLong()
                } else 0

                if (timeElapsed >= progressPeriodMs || force) {
                    progressCallback.invoke(totalDataSize, speed)
                    lastProgressShowTimeMs = currentTime
                }
            }
        }

        var lastReadEndsOnDataBorder = false

        while(true) {

            val readBytes = inputStream.read( dataBuffer, 0, operationPortionSize)
            logD("")
            logD("readBytes: $readBytes")

            if (-1 == readBytes) {
                logD("Данные закончились")
                if (lastReadEndsOnDataBorder) {
                    showProgressIfNeeded(
                        totalDataCopied,
                        stepDataCopied,
                        0,
                        force = true
                    )
                }
                finishCallback?.invoke(totalDataCopied)
                break
            }


            val stepStartTimeNanos = currentTimeNanos

            outputStream.write(dataBuffer, 0, readBytes)

            val dataCopyDurationNanos = currentTimeNanos - stepStartTimeNanos

            stepDataCopied += readBytes
            logD("stepDataCopied: $stepDataCopied")

            totalDataCopied += readBytes
            logD("totalDataCopied: $totalDataCopied")


            if (readBytes < operationPortionSize) {
                logD("readBytes < operationPortionSize ($readBytes < $operationPortionSize)")

                lastReadEndsOnDataBorder = false

                val dataFraction: Float = 1f * readBytes / operationPortionSize
                logD("dataFraction: $dataFraction")

                val correctedExpectedStepDuration = (dataFraction * expectedStepDurationNanos).roundToLong()
                logD("correctedExpectedStepDuration: ${correctedExpectedStepDuration.humanDecimalPlaces}")

                logD("realDurationNanos: ${dataCopyDurationNanos.humanDecimalPlaces}")

                sleepIfNeeded(
                    dataCopyDurationNanos,
                    correctedExpectedStepDuration,
                )

                val copyAndSleepDurationNanos = currentTimeNanos - stepStartTimeNanos

                showProgressIfNeeded(
                    totalDataCopied,
                    readBytes,
                    copyAndSleepDurationNanos,
                    true
                )

                stepDataCopied = 0
            }
            else if (readBytes == operationPortionSize) {
                logD("readBytes == operationPortionSize ($readBytes == $operationPortionSize)")

                lastReadEndsOnDataBorder = true

                if (stepDataCopied >= maxDataSizeCanCopiedByStep) {
                    // Пора считать скорость.
                    sleepIfNeeded(
                        dataCopyDurationNanos,
                        expectedStepDurationNanos
                    )

                    showProgressIfNeeded(
                        totalDataCopied,
                        stepDataCopied,
                        dataCopyDurationNanos
                    )

                    stepDataCopied = 0
                }
            } else {
                throw RuntimeException("readBytes ($readBytes) > operationPortionSize ($operationPortionSize)")
            }
        }

        logD("-----------------------------------------")
//        logD("2")
    }


    private fun logD(text: String) {
//        Log.d(TAG, text)
    }

    companion object {
        val TAG: String = LimitedStreamCopierNs::class.java.simpleName
        const val NANOS_IN_SECOND: Double = 1_000_000_000.0 // TODO: переделать в Long
        const val MILLIS_IN_SECOND: Int = 1_000
    }
}