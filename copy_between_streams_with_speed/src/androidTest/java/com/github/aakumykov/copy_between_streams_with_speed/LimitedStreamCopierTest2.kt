package com.github.aakumykov.copy_between_streams_with_speed

import android.util.Log
import com.github.aakumykov.copy_between_streams_with_speed.stream2stream_copier.LimitedStreamCopier
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


class LimitedStreamCopierTest2 : TestBase() {

    /**
     * План теста:
     *
     * Данные просто копируются [data_simply_copied]
     *
     * Коллбеки [callbacks_are_triggered]:
     *  - вызываются
     *  - коллбек завершения вызывается один раз
     *  - коллбек прогресса вызывается минимум 2 раза
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
     * [test_with_diff_data_size_10_19]
     * [test_with_diff_data_size_20_99]
     *
     * Разные скорости:
     * [test_with_diff_speed_1_9]
     *
     * Разные частоты прогресса:
     * [test_with_diff_rate_1_9]
     */

    @Test
    fun simple_test_for_speed() {
        val dataSize = 100
        val speed = 10
        val rate = 10
        prepareSourceAndTargetFiles(dataSize)
        Log.d(TAG, "старт")
        LimitedStreamCopier(speed, rate).copyFromStreamToStream(
            sourceFileStreamGetNew,
            targetFileStreamGetNew
        )
        Log.d(TAG, "финиш")
        standard_test_with(dataSize, speed, rate)
    }

    @Test
    fun data_simply_copied() {

        val dataSize = 100
        val speed = dataSize * 2
        val progressRate = 1

        prepareSourceAndTargetFiles(dataSize)

        LimitedStreamCopier(speed, progressRate)
            .copyFromStreamToStream(sourceFileStreamGetNew, targetFileStreamGetNew)

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
            .copyFromStreamToStream(sourceFileStreamGetNew, targetFileStreamGetNew,
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
                    inputStream = sourceFileStreamGetNew,
                    outputStream = targetFileStreamGetNew,
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
                    inputStream = sourceFileStreamGetNew,
                    outputStream = targetFileStreamGetNew,
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
                    sourceFileStreamGetNew,
                    targetFileStreamGetNew,
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

        val sourceStream = sourceFileStreamGetNew
        val targetStream = targetFileStreamGetNew

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
        for (dataSize in 1..9) {
            test_with_data_size(dataSize)
        }
    }

    @Test
    fun test_with_diff_data_size_10_19() {
        for (dataSize in 10..19) {
            test_with_data_size(dataSize)
        }
    }

    @Test
    fun test_with_diff_data_size_20_99() {
        for (dataSize in 20..99) {
            test_with_data_size(dataSize)
        }
    }

    private fun test_with_data_size(dataSize: Int) {
        val speed = dataSize * 2
        val rate = 1
        standard_test_with(dataSize, speed, rate)
    }


    @Test
    fun test_with_diff_speed_1_9() {
        for (speed in 1..9) {
            test_with_speed(speed)
        }
    }

    private fun test_with_speed(speed: Int) {
        val dataSize = 1
        val rate = 1
        standard_test_with(dataSize, speed, rate)
    }


    @Test
    fun test_with_diff_rate_1_9() {
        for (rate in 1..9) {
            for (dataSize in 1..9) {
                test_with_rate(dataSize, rate)
            }
        }
    }

    // TODO: разные превышения скорости над частотой
    private fun test_with_rate(dataSize: Int, rate: Int) {
        val speed = 2 * rate
        standard_test_with(dataSize, speed, rate)
    }


    private fun standard_test_with(dataSize: Int, speed: Int, rate: Int) {

        "standard_test_with(dataSize:$dataSize, speed:$speed, rate:$rate)".also {
            println(it)
//            Log.d(TAG, it)
        }

        val finishCallbackWasTriggered = AtomicBoolean(false)
        val progressList = mutableListOf<Long>()
        val speedList = mutableListOf<Long>()

        val sourceData = prepareSourceAndTargetFiles(dataSize)

        LimitedStreamCopier(speed, rate)
            .copyFromStreamToStream(sourceFileStreamGetNew, targetFileStreamGetNew,
                progressCallback = { bytes,speed ->
                    progressList.add(bytes)
                    speedList.add(speed)
                }, finishCallback = { _ ->
                    finishCallbackWasTriggered.set(true)
                })

        delayToAllowCallbackFinish(rate)

        // Проверка данных
        Assert.assertEquals(sourceData, sourceFileContents)
        Assert.assertEquals(sourceData, targetFileContents)

        // Проверка коллбеков
        Assert.assertTrue("Был вызван коллбек завершения",
            finishCallbackWasTriggered.get())

        Log.d(TAG,
            "size: ${dataSize}, " +
                "speed:$speed, " +
                "rate:$rate, " +
                "progressList[${progressList.size}]: ${progressList.joinToString(",")}, " +
                "speedList[${speedList.size}]: ${speedList.joinToString(",")}"
        )

        val minProgressListSize = 1
        val minSpeedListSize = 1

        Assert.assertTrue("Размер списка прогресса (${progressList.size}) >= $minProgressListSize",
            progressList.size >= minProgressListSize)

        Assert.assertTrue("Размер списка скорости >= $minSpeedListSize",
            speedList.size >= minSpeedListSize)

        if (dataSize > 1)
            check_progress_list_is_incremental(progressList)
    }


    @Test
    fun test_progress_callback() {

        val dataSize = 30
        val speed = 10
        val progressRate = 1

        prepareSourceAndTargetFiles(dataSize)

        val progressList = buildList<Long> {
            LimitedStreamCopier(speed, progressRate)
                .copyFromStreamToStream(
                    sourceFileStreamGetNew,
                    targetFileStreamGetNew,
                    progressCallback = { bytes,_ ->
//                        Log.d(TAG, "add(${bytes}), ${this.javaClass.simpleName}")
                        add(bytes)
                    }
                )
        }

        check_progress_list_is_incremental(progressList)
    }



    private fun check_progress_list_is_incremental(progressList: List<Long>) {
        progressList.reduce { acc, nextValue ->
            Assert.assertTrue(
                "Каждое следующее значение в списке прогресса больше предудущаго ($nextValue > $acc)",
                nextValue > acc
            )
            nextValue
        }
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

val currentTimeMs: Long get() = System.currentTimeMillis()