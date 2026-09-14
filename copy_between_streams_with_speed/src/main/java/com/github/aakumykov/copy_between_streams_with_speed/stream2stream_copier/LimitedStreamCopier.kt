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
 * @param dataCopyStepsPerSecond Не может быть больше [speedBytesPerSecond].
 * @param progressRatePerSecond Частота срабатывания коллбека прогресса.
 */
class LimitedStreamCopier(
    private val speedBytesPerSecond: Int,
    private val dataCopyStepsPerSecond: Int,
    private val progressRatePerSecond: Int, // TODO: перенести в функцию?
): Stream2StreamCopier {

    private var speed: Int = speedBytesPerSecond

    //
    // Скорость может быть задана огромная, параметр "количество данных, которые должны быть
    // переданы за шаг [dataCopyStepsPerSecond]", потенциально (но не всегда!) самый большой.
    //
    // get() - для динамического изменения скорости
    //
    private val dataSizeToBeCopiedByStep: Int
        get() = (1f * speed / dataCopyStepsPerSecond).roundToInt()

    //
    // Оперирую данными (черпаю данные) в размере, равном размеру данных "на шаг", или
    // если он больше размера буфера по умолчанию, размеру буфера по умолчанию.
    // Смысл: скорость может быть задана такой большой, что данные такого размера
    // исчерпают память.
    //
    private val operatingPortionSize = min(dataSizeToBeCopiedByStep, DEFAULT_BUFFER_SIZE)

    private val timeForStepMs: Long = (1000f / dataCopyStepsPerSecond).roundToLong()

    private val dataBuffer = ByteArray(operatingPortionSize)


    @Throws(IllegalStateException::class, IllegalArgumentException::class, IOException::class)
    override fun copyFromStreamToStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        progressCallback: ((transferredBytes: Long) -> Unit)?,
        finishCallback: ((transferredBytes: Long) -> Unit)?,
    ) {
//        logD( "copyFromStreamToStream() called with: inputStream = $inputStream, outputStream = $outputStream, progressCallback = $progressCallback, finishCallback = $finishCallback")

        val minimumProgressCallbackPeriodMs = (1000f / progressRatePerSecond).roundToLong()
        var lastProgressPublishTimeMs: Long = 0
        var lastProgressWasSent = false


        fun publishProgressIfItsTime(totalDataRead: Long, force: Boolean = false) {
            val progressSendingInterval: Long = System.currentTimeMillis() - lastProgressPublishTimeMs

            if (progressSendingInterval >= minimumProgressCallbackPeriodMs || force) {
                logD( "publishProgressIfItsTime() called with: totalDataRead = $totalDataRead, force = $force")
                progressCallback?.invoke(totalDataRead)
                lastProgressPublishTimeMs = System.currentTimeMillis()
                lastProgressWasSent = true
            } else {
                lastProgressWasSent = false
            }
        }

        fun sleepIfNeeded(stepDurationMs: Long, timeAllocatedForStep: Long,
                          bytesRealCopiedInStep: Long, bytesNeedToBeCopiedInStep: Long) {
//            logD( "sleepIfNeeded() called with: stepDurationMs = $stepDurationMs, timeAllocatedForStep = $timeAllocatedForStep, bytesRealCopiedInStep = $bytesRealCopiedInStep, bytesNeedToBeCopiedInStep = $bytesNeedToBeCopiedInStep")

            if (bytesRealCopiedInStep >= bytesNeedToBeCopiedInStep) {

                val bytesOverrunPercentage: Float = (bytesRealCopiedInStep.toFloat() / bytesNeedToBeCopiedInStep)

                val sleepingLackTimeMs = (bytesOverrunPercentage * timeAllocatedForStep - stepDurationMs).roundToLong()

                if (sleepingLackTimeMs > 0) {
                    Thread.sleep(sleepingLackTimeMs)
                }
            } else {
                println("спать не нужно")
            }
        }


        if (speed <= 0)
            throw IllegalArgumentException("Speed must be greater than zero.")

        if (dataCopyStepsPerSecond > speed)
            throw IllegalArgumentException("StepsPerSecond cannot be greater than speedBytesPerSecond.")

        var totalDataRead: Long = 0
        var thisStepDataRead: Long = 0


        // Для начала отсчёта периода срабатывания коллбека прогресса
        publishProgressIfItsTime(0, true)


        logD( "operatingPortionSize: $operatingPortionSize")

        while(true) {
            val startTime = System.currentTimeMillis()

            val readBytes = inputStream.read(dataBuffer, 0, operatingPortionSize)

            // Данные закончились.
            if (-1 == readBytes) {
                logD( "-1 == readBytes")
                // Для случая, когда данные закончились ровно на границе [dataSizeToBeCopiedByStep].
                // В этом случае
//                if (!lastProgressWasSent) { progressCallback?.invoke(totalDataRead) }
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
                logD( "readBytes ($readBytes) < operatingPortionSize ($operatingPortionSize)")

                publishProgressIfItsTime(totalDataRead, true)
                sleepIfNeeded(
                    System.currentTimeMillis() - startTime,
                    timeForStepMs,
                    thisStepDataRead,
                    dataSizeToBeCopiedByStep.toLong()
                )
            }
            else if (readBytes < dataSizeToBeCopiedByStep) {
                logD( "readBytes ($readBytes) < dataSizeToBeCopiedByStep ($dataSizeToBeCopiedByStep)")

                publishProgressIfItsTime(totalDataRead, true)
                sleepIfNeeded(
                    System.currentTimeMillis() - startTime,
                    timeForStepMs,
                    thisStepDataRead,
                    dataSizeToBeCopiedByStep.toLong()
                )
            }
            else if (thisStepDataRead >= dataSizeToBeCopiedByStep) {
                logD( "thisStepDataRead ($thisStepDataRead) >= dataSizeToBeCopiedByStep ($dataSizeToBeCopiedByStep)")

                publishProgressIfItsTime(totalDataRead)
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


    override fun setSpeedBytesPerSec(value: Int) {
        if (value >= dataCopyStepsPerSecond) {
            speed = value
        } else {
            Log.w(TAG, "Speed bytes per second ($value) cannot be greater than steps per second ($dataCopyStepsPerSecond) value.")
        }
    }


    private fun logD(text: String) {
        Log.d(TAG, "[$uniqueId] $text")
    }

    private val uniqueId: String get() = UUID.randomUUID().toString().split("-").first()

    companion object {
        val TAG: String = LimitedStreamCopier::class.java.simpleName
    }
}
