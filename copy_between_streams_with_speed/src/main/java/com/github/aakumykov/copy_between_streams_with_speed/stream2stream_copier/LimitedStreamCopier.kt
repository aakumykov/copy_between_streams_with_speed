package com.github.aakumykov.copy_between_streams_with_speed.stream2stream_copier

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

//typealias ProgressCallback = ((transferredBytes: Long, speedBytedPerSecond: Long) -> Unit)
//typealias FinishCallback = ((transferredBytes: Long) -> Unit)

/**
 * ПотокоНЕБЕЗОПАСЕН.
 *
 * @param speedBytesPerSecond
 *
 * @param progressRatePerSecond Частота срабатывания коллбека прогресса.
 *
 * @param dataCopyingStepsPerSecond Для равномерного "размазывания" данных
 * по времени во имя поддержания заданной скорости служит этот параметр.
 * Можно передать все данные за 1 мс и ждать оставшиеся 999 мс,
 * средняя скорость при этом будет заданной, но "мгновенная" значительно выше.
 *
 * Особенность: finishCallback вызывается даже при ошибке.
 */
class LimitedStreamCopier(
    private val speedBytesPerSecond: Int,
    private val progressRatePerSecond: Int,
    dataCopyingStepsPerSecond: Int = 1000,
): Stream2StreamCopier {

    init {
        if (speedBytesPerSecond <= 0)
            throw IllegalArgumentException("Speed must be greater than zero.")

        if (dataCopyingStepsPerSecond <= 0)
            throw IllegalArgumentException("dataCopyingStepsPerSecond cannot be zero")
    }

    //
    // Количество шагов в секунду не может быть меньше количества байт
    // передаваемых за эту же секунду, поэтому выбирается меньшее значение.
    //
    private val stepsPerSecond
        = min(speedBytesPerSecond, dataCopyingStepsPerSecond)

    //
    // При огромной заданной скорости, параметр "количество данных, которые должны быть
    // переданы за шаг", потенциально (но не всегда!) самый большой.
    // Будучи задаваемым здесь, округляется в большую сторону.
    //
    private val dataSizeToBeCopiedByStep: Int
        = ceil(1f * speedBytesPerSecond / stepsPerSecond).roundToInt()

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

    private val workIsRunning = AtomicBoolean(false)


    var totalDataRead: Long = 0
    var oneStepDataRead: Long = 0


    // Объёмы данных в порядке уменьшения:
    // Полный размер данных.
    // Размер данных, которые должны быть скопировать за шаг.
    // Размер данных, которыми оперируют в процессе перекидывания данных.


    @Throws(IllegalStateException::class,
        IllegalArgumentException::class, IOException::class)
    override fun copyFromStreamToStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        progressCallback: ((transferredBytes: Long, speedBytesPerSecond: Long) -> Unit)?,
        finishCallback: ((transferredBytes: Long) -> Unit)?,
    ) {
        try {
            logD( "speed: $speedBytesPerSecond, rate: $progressRatePerSecond, operatingPortionSize: $operatingPortionSize")

            thread {
                var startTime: Long = System.currentTimeMillis()
                var startBytes = totalDataRead

                do {
                    progressCallback?.invoke(
                        totalDataRead,
                        calcSpeed(startTime, startBytes)
                    )

                    TimeUnit.MILLISECONDS.sleep(progressCallbackIntervalMs)

                    startTime = System.currentTimeMillis()
                    startBytes = totalDataRead

                } while(workIsRunning.get())

                // Отправка остатков прогресса, потерянного из-за задержек.
                progressCallback?.invoke(
                    totalDataRead,
                    calcSpeed(startTime, startBytes)
                )
                finishCallback?.invoke(totalDataRead)
            }

            this.workIsRunning.set(true)

            while(true) {

                val startTime = System.currentTimeMillis()

                val readBytes = inputStream.read(dataBuffer, 0, operatingPortionSize)

                // Данные закончились.
                if (-1 == readBytes) {
                    logD( "прочитано, -1 == readBytes")
                    break
                }

                outputStream.write(dataBuffer, 0, readBytes)

                oneStepDataRead += readBytes
                totalDataRead += readBytes

                if (readBytes < operatingPortionSize) {
                    logD( "прочитано, readBytes ($readBytes) < operatingPortionSize ($operatingPortionSize)")
                    sleepIfNeeded(startTime)
                }
                else if (readBytes < dataSizeToBeCopiedByStep) {
                    logD( "прочитано, readBytes ($readBytes) < dataSizeToBeCopiedByStep ($dataSizeToBeCopiedByStep)")
                    sleepIfNeeded(startTime)
                }
                else if (oneStepDataRead >= dataSizeToBeCopiedByStep) {
                    logD( "прочитано, thisStepDataRead ($oneStepDataRead) >= dataSizeToBeCopiedByStep ($dataSizeToBeCopiedByStep)")
                    sleepIfNeeded(startTime)
                    oneStepDataRead = 0
                }
            }

        } finally {
            workIsRunning.set(false)
        }
    }


    private fun sleepIfNeeded(startTime: Long) {
        if (oneStepDataRead >= dataSizeToBeCopiedByStep) {
            sleepingLackTimeMs(System.currentTimeMillis() - startTime).also {
                if (it > 0) Thread.sleep(it)
            }
        } else {
            logD("спать не нужно")
        }
    }


    private fun sleepingLackTimeMs(stepDurationMs: Long): Long {
        val bytesOverrunPercentage: Float = (oneStepDataRead.toFloat() / dataSizeToBeCopiedByStep)
        return (bytesOverrunPercentage * dataCopyingIntervalMs - stepDurationMs).roundToLong()
    }


    private fun calcSpeed(startTime: Long, startBytes: Long): Long {
        val durationMs = System.currentTimeMillis() - startTime
        val amount = totalDataRead - startBytes
        return if (durationMs > 0) amount / durationMs else 0
    }


    private fun logD(text: String) {
//        Log.d(TAG, "[$uniqueId] $text")
    }

    private fun logDEBUG(text: String, tag: String = TAG) {
//        Log.i(tag, "[$uniqueId] $text")
    }

    companion object {
        val TAG: String = LimitedStreamCopier::class.java.simpleName
    }
}