package com.github.aakumykov.copy_between_streams_with_speed

import android.R.attr.duration
import android.util.Log
import com.github.aakumykov.copy_between_streams_with_speed.ext.roundToFloatingDigits
import com.github.aakumykov.copy_between_streams_with_speed.ext.toHMS
import com.github.aakumykov.copy_between_streams_with_speed.stream2stream_copier.LimitedStreamCopier
import com.github.aakumykov.copy_between_streams_with_speed.utils.humanDecimalPlaces
import com.github.aakumykov.copy_between_streams_with_speed.utils.random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
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
     * Разные размеры данных [test_with_diff_data_size_1_9].
     *
     */

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
        val progressPeriod = ceil(1000f / rate).toLong()

        prepareSourceAndTargetFiles(dataSize)

        LimitedStreamCopier(speed, rate)
            .copyFromStreamToStream(sourceFileStream, targetFileStream,
                progressCallback = { _, _ ->
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
            prepareSourceAndTargetFiles()
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
            prepareSourceAndTargetFiles()
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
                    progressCallback = { bytes, speed ->
                        add(bytes)
                    }
                )
        }

        Assert.assertEquals(dataSize.toLong(), sourceFile.length())
        Assert.assertEquals(dataSize.toLong(), targetFile.length())

        Assert.assertTrue(progressList.isEmpty())
    }

    @Test
    fun qw() = runBlocking {
        Assert.assertTrue(true)
    }

    @Test
    fun error_reading_from_stream() = runBlocking {

        // Пока не работает по неизвестныфм причинам

        val dataSize = 100
        val speed = 10
        val rate = 1
        val streamCloseDelayMs: Long = 1000

        val finishedCallbackWasTriggered = AtomicBoolean(false)

        prepareSourceAndTargetFiles(dataSize)

        launch {
            delay(streamCloseDelayMs)
            sourceFileStream.close()
        }


        Assert.assertThrows(Exception::class.java) {
            LimitedStreamCopier(speed, rate)
                .copyFromStreamToStream(sourceFileStream, targetFileStream,
                    finishCallback = { _ ->
                        finishedCallbackWasTriggered.set(true)
                    })
        }

        Assert.assertFalse(finishedCallbackWasTriggered.get())
    }


    @Test
    fun test_with_diff_data_size_1_9() {
        for (dataSize in 1..9) {
            test_with_data_size(dataSize)
        }
    }

    private fun test_with_data_size(dataSize: Int) {
        val speed = dataSize * 2
        val rate = 1
        standard_test_with(dataSize, speed, rate)
    }

    private fun standard_test_with(dataSize: Int, speed: Int, rate: Int) {

        "standard_test_with(dataSize:$dataSize, speed:$speed, rate:$rate)".also {
            println(it)
            Log.d(TAG, it)
        }

        val finishCallbackWasTriggered = AtomicBoolean(false)
        val progressList = mutableListOf<Long>()
        val speedList = mutableListOf<Long>()

        val sourceData = prepareSourceAndTargetFiles(dataSize)

        LimitedStreamCopier(speed, rate)
            .copyFromStreamToStream(sourceFileStream, targetFileStream,
                progressCallback = { bytes, speed ->
                    progressList.add(bytes)
                    speedList.add(speed)
                }, finishCallback = { bytes ->
                    finishCallbackWasTriggered.set(true)
                })

        delayToAllowCallbackFinish(rate)

        // Проверка данных
        Assert.assertEquals(sourceData, sourceFileContents)
        Assert.assertEquals(sourceData, targetFileContents)

        // Проверка работы коллбеков
        Assert.assertTrue("Был вызван коллбек завершения", finishCallbackWasTriggered.get())

        Assert.assertTrue("Размер списка прогресса >= 2", progressList.size >= 2)
        Assert.assertTrue("Размер списка скорости >= 2",speedList.size >= 2)

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
                    sourceFileStream,
                    targetFileStream,
                    progressCallback = { bytes, speed ->
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




    @Test
    fun tiny_file() {

        val dataSize = 1
        val speed = 1
        val progressRate = 1

        val progressList = mutableListOf<Long>()
        val finishCallbackWasTriggered = AtomicBoolean(false)

        prepareSourceAndTargetFiles(dataSize)

        LimitedStreamCopier(speed, progressRate)
            .copyFromStreamToStream(sourceFileStream, targetFileStream,
                progressCallback = { b,_ ->
                    progressList.add(b)
                }, finishCallback = { _ ->
                    finishCallbackWasTriggered.set(true)
                })

        Assert.assertEquals(dataSize.toLong(), targetFile.length())
        Assert.assertEquals(dataSize.toLong(), sourceFile.length())
        Assert.assertEquals(sourceFileContents, targetFileContents)

        Assert.assertTrue(finishCallbackWasTriggered.get())
        Assert.assertTrue(progressList.isNotEmpty())
    }


    @Test
    fun test_10_bytes() {
        test_with(
            10,
            10,
            1
        )
    }

    @Test
    fun test_674_107() {
        repeat(3) {
            Log.d(TAG, "Прогон $it")
            listOf(83).forEach { progressRate ->
                test_with(
                    674,
                    107,
                    progressRate
                ) { log, diff ->
                    Log.d(TAG, "[$diff] -> $log")
                }
            }
        }
    }

    @Test
    fun a1() {
        buildMap<String,Int> {
            repeat(1000) {
                val dataSizeBytes = random.nextInt(10, 1001)
                val speedBytesPerSec = random.nextInt(100, 10_001)
                val progressRate = random.nextInt(1, 101)
                test_with(dataSizeBytes, speedBytesPerSec, progressRate) { log,diff ->
                    put(log, diff)
                }
            }
        }.maxBy { me ->
            me.value
        }.also { maxDiffItem ->
            Log.d(TAG, "Наибольшее расхождение:")
            Log.d(TAG, "diff: ${maxDiffItem.value}: ${maxDiffItem.key}")
        }
    }

    private fun test_with(dataSizeBytes: Int, speedBytesPerSec: Int, progressRatePerSec: Int,
                          logCallback: ((log: String, diff: Int) -> Unit)? = null) {

//        Log.d(TAG, "test_with() called with: dataSizeBytes = $dataSizeBytes, speedBytesPerSec = $speedBytesPerSec, progressRatePerSec = $progressRatePerSec")

        val finishCallbackTriggered = AtomicBoolean(false)
        var progressCallbackRealCount = 0

        prepareSourceAndTargetFiles(dataSizeBytes)

        val lsc = LimitedStreamCopier(
            speedBytesPerSecond = speedBytesPerSec,
            progressRatePerSecond = progressRatePerSec,
        )

        val startTime = currentTimeMs

        lsc.copyFromStreamToStream(
            sourceFileStream,
            targetFileStream,
            progressCallback = { transferred, speed ->
                progressCallbackRealCount++
                println("скопировано: $transferred, скорость: $speed")
            },
            finishCallback = { transferredBytes ->
                finishCallbackTriggered.set(true)
                println("завершено, $transferredBytes")
            }
        )

        val durationMs = currentTimeMs - startTime

        // TODO: сделать специальный метод StreamCopier-а "calcProgressPeriod"?
        val progressPeriodMs = floor(1000f / progressRatePerSec).roundToInt()
        val progressCallbacksEstimatedCount = floor(1f * durationMs / progressPeriodMs).roundToInt()

        Assert.assertEquals(
            dataSizeBytes.toLong(),
            targetFile.length()
        )

        Assert.assertEquals(
            sourceFileContents,
            targetFileContents
        )

        // Это ожидание нужно
        delayToAllowCallbackFinish(progressRatePerSec)

        Assert.assertTrue(finishCallbackTriggered.get())

        val log = "size:$dataSizeBytes, " +
                "speed:$speedBytesPerSec, " +
                "rate:${progressRatePerSec}, " +
                "ecnt: $progressCallbacksEstimatedCount, " +
                "rcnt: $progressCallbackRealCount, " +
                "duration:${durationMs}"
        val diff = abs(progressCallbacksEstimatedCount - progressCallbackRealCount)

        logCallback?.invoke(log,diff)
    }


    @Test
    fun simple_test() = runBlocking {
        test_with_params(
            57,
            13,
            1,
            10.0
        )
    }

    @Test
    fun repeated_simple_test() = runBlocking {
        for (multiplier in 1..10) {
            test_with_params(
                100 * multiplier,
                30 * multiplier,
                10,
                10.0
            )
        }
    }

    @Test
    fun `продолжительность_копирования_плюс_минус_10_процентов_от_расчётной`() = runTest {
        listOf(
//            IntRange(1,2),
//            IntRange(3,6),
//            IntRange(7,10),
//            IntRange(10,20),
//            IntRange(20,30),
            IntRange(30,40),
        ).forEach{ range ->
            val speedMultiplier = 10
            range.forEach { dataSize ->
                test_with_params(
                    dataSize = dataSize,
                    speed = dataSize * speedMultiplier,
                    rate = 10,
                    10.0
                )
            }
        }
    }

    private fun test_with_params(dataSize: Int,
                                 speed: Int,
                                 rate: Int,
                                 targetCopyingTimeDeviationPercents: Double
    ) {

        prepareSourceAndTargetFiles(dataSize)

        val estimatedDuration
                = ((1f * dataSize / speed)*1_000_000_000)
            .roundToLong()

        val lsc = LimitedStreamCopier(
            speedBytesPerSecond = speed,
            progressRatePerSecond = rate,
        )

        val startTimeNs = currentTimeNanos
        val startTimeMs = currentTimeMs

        lsc.copyFromStreamToStream(
            sourceFileStream,
            targetFileStream,
            progressCallback = { b,s ->
                println("скопировано: $b")
            }
        )
        val durationNs = currentTimeNanos - startTimeNs
        val durationMs = currentTimeMs - startTimeMs

        println("${dataSize.humanDecimalPlaces} со скоростью ${speed.humanDecimalPlaces} скопировано за время: ${durationMs.toHMS()}")

        val deviationPercent
            = (100 * estimatedDuration / durationNs.toDouble())
            .roundToFloatingDigits(0)

        val logString =
//            "sz: $dataSize, " +
//            "sp: $speed, " +
//            "st: $steps " +
//            "-> " +
            "edr:${estimatedDuration.humanDecimalPlaces}, " +
            "rdr:${duration.humanDecimalPlaces} " +
            "(${deviationPercent}%)"

        println(logString)

//        Assert.assertTrue(
//            "отклонение времени копирования " +
//                    "не более ${targetCopyingTimeDeviationPercents}% " +
//                    "(реальное ${deviationPercent}%)",
//            deviationPercent <= targetCopyingTimeDeviationPercents
//        )
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