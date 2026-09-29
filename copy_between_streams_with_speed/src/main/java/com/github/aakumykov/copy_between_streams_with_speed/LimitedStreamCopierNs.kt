package com.github.aakumykov.copy_between_streams_with_speed

import android.util.Log
import com.github.aakumykov.copy_between_streams_with_speed.ext.notEquals
import com.github.aakumykov.copy_between_streams_with_speed.ext.roundToFloatingDigits
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

// TODO: избавиться от преобразований данных
class LimitedStreamCopierNs {

    fun copyFromStreamToStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        speedBytesPerSecond: Int,
        progressRatePerSecond: Int = 1,
        stepsPerSecond: Int = 1000,
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

        val progressPeriodNs: Double = NANOS_IN_SECOND / progressRatePerSecond
        logI("progressPeriodNs: $progressPeriodNs")


        val dataBuffer = ByteArray(operationPortionSize)


        var totalDataCopied: Long = 0
        var stepDataCopied = 0
        var lastProgressShowTimeNs: Long = currentTimeNanos
        var lastSentProgressValue: Long? = null

        fun sleepIfNeeded(realDurationNanos: Long, expectedDurationNanos: Long) {

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
            stepDataCopied: Int,
            stepWithSleepDurationNanos: Long,
            force: Boolean = false,
            withSpeed: Boolean = true
        ) {
            logDD("showProgressIfNeeded(stepWithSleepDurationNanos:$stepWithSleepDurationNanos, force:$force, withSpeed:$withSpeed)")

            val textIisForced = if (force) "force" else ""
            val textWithSpeed = if (withSpeed) "withSpeed" else ""

            progressCallback?.also {

                val currentTimeNs = currentTimeNanos
                val timeElapsedNs = currentTimeNs - lastProgressShowTimeNs

                val speed = if (withSpeed) {
                    (NANOS_IN_SECOND * stepDataCopied / stepWithSleepDurationNanos).roundToLong()
                } else 0

                if (timeElapsedNs >= progressPeriodNs || force) {
                    logI("timeElapsedNs >= progressPeriodNs (${timeElapsedNs.humanDecimalPlaces} >= ${progressPeriodNs.humanDecimalPlaces})")
                    progressCallback.invoke(totalDataCopied, speed)
                    logDD("progressCallback(tot:$totalDataCopied, sp:$speed, $textIisForced, $textWithSpeed)")
                    lastProgressShowTimeNs = currentTimeNs
                    lastSentProgressValue = totalDataCopied
                } else {
                    logDD("Отсылать прогресс не нужно [timeElapsedNs (${timeElapsedNs.humanDecimalPlaces}) < progressPeriodMs (${progressPeriodNs.humanDecimalPlaces})]")
                }
            }
        }

        fun calcSpeed(dataSizeBytes: Int, durationNs: Long): Double {
            return (1.0 * dataSizeBytes / durationNs)
        }

        while(true) {

            val readBytes = inputStream.read( dataBuffer, 0, operationPortionSize)
            logD("")
            logDD("readBytes: $readBytes")

            if (-1 == readBytes) {
                logD("Данные закончились")

                // Последняя порция данных не была сообщена или они закончились на первом чтении.
                if (lastSentProgressValue?.notEquals(totalDataCopied) ?: false) {
                    showProgressIfNeeded(
                        0,
                        0,
                        force = true,
                        withSpeed = false
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

                logMomentalSpeed(calcSpeed(readBytes, copyAndSleepDurationNanos))

                showProgressIfNeeded(
                    stepDataCopied = readBytes,
                    stepWithSleepDurationNanos = copyAndSleepDurationNanos,
                    force = true,
                    withSpeed = true
                )

                stepDataCopied = 0
            }
            else if (readBytes == operationPortionSize) {
                logD("readBytes == operationPortionSize ($readBytes == $operationPortionSize)")

                if (stepDataCopied >= maxDataSizeCanCopiedByStep) {
                    // Пора считать скорость.
                    sleepIfNeeded(
                        dataCopyDurationNanos,
                        expectedStepDurationNanos
                    )

                    val copyAndSleepDurationNanos = currentTimeNanos - stepStartTimeNanos

                    logMomentalSpeed(calcSpeed(readBytes, copyAndSleepDurationNanos))

                    showProgressIfNeeded(
                        stepDataCopied = stepDataCopied,
                        stepWithSleepDurationNanos = dataCopyDurationNanos,
                        force = false,
                        withSpeed = true
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

    private fun logDD(text: String) {
//        Log.d(TAG, text)
    }

    private fun logI(text: String) {
//        Log.i(TAG, text)
    }

    private fun logMomentalSpeed(value: Number) {
        Log.i(TAG, "моментальная скорость: ${value} байт/с")
    }

    companion object {
        val TAG: String = LimitedStreamCopierNs::class.java.simpleName
        // FIXME: для NANOS_IN_SECOND достаточно Long или Float?
        const val NANOS_IN_SECOND: Double = 1_000_000_000.0 // TODO: переделать в Long
        const val MILLIS_IN_SECOND: Int = 1_000
    }
}