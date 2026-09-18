package com.github.aakumykov.copy_between_streams_with_speed.stream2stream_copier

import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

typealias ProgressCallback = ((transferredBytes: Long) -> Unit)
typealias FinishCallback = ((transferredBytes: Long) -> Unit)

/**
 * ПотокоНЕБЕЗОПАСЕН.
 *
 * @param progressRatePerSecond Частота срабатывания коллбека прогресса.
 */
class LimitedStreamCopier(
    private val speedBytesPerSecond: Int,
    private val progressRatePerSecond: Int,
    dataCopyingStepsPerSecond: Int = 1000,
): Stream2StreamCopier {

    val stepsPerSecond = min(speedBytesPerSecond, dataCopyingStepsPerSecond)

    init {
        if (speedBytesPerSecond <= 0)
            throw IllegalArgumentException("Speed must be greater than zero.")

        if (dataCopyingStepsPerSecond <= 0)
            throw IllegalArgumentException("dataCopyingStepsPerSecond cannot be zero")

//        if (dataCopyingStepsPerSecond > speedBytesPerSecond)
//            throw IllegalArgumentException("data copying steps per second cannot be greater than speed")
    }

    private var progressCallback: ProgressCallback? = null
    private var finishCallback: FinishCallback? = null

    private var workIsRunning = AtomicBoolean(false)

    var totalDataRead: Long = 0
    var stepDataRead: Long = 0

    //
    // Скорость может быть задана огромная, параметр "количество данных, которые должны быть
    // переданы за шаг [steps]", потенциально (но не всегда!) самый большой.
    //
    // get() - для динамического изменения скорости
    //
    private val dataSizeToBeCopiedByStep: Int
        get() = ceil(1f * speedBytesPerSecond / stepsPerSecond).roundToInt()

    //
    // Оперирую данными (черпаю данные) в размере, равном размеру данных "на шаг", или
    // если он больше размера буфера по умолчанию, размеру буфера по умолчанию.
    // Смысл: скорость может быть задана такой большой, что данные такого размера
    // исчерпают память.
    //
    private val operatingPortionSize = min(dataSizeToBeCopiedByStep, DEFAULT_BUFFER_SIZE)

    private val dataCopyingIntervalMs: Long = floor(1000f / stepsPerSecond).roundToLong()
    private val progressCallbackIntervalMs: Long = floor(1000f / progressRatePerSecond).roundToLong()

    private val dataBuffer = ByteArray(operatingPortionSize)

    @Throws(IllegalStateException::class, IllegalArgumentException::class, IOException::class)
    override fun copyFromStreamToStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        progressCallback: ProgressCallback?,
        finishCallback: FinishCallback?,
    ) {
        logD( "speed: $speedBytesPerSecond, rate: $progressRatePerSecond, operatingPortionSize: $operatingPortionSize")

        this.progressCallback = progressCallback
        this.finishCallback = finishCallback

        thread {
            while(workIsRunning.get()) {
                progressCallback?.invoke(totalDataRead)
                TimeUnit.MILLISECONDS.sleep(progressCallbackIntervalMs)
            }
            Log.d(TAG, "progressCallback: $totalDataRead")
            progressCallback?.invoke(totalDataRead)
            Log.d(TAG, "finishCallback: $totalDataRead")
            finishCallback?.invoke(totalDataRead)
        }

        this.workIsRunning.set(true)

        while(true) {

            val startTime = System.currentTimeMillis()

            val readBytes = inputStream.read(dataBuffer, 0, operatingPortionSize)

            // Данные закончились.
            if (-1 == readBytes) {
                logD( "прочитано, -1 == readBytes")
                workIsRunning.set(false)
                break
            }

            outputStream.write(dataBuffer, 0, readBytes)

            stepDataRead += readBytes
            totalDataRead += readBytes

            // Объёмы данных в порядке уменьшения:
            // Полный размер данных.
            // Размер данных, которые должны быть скопировать за шаг.
            // Размер данных, которыми оперируют в процессе перекидывания данных.

            if (readBytes < operatingPortionSize) {
                logD( "прочитано, readBytes ($readBytes) < operatingPortionSize ($operatingPortionSize)")
                sleepIfNeeded(startTime)
            }
            else if (readBytes < dataSizeToBeCopiedByStep) {
                logD( "прочитано, readBytes ($readBytes) < dataSizeToBeCopiedByStep ($dataSizeToBeCopiedByStep)")
                sleepIfNeeded(startTime)
            }
            else if (stepDataRead >= dataSizeToBeCopiedByStep) {
                logD( "прочитано, thisStepDataRead ($stepDataRead) >= dataSizeToBeCopiedByStep ($dataSizeToBeCopiedByStep)")
                sleepIfNeeded(startTime)
                stepDataRead = 0
            }
        }
    }

    private fun sleepIfNeeded(startTime: Long) {
        if (stepDataRead >= dataSizeToBeCopiedByStep) {
            sleepingLackTimeMs(System.currentTimeMillis() - startTime).also {
                if (it > 0) Thread.sleep(it)
            }
        } else {
            logD("спать не нужно")
        }
    }

    private fun sleepingLackTimeMs(stepDurationMs: Long): Long {
        val bytesOverrunPercentage: Float = (stepDataRead.toFloat() / dataSizeToBeCopiedByStep)
        return (bytesOverrunPercentage * dataCopyingIntervalMs - stepDurationMs).roundToLong()
    }


    private fun logD(text: String) {
//        Log.d(TAG, "[$uniqueId] $text")
    }

    private fun logDEBUG(text: String, tag: String = TAG) {
//        Log.i(tag, "[$uniqueId] $text")
    }

    // Чтобы logcat не скрывал повторяющиеся записи.
    private val uniqueId: String get() = UUID.randomUUID().toString().split("-").first()

    companion object {
        val TAG: String = LimitedStreamCopier::class.java.simpleName
    }
}
