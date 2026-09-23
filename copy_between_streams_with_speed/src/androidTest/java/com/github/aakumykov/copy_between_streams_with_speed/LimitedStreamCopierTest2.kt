package com.github.aakumykov.copy_between_streams_with_speed

import android.util.Log
import com.github.aakumykov.copy_between_streams_with_speed.ext.roundToFloatingDigits
import com.github.aakumykov.copy_between_streams_with_speed.stream2stream_copier.LimitedStreamCopier
import com.github.aakumykov.copy_between_streams_with_speed.utils.KILOBYTES
import com.github.aakumykov.copy_between_streams_with_speed.utils.MEGABYTES
import com.github.aakumykov.copy_between_streams_with_speed.utils.humanDecimalPlaces
import com.github.aakumykov.copy_between_streams_with_speed.utils.humanSizeBinary
import com.github.aakumykov.copy_between_streams_with_speed.utils.random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong


class LimitedStreamCopierTest2 : TestBase() {

    /**
     * План теста:
     *
     * Данные просто копируются [data_simply_copied]
     *
     * Коллбеки [callbacks_are_triggered]:
     *  - вызываются
     *  - коллбек завершения вызывается один раз
     *  - коллбек прогресса вызывается минимум 1 раз
     *
     * Ограничения работают:
     *   - исключение при нулевой скорости [throws_exception_on_zero_speed]
     *   - исключение при отрицательной скорости [throws_exception_on_negative_speed]
     *   - исключение при отрицательном нулевом количестве шагов в секунду [throws_exception_on_zero_rate]
     *   - исключение при отрицательном количестве шагов в секунду [throws_exception_on_negative_rate]
     *
     * Файл нулевого размера [zero_size_file]:
     *  - исходный и целевой файлы остаются нулевого размера.
     *  - массив прогресса пустой.
     *
     * Ошибка чтения потока [error_reading_from_stream].
     * Ошибка записи в поток [error_writing_to_stream].
     *
     * Разные размеры данных:
     * [test_with_diff_data_size_1_9]
     * [test_with_diff_data_size_10_99]
     * [test_with_diff_data_size_100_999]
     * [test_with_diff_data_size_1000_9999]
     *
     * Разные скорости:
     * [test_with_diff_speed_1_9]
     * [test_with_diff_speed_10_99]
     * [test_with_diff_speed_100_999]
     * [test_with_diff_speed_1000_9999]
     *
     * Разные частоты прогресса:
     * [test_with_diff_rate_1_9]
     * [test_with_diff_rate_10_99]
     * [test_with_diff_rate_100_999]
     * [test_with_diff_rate_1000_9999]
     *
     * Фиксированный размер с разными скоростями и частотами:
     * [fixed_data_size_10_with_diff_speed_and_rate]
     * [fixed_data_size_100_with_diff_speed_and_rate]
     * [fixed_data_size_1000_with_diff_speed_and_rate]
     *
     * Фиксированная скорость с разными размерами и частотами:
     * [fixed_speed_10_with_diff_size_and_rate]
     * [fixed_speed_100_with_diff_size_and_rate]
     * [fixed_speed_1000_with_diff_size_and_rate]
     *
     * Фиксированная частота с разными размерами и скоростями:
     * [fixed_rate_10_with_diff_size_and_speed]
     * [fixed_rate_100_with_diff_size_and_speed]
     * [fixed_rate_1000_with_diff_size_and_speed]
     *
     * Копирование большаго файла разными вариациями:
     * [big_file_with_variations]
     */

    @Test
    fun test_data_size_hundreds_bytes() {
        for(base in 1.. 9) {
            val dataSize = base * 100
            val speed = dataSize * 10
            val rate = 1
            standard_test_with(dataSize,speed,rate)
        }
    }

    @Test
    fun test_data_size_kilobytes() {
        for(base in 1..10) {
            val dataSize = base.KILOBYTES
            val speed = dataSize * 10
            val rate = 1
            standard_test_with(dataSize,speed,rate)
        }
    }

    @Test
    fun simple_test_for_speed() {
        for (sizeBase in listOf(1, 10, 100, 500, 1000)) {
            val dataSize = sizeBase.KILOBYTES
            val speed = 5 * dataSize
            val rate = 1
            prepareSourceAndTargetFiles(dataSize)
            Log.d(TAG, "simple_test_for_speed(sizeBase:$sizeBase), старт")
            LimitedStreamCopier(speed, rate).copyFromStreamToStream(
                sourceFileStream,
                targetFileStream
            )
            Log.d(TAG, "simple_test_for_speed(sizeBase:$sizeBase), финиш")
            Log.d(TAG, "")
            standard_test_with(dataSize, speed, rate)
        }
    }

    @Test
    fun data_simply_copied() {

        val dataSize = 100
        val speed = dataSize * 2
        val progressRate = 1

        prepareSourceAndTargetFiles(dataSize)

        LimitedStreamCopier(speed, progressRate)
            .copyFromStreamToStream(sourceFileStream, targetFileStream)

        Assert.assertEquals(dataSize.toLong(), targetFile.length())
        Assert.assertEquals(dataSize.toLong(), sourceFile.length())
        Assert.assertEquals(sourceFileContents, targetFileContents)
    }


    @Test
    fun callbacks_are_triggered() = runBlocking {

        val progressCallbackCount = AtomicInteger(0)
        val finishCallbackCount = AtomicInteger(0)

        val dataSize = 100
        val speed = 30
        val rate = 1

        prepareSourceAndTargetFiles(dataSize)

        LimitedStreamCopier(speed, rate)
            .copyFromStreamToStream(sourceFileStream, targetFileStream,
                progressCallback = { _,_ ->
                    progressCallbackCount.getAndIncrement()
                },
                finishCallback = { _ ->
                    finishCallbackCount.getAndIncrement()
                }
            )

        delayToAllowCallbackFinish(rate)

        Assert.assertTrue(progressCallbackCount.get() >= 2)
        Assert.assertTrue(finishCallbackCount.get() == 1)
    }



    @Test
    fun throws_exception_on_zero_speed() {
        checkOnExceptionWithSpeed(0)
    }

    @Test
    fun throws_exception_on_negative_speed() {
        checkOnExceptionWithSpeed(-1)
    }

    private fun checkOnExceptionWithSpeed(speed: Int) {
        Assert.assertThrows(IllegalArgumentException::class.java) {
            prepareSourceAndTargetFiles(1)
            runBlocking {
                LimitedStreamCopier(speed, 1).copyFromStreamToStream(
                    inputStream = sourceFileStream,
                    outputStream = targetFileStream,
                )
            }
        }
    }


    @Test
    fun throws_exception_on_zero_rate() {
        checkOnExceptionWithRate(0)
    }

    @Test
    fun throws_exception_on_negative_rate() {
        checkOnExceptionWithRate(-1)
    }

    private fun checkOnExceptionWithRate(rate: Int) {
        Assert.assertThrows(IllegalArgumentException::class.java) {
            prepareSourceAndTargetFiles(1)
            runBlocking {
                LimitedStreamCopier(1, rate).copyFromStreamToStream(
                    inputStream = sourceFileStream,
                    outputStream = targetFileStream,
                )
            }
        }
    }


    @Test
    fun zero_size_file() {

        val dataSize = 0
        val speed = 10
        val progressRate = 1

        prepareSourceAndTargetFiles(dataSize)

        val progressList = buildList<Long> {
            LimitedStreamCopier(speed, progressRate)
                .copyFromStreamToStream(
                    sourceFileStream,
                    targetFileStream,
                    progressCallback = { bytes,_ ->
                        add(bytes)
                    }
                )
        }

        Assert.assertEquals(dataSize.toLong(), sourceFile.length())
        Assert.assertEquals(dataSize.toLong(), targetFile.length())

        Assert.assertTrue(progressList.isEmpty())
    }


    @Test
    fun error_reading_from_stream() = runBlocking {
        test_error_behaviour(this) { sourceStream, _ ->
            sourceStream.close()
        }
    }

    @Test
    fun error_writing_to_stream() = runBlocking {
        test_error_behaviour(this) { _, targetStream ->
            targetStream.close()
        }
    }

    private fun test_error_behaviour(
        scope: CoroutineScope,
        errorTrigger: (sourceStream: InputStream, targetStream: OutputStream) -> Unit
    ) {

        val dataSize = 1000
        val speed = 100
        val errorDelayMs: Long = 1000

        val finishCallbackWasTriggered = AtomicBoolean(false)

        prepareSourceAndTargetFiles(dataSize)

        val sourceStream = sourceFileStream
        val targetStream = targetFileStream

        scope.launch (Dispatchers.IO) {
            delay(errorDelayMs)
            errorTrigger.invoke(sourceStream, targetStream)
        }

        Assert.assertThrows(Exception::class.java) {
            LimitedStreamCopier(speed, 1).copyFromStreamToStream(
                inputStream = sourceStream,
                outputStream = targetStream,
            )
        }

        Assert.assertFalse(finishCallbackWasTriggered.get())
    }


    @Test
    fun test_with_diff_data_size_1_9() {
        repeat_with_params(1..9, 1,0){ dataSize ->
            standard_test_with(dataSize, 1, 1)
        }
    }

    @Test
    fun test_with_diff_data_size_10_99() {
        repeat_with_params(10..99, 10,5){ dataSize ->
            standard_test_with(dataSize, dataSize * 2, 10)
        }
    }

    @Test
    fun test_with_diff_data_size_100_999() {
        repeat_with_params(100..999, 100,50){ dataSize ->
            standard_test_with(dataSize, dataSize * 2, 10)
        }
    }

    @Test
    fun test_with_diff_data_size_1000_9999() {
        repeat_with_params(1000..9999, 1000,500){ dataSize ->
            standard_test_with(dataSize, dataSize * 2, 10)
        }
    }



    @Test
    fun test_with_diff_speed_1_9() {
        repeat_with_params(1..9, 1, randomSize = 0) { speed ->
            standard_test_with(10, speed, speed)
        }
    }

    @Test
    fun test_with_diff_speed_10_99() {
        repeat_with_params(10..99, 10, 5) { speed ->
            standard_test_with(10, speed, 10)
        }
    }

    @Test
    fun test_with_diff_speed_100_999() {
        repeat_with_params(100..999, 100, 50) { speed ->
            standard_test_with(10, speed, 10)
        }
    }

    @Test
    fun test_with_diff_speed_1000_9999() {
        repeat_with_params(1000..9999, 1000, 500) { speed ->
            standard_test_with(10, speed, 10)
        }
    }


    @Test
    fun test_with_diff_rate_1_9() {
        repeat_with_params(1..9, 1, randomSize = 0) { rate ->
            standard_test_with(10, 10, rate)
        }
    }

    @Test
    fun test_with_diff_rate_10_99() {
        repeat_with_params(10..99, 10, 5) { rate ->
            standard_test_with(10, 100, rate)
        }
    }

    @Test
    fun test_with_diff_rate_100_999() {
        repeat_with_params(100..999, 100, 50) { rate ->
            standard_test_with(10, 1000, rate)
        }
    }

    @Test
    fun test_with_diff_rate_1000_9999() {
        repeat_with_params(1000..9999, 1000, 500) { rate ->
            standard_test_with(10, 100_000, rate)
        }
    }


    @Test
    fun fixed_data_size_10_with_diff_speed_and_rate() {
        val dataSize = 10
        val otherParamsRange = 1..9
        val otherParamsStep = 1
        repeat_with_params(otherParamsRange, otherParamsStep) { speed ->
            repeat_with_params(otherParamsRange, otherParamsStep) { rate ->
                val realRate = min(speed, rate)
                standard_test_with(dataSize, speed, realRate)
            }
        }
    }

    @Test
    fun fixed_data_size_100_with_diff_speed_and_rate() {
        val dataSize = 100
        val otherParamsRange = 10..99
        val otherParamsStep = 10
        repeat_with_params(otherParamsRange, otherParamsStep) { speed ->
            repeat_with_params(otherParamsRange, otherParamsStep) { rate ->
                val realRate = min(speed, rate)
                standard_test_with(dataSize, speed, realRate)
            }
        }
    }

    @Test
    fun fixed_data_size_1000_with_diff_speed_and_rate() {
        val dataSize = 100
        val otherParamsRange = 1000..9999
        val otherParamsStep = 1000
        repeat_with_params(otherParamsRange, otherParamsStep) { speed ->
            repeat_with_params(otherParamsRange, otherParamsStep) { rate ->
                val realRate = min(speed, rate)
                standard_test_with(dataSize, speed, realRate)
            }
        }
    }


    @Test
    fun fixed_speed_10_with_diff_size_and_rate() {
        val speed = 10
        val otherParamsRange = 1..9
        val otherParamsStep = 1
        repeat_with_params(otherParamsRange, otherParamsStep) { dataSize ->
            repeat_with_params(otherParamsRange, otherParamsStep) { rate ->
                val realRate = min(speed, rate)
                standard_test_with(dataSize, speed, realRate)
            }
        }
    }

    @Test
    fun fixed_speed_100_with_diff_size_and_rate() {
        val speed = 100
        val otherParamsRange = 10..99
        val otherParamsStep = 10
        repeat_with_params(otherParamsRange, otherParamsStep) { dataSize ->
            repeat_with_params(otherParamsRange, otherParamsStep) { rate ->
                val realRate = min(speed, rate)
                standard_test_with(dataSize, speed, realRate)
            }
        }
    }

    @Test
    fun fixed_speed_1000_with_diff_size_and_rate() {
        val speed = 1000
        val otherParamsRange = 100..999
        val otherParamsStep = 100
        repeat_with_params(otherParamsRange, otherParamsStep) { dataSize ->
            repeat_with_params(otherParamsRange, otherParamsStep) { rate ->
                val realRate = min(speed, rate)
                standard_test_with(dataSize, speed, realRate)
            }
        }
    }



    @Test
    fun fixed_rate_10_with_diff_size_and_speed() {
        val rate = 10
        val otherParamsRange = 1..9
        val otherParamsStep = 1
        repeat_with_params(otherParamsRange, otherParamsStep) { dataSize ->
            repeat_with_params(otherParamsRange, otherParamsStep) { speed ->
                val realRate = min(speed, rate)
                standard_test_with(dataSize, speed, realRate)
            }
        }
    }

    @Test
    fun fixed_rate_100_with_diff_size_and_speed() {
        val rate = 100
        val otherParamsRange = 10..99
        val otherParamsStep = 10
        repeat_with_params(otherParamsRange, otherParamsStep) { dataSize ->
            repeat_with_params(otherParamsRange, otherParamsStep) { speed ->
                val realRate = min(speed, rate)
                standard_test_with(dataSize, speed, realRate)
            }
        }
    }

    @Test
    fun fixed_rate_1000_with_diff_size_and_speed() {
        val rate = 100
        val otherParamsRange = 100..999
        val otherParamsStep = 100
        repeat_with_params(otherParamsRange, otherParamsStep) { dataSize ->
            repeat_with_params(otherParamsRange, otherParamsStep) { speed ->
                val realRate = min(speed, rate)
                standard_test_with(dataSize, speed, realRate)
            }
        }
    }


    @Test
    fun big_file_with_variations() {
        repeat(10) { i ->
            val dataSize = 1.MEGABYTES * random.nextInt(10)
            val speed = 1.KILOBYTES * random.nextInt(100, 1001)
            val rate = random.nextInt(1, 100)
            standard_test_with(dataSize, speed, rate)
        }
    }


    private fun repeat_with_params(range: IntRange,
                                   step: Int,
                                   randomSize: Int = floor(step/2f).roundToInt(),
                                   action: (value:Int) -> Unit) {
        var base = range.first
        while(base <= range.last) {
            val value = base + (if (randomSize > 0) random.nextInt(randomSize) else 0)
            action.invoke(value)
            base += step
        }
    }

    private fun standard_test_with(dataSizeBytes: Int, speedBytesPerSec: Int, progressRatePerSec: Int) {

        "standard_test_with(dataSize:$dataSizeBytes, speed:$speedBytesPerSec, rate:$progressRatePerSec)".also {
//            println(it)
//            Log.d(TAG, it)
        }

        val finishCallbackWasTriggered = AtomicBoolean(false)
        val progressList = mutableListOf<Long>()
        val speedList = mutableListOf<Long>()

        val sourceData = prepareSourceAndTargetFiles(dataSizeBytes)


        val estimatedCopyTimeMs: Long = (1000f * dataSizeBytes / speedBytesPerSec).roundToLong()

        val startTimeMs = System.currentTimeMillis()

        LimitedStreamCopier(speedBytesPerSec, progressRatePerSec)
            .copyFromStreamToStream(sourceFileStream, targetFileStream,
                progressCallback = { bytes,speed ->
                    progressList.add(bytes)
                    speedList.add(speed)
                }, finishCallback = { _ ->
                    finishCallbackWasTriggered.set(true)
                })

        val realCopyTimeMs = System.currentTimeMillis() - startTimeMs
        val timeDiffMs = estimatedCopyTimeMs - realCopyTimeMs
        val timeDiffPercents = (1.toDouble() * timeDiffMs / estimatedCopyTimeMs).roundToFloatingDigits(2)

        delayToAllowCallbackFinish(progressRatePerSec)

        // Проверка данных
        Assert.assertEquals(sourceData, sourceFileContents)
        Assert.assertEquals(sourceData, targetFileContents)

        // Проверка коллбеков
        Assert.assertTrue("Был вызван коллбек завершения",
            finishCallbackWasTriggered.get())

        val msg = "sz:${dataSizeBytes.humanSizeBinary()}, " +
                "sp:${speedBytesPerSec.humanSizeBinary()}, " +
                "rt:$progressRatePerSec, " +
                "est.time:${estimatedCopyTimeMs.humanDecimalPlaces}, " +
                "real.time:${realCopyTimeMs.humanDecimalPlaces}, " +
                "t.diff:${timeDiffMs} (${timeDiffPercents}%), " +
                "prList[${progressList.size}]: ${progressList.joinToString(",")}, " +
                "spList[${speedList.size}]: ${speedList.joinToString(",")}"
        Log.d(TAG,msg)

        val minimumProgressListSize = 1
        val minimumSpeedListSize = 1

        Assert.assertTrue("Размер списка прогресса (${progressList.size}) >= $minimumProgressListSize",
            progressList.size >= minimumProgressListSize)

        Assert.assertTrue("Размер списка скорости >= $minimumSpeedListSize",
            speedList.size >= minimumSpeedListSize)

        if (dataSizeBytes > 1) {
            progressList.reduce { acc, nextValue ->
                Assert.assertTrue(
                    "Каждое следующее значение в списке прогресса больше предыдущего ($nextValue > $acc)",
                    nextValue > acc
                )
                nextValue
            }
        }

        Assert.assertEquals(
            "Последнее значение списка прогресса (${progressList.last()}) == размеру данных ($dataSizeBytes)",
            dataSizeBytes.toLong(),
            progressList.last()
        )
    }


    private fun delayToAllowCallbackFinish(progressRate: Int) {
        val timeout = 3 * ceil(1000f / progressRate).toLong()
        TimeUnit.MILLISECONDS.sleep(timeout)
    }

    private val currentTimeMs: Long
        get() = System.currentTimeMillis()

    private val currentTimeNanos: Long
        get() = System.currentTimeMillis() + System.nanoTime()

    companion object {
        val TAG: String = LimitedStreamCopierTest2::class.java.simpleName
    }
}

