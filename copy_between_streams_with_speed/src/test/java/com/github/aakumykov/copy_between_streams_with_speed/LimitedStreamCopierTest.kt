package com.github.aakumykov.copy_between_streams_with_speed

import android.util.Log
import com.github.aakumykov.copy_between_streams_with_speed.LimitedStreamCopierNs.Companion.NANOS_IN_SECOND
import com.github.aakumykov.copy_between_streams_with_speed.ext.percentOf
import com.github.aakumykov.copy_between_streams_with_speed.ext.roundToFloatingDigits
import com.github.aakumykov.copy_between_streams_with_speed.utils.KILOBYTES
import com.github.aakumykov.copy_between_streams_with_speed.utils.MEGABYTES
import com.github.aakumykov.copy_between_streams_with_speed.utils.currentTimeMs
import com.github.aakumykov.copy_between_streams_with_speed.utils.currentTimeNanos
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


class LimitedStreamCopierTest : TestBase() {

    private val limitedStreamCopier = LimitedStreamCopierNs()

    // FIXME: проверять количество шагов в прогрессе!
    /**
     * [test_low_data_size]
     * [test_data_size_hundreds_bytes]
     * [test_data_size_kilobytes]
     * [simple_test_100kb_30kb_with_specific_steps]
     * [simple_test_for_speed]
     * [simple_test_100kb_30kb_with_diff_steps]
     *
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
     * Время копирования:
     * [time_test_on_single_params_set]
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



    /**
     * Этот тест ниже был сбойным...
     * [test_with_diff_speed_100_999]
     */
    @Test
    fun test_low_data_size() {
        repeat(10) { i ->
            logD("================= Прогон ${i+1} =================")
            standard_test_with(
                10,
                126,
                10
            )
        }
    }


    @Test
    fun test_data_size_hundreds_bytes() {
        for(base in 1.. 9) {
            val dataSize = base * 100
            val speed = dataSize * 10
            val rate = 1
            standard_test_with(dataSize, speed, rate,)
        }
    }

    @Test
    fun test_data_size_kilobytes() {
        for(base in 1..10) {
            val dataSize = base.KILOBYTES
            val speed = dataSize * 10
            val rate = 1
            standard_test_with(dataSize, speed, rate,)
        }
    }

    @Test
    fun simple_test_100kb_30kb_with_specific_steps() {

        val dataSize = 100.KILOBYTES
        val speed = 30.KILOBYTES
        val progressRate = 1
        val stepsPerSecond = 5

        standard_test_with(
            dataSize,
            speed,
            progressRate,
            stepsPerSecond = stepsPerSecond
        )
    }

    @Test
    fun simple_test_100kb_30kb_with_diff_steps() {
//        listOf(1,2,3,4,5,6,7,8,9,10).forEach { steps ->
        for (steps in 1..10 step 1) {
            logD("steps: $steps")

            val dataSize = 100.KILOBYTES
            val speed = 30.KILOBYTES
            val progressRate = 1

            standard_test_with(
                dataSize,
                speed,
                progressRate,
                stepsPerSecond = steps
            )

            logD("")
        }
    }

    @Test
    fun simple_test_for_speed() {
        for (sizeBase in listOf(1, 10, 100, 500, 1000)) {
            val dataSize = sizeBase.KILOBYTES
            val speed = dataSize / 3
            val rate = 1
            prepareSourceAndTargetFiles(dataSize)
            logD( "simple_test_for_speed(sizeBase:$sizeBase), старт")
            limitedStreamCopier.copyFromStreamToStream(
                newSourceFileStream,
                newTargetFileStream,
                speed,
                rate
            )
            logD( "simple_test_for_speed(sizeBase:$sizeBase), финиш")
            standard_test_with(dataSize, speed, rate,)
        }
    }

    @Test
    fun data_simply_copied() {

        val dataSize = 101
        val speed = dataSize * 2
        val progressRate = 1

        prepareSourceAndTargetFiles(dataSize)

        limitedStreamCopier.copyFromStreamToStream(newSourceFileStream, newTargetFileStream,
            speed, progressRate)

        Assert.assertEquals(dataSize.toLong(), targetFile.length())
        Assert.assertEquals(dataSize.toLong(), sourceFile.length())
        Assert.assertEquals(newSourceFileContents, newTargetFileContents)
    }


    @Test
    fun callbacks_are_triggered() = runBlocking {

        val progressCallbackCount = AtomicInteger(0)
        val finishCallbackCount = AtomicInteger(0)

        val dataSize = 100
        val speed = 30
        val rate = 1

        prepareSourceAndTargetFiles(dataSize)

        limitedStreamCopier
            .copyFromStreamToStream(newSourceFileStream, newTargetFileStream,
                speed, rate,
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
                limitedStreamCopier.copyFromStreamToStream(
                    inputStream = newSourceFileStream,
                    outputStream = newTargetFileStream,
                    speed,
                    1
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
                limitedStreamCopier.copyFromStreamToStream(
                    inputStream = newSourceFileStream,
                    outputStream = newTargetFileStream,
                    1,
                    rate
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

        val progressList = buildList {
            limitedStreamCopier
                .copyFromStreamToStream(
                    newSourceFileStream,
                    newTargetFileStream,
                    speed,
                    progressRate,
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

    @Test
    fun time_test_on_single_params_set() {

        val dataSizeBytes = 2.KILOBYTES
        val speedBytesPerSec = 1.KILOBYTES
        val expectedDurationNs = NANOS_IN_SECOND * dataSizeBytes / speedBytesPerSec

        val startTime = currentTimeNanos
        freshLimitedStreamCopier
            .copyFromStreamToStream(
                inputStream = newSourceFileStream,
                outputStream = newTargetFileStream,
                speedBytesPerSec,
            )
        val durationNs = currentTimeNanos - startTime

        val requiredDiffPercent = 20.0
        val realDiffPercents = durationNs.percentOf(expectedDurationNs)

        Assert.assertTrue(
            "Время копирования отличается не более чем на ${requiredDiffPercent}% (реально на ${realDiffPercents}%)",
            realDiffPercents <= requiredDiffPercent
        )
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

        val sourceStream = newSourceFileStream
        val targetStream = newTargetFileStream

        scope.launch (Dispatchers.IO) {
            delay(errorDelayMs)
            errorTrigger.invoke(sourceStream, targetStream)
        }

        Assert.assertThrows(Exception::class.java) {
            limitedStreamCopier.copyFromStreamToStream(
                inputStream = sourceStream,
                outputStream = targetStream,
                speed,
                1
            )
        }

        Assert.assertFalse(finishCallbackWasTriggered.get())
    }


    @Test
    fun test_with_diff_data_size_1_9() {
        repeat_with_params(1..9, 1,0){ dataSize ->
            standard_test_with(dataSize, 1, 1)
            TimeUnit.SECONDS.sleep(1)
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
            standard_test_with(
                10,
                speed,
                10
            )
        }
    }

    /**
     * При разных скоростях появляются сбои...
     * Одиночный тест проходит [test_low_data_size] нормально...
     */
    @Test
    fun test_with_diff_speed_100_999() {
        repeat_with_params(100..999, 100, 50) { speed ->
            standard_test_with(
                10,
                speed,
                10
            )
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
            logI("===== прогон ${i+1} =====")
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

    private fun standard_test_with(
        dataSizeBytes: Int,
        speedBytesPerSec: Int,
        progressRatePerSec: Int,
        stepsPerSecond: Int = 1000
    ) {
        val finishCallbackWasTriggered = AtomicBoolean(false)
        val progressList = mutableListOf<Long>()
        val speedList = mutableListOf<Long>()

        val sourceData = prepareSourceAndTargetFiles(dataSizeBytes)


        val estimatedDurationSec: Float = (1f * dataSizeBytes / speedBytesPerSec)
        val estimatedDurationNs: Double = estimatedDurationSec * NANOS_IN_SECOND
        logD("estimatedDurationNs: ${estimatedDurationNs.humanDecimalPlaces} ($estimatedDurationSec sec)")

        val startTimeNs: Long = currentTimeNanos
        logD("start:  ${startTimeNs.humanDecimalPlaces}")

        limitedStreamCopier
            .copyFromStreamToStream(
                newSourceFileStream,
                newTargetFileStream,
                speedBytesPerSec,
                progressRatePerSec,
                stepsPerSecond = stepsPerSecond,
                progressCallback = { bytes, speed ->
                    logI("progress [${currentTimeMs.humanDecimalPlaces}]: $bytes")
                    progressList.add(bytes)
                    speedList.add(speed)
                }, finishCallback = { _ ->
                    finishCallbackWasTriggered.set(true)
                })

        val finishTime: Long = currentTimeNanos
        logD("finish: ${finishTime.humanDecimalPlaces}")

        val realDurationNs: Long = finishTime - startTimeNs
        logD("duration: ${realDurationNs.humanDecimalPlaces}")

        val timeDiffNs: Double = realDurationNs - estimatedDurationNs
        logD("timeDiffNs: ${timeDiffNs.humanDecimalPlaces}")

        val timeDiffPercents = realDurationNs
            .percentOf(estimatedDurationNs)
            .roundToFloatingDigits(2)

        delayToAllowCallbackFinish(progressRatePerSec)

        // Проверка данных
        Assert.assertEquals(sourceData, newSourceFileContents)
        Assert.assertEquals(sourceData, newTargetFileContents)

        // Проверка коллбеков
        Assert.assertTrue("Был вызван коллбек завершения",
            finishCallbackWasTriggered.get())


        val msgMedium = "\n" +
            "sz:${dataSizeBytes.humanDecimalPlaces},\n" +
            "sp:${speedBytesPerSec.humanDecimalPlaces},\n" +
            "rt:$progressRatePerSec,\n" +
            "stp:$stepsPerSecond,\n" +
            "est.time:${estimatedDurationNs.humanDecimalPlaces},\n" +
            "real.time:${realDurationNs.humanDecimalPlaces} (${timeDiffPercents}%),\n" +
            "t.diff:${timeDiffNs},\n"

        val progressMsg =
                "prList[${progressList.size}]: ${progressList.joinToString(",")},\n" +
                "spList[${0}]: ${speedList.map { "${it.humanSizeBinary()}/с" }.joinToString(",")},\n"

        logI(msgMedium)

        val minimumProgressListSize = 1
        val minimumSpeedListSize = 1

        Assert.assertTrue("Размер списка прогресса (${progressList.size}) >= $minimumProgressListSize",
            progressList.size >= minimumProgressListSize)

        Assert.assertTrue("Размер списка скорости (${speedList.size}) >= $minimumSpeedListSize",
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


    private val freshLimitedStreamCopier
        get() = LimitedStreamCopierNs()


    private fun logD(text: String) {
//        Log.d(TAG, text)
    }

    private fun logI(text: String) {
        Log.i(TAG, text)
    }

    companion object {
        val TAG: String = LimitedStreamCopierTest::class.java.simpleName
    }
}

