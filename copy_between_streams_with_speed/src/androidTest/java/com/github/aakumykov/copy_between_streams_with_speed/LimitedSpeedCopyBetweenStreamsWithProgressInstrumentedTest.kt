package com.github.aakumykov.copy_between_streams_with_speed

import com.github.aakumykov.copy_between_streams_with_speed.utils.humanSizeBinary
import com.github.aakumykov.copy_between_streams_with_speed.utils.random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test
import java.io.FileNotFoundException
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

class LimitedSpeedCopyBetweenStreamsWithProgressInstrumentedTest : TestBase() {

    /**
    План теста:
    1) проверить граничные условия (в данном случае исключения):
        - нулевая скорость [throws_exception_on_zero_speed]
        - отрицательная скорость [throws_exception_on_negative_speed]
        - количество шагов в секунду больше скорости в секунду [throws_exception_on_steps_greater_then_speed]
        - оцуцтвует исходный файл [thrown_FNFE_on_no_source_file]
    2) простое копирование файла [file_simply_copied], при котором он:
        - копируется;
        - совпадает по размеру;
        - совпадает по содержимому;
        - исходный файл сохраняется.
    3) прогресс копирования:
        - при копировании файла нулевого размера данные о прогрессе пусты [progress_is_empty_on_zero_size_file]
        - при копировании файла ненулевого размера в трёх вариантах
          относительно размера буфера копирования ([DEFAULT_BUFFER_SIZE]):
            а) размер файла меньше буфера [progress_is_correct_on_file_size_lower_than_buffer_size];
            б) размер файла равен размеру буфера [progress_is_correct_on_file_size_equals_buffer_size];
            в) размер файла кратен размеру буфера [progress_is_correct_on_file_size_proportional_buffer_size];
            г) размер файла больше размера буфера [progress_is_correct_on_file_size_greater_than_buffer_size].
            Данные о прогрессе:
                -- приходят;
                -- первое значение меньше последнего;
                -- расположены в порядке возрастания.
    4) вариации аргументов:
        - разная скорость при фиксированном числе шагов и размере
            [diff_speed_with_constant_size_and_steps__units_to_thousands];
            [diff_speed_with_constant_size_and_steps__tens_thousands];
            [diff_speed_with_constant_size_and_steps__hundreds_thousands];
            [diff_speed_with_constant_size_and_steps__millions];
        - разное число шагов при фиксированных размере и скорости
            [diff_steps_with_constant_size_and_speed__units_to_thousands];
            [diff_steps_with_constant_size_and_speed__tens_thousands];
            [diff_steps_with_constant_size_and_speed__hundreds_thousands];
            [diff_steps_with_constant_size_and_speed__millions];
        - разный размер при фиксированных скорости и числе шагов
            [diff_size_with_constant_steps_and_speed__units_to_thousands].
            [diff_size_with_constant_steps_and_speed__tens_thousands].
            [diff_size_with_constant_steps_and_speed__hundreds_thousands].
            [diff_size_with_constant_steps_and_speed__millions].
     */


    //
    // Испытание простого копирования.
    //
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun file_simply_copied() = runBlocking {
        repeat_on_different_sizes {
            runTest {
                val fileSize = 10272
                println("размер файла: $fileSize")

                prepareSourceAndTargetFiles(fileSize)

                val job = launch (Dispatchers.IO) {
                    limitedStreamCopier.progressFlow.collect {
                        println(it)
                    }
                }
                advanceUntilIdle()

                limitedStreamCopier.copyFromStreamToStream(
                    inputStream = sourceFileStream,
                    outputStream = targetFileStream,
                    speed = 1_000_000
                )
                job.cancel()

                test_files(fileSize)
            }
        }
    }


    //
    // Испытание неправильных аргументов
    //
    @Test
    fun thrown_FNFE_on_no_source_file() {
        Assert.assertThrows(FileNotFoundException::class.java) {
            clearSourceFile()
            prepareTargetFile()
            copyWithoutCheck()
        }
    }

    //
    // Не получится так просто проверить с оцуцтвием целевого файла,
    // так как он создаётся по требованию,
    // а сделать файловую систему только для чтения так просто нельзя.
    //


    @Test
    fun throws_exception_on_zero_speed() {
        Assert.assertThrows(IllegalArgumentException::class.java) {
            prepareSourceAndTargetFiles()
            runBlocking {
                limitedStreamCopier.copyFromStreamToStream(
                    inputStream = sourceFileStream,
                    outputStream = targetFileStream,
                    speed = 0
                )
            }
        }
    }


    @Test
    fun throws_exception_on_negative_speed() {
        Assert.assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                limitedStreamCopier.copyFromStreamToStream(
                    inputStream = sourceFileStream,
                    outputStream = targetFileStream,
                    speed = -1
                )
            }
        }
    }


    @Test
    fun throws_exception_on_steps_greater_then_speed() {
        Assert.assertThrows(IllegalArgumentException::class.java) {
            prepareSourceAndTargetFiles(10)
            runBlocking {
                limitedStreamCopier.copyFromStreamToStream(
                    inputStream = sourceFileStream,
                    outputStream = targetFileStream,
                    speed = 10,
                    stepsPerSecond = 20
                )
            }
        }
    }


    //
    // Испытание прогресса
    //
    @Test
    fun progress_is_empty_on_zero_size_file() = runBlocking {
        val size = 0
        val speed = 1000
        val steps = 10
        prepareSourceAndTargetFiles(size)
        copy_data_with_on_complete(speed, steps) {
            test_progress_list(it, size, speed, steps)
            test_files(0)
        }
    }


    @Test
    fun progress_is_correct_on_sizes_lowe_than_10() = runBlocking {
        repeat(9) { i ->
            val size = i + 10
            val speed = 1000
            val steps = 10
            prepareSourceAndTargetFiles(size)
            copy_data_with_on_complete(1000, 10) {
                test_progress_list(it, size, speed, steps)
                test_files(size)
            }
        }
    }

    @Test
    fun progress_is_correct_on_sizes_10_to_100() = runBlocking {
        repeat(9) { tens ->
            val size = (tens+1) * 10 + random.nextInt(0,10)
            val speed = 1000
            val steps = 10
            prepareSourceAndTargetFiles(size)
            copy_data_with_on_complete(1000, 10) {
                test_progress_list(it, size, speed, steps)
                test_files(size)
            }
        }
    }


    @Test
    fun progress_is_correct_on_sizes_100_to_1000() = runBlocking {
        repeat(9) { tens ->
            val size = (tens+1) * 100 + random.nextInt(0,100)
            val speed = 1000
            val steps = 10
            prepareSourceAndTargetFiles(size)
            copy_data_with_on_complete(1000, 10) {
                test_progress_list(it, size, speed, steps)
                test_files(size)
            }
        }
    }


    @Test
    fun progress_is_correct_on_file_size_lower_than_buffer_size() = runBlocking {
        repeat(10) { i ->
            val size = (i+1) * 10 + random.nextInt(10)
            val speed = 1000
            val steps = 10
            prepareSourceAndTargetFiles(size)
            copy_data_with_on_complete(speed, steps) {
                test_progress_list(it, size, speed, steps)
                test_files(size)
            }
        }
    }


    @Test
    fun progress_is_correct_on_file_size_equals_buffer_size() = runBlocking {
        val size = DEFAULT_BUFFER_SIZE
        val speed = 1000
        val steps = 10
        prepareSourceAndTargetFiles(size)
        copy_data_with_on_complete(speed, steps) {
            test_progress_list(it, size, speed, steps)
            test_files(size)
        }
    }


    @Test
    fun progress_is_correct_on_file_size_proportional_buffer_size() = runBlocking {
        repeat(10) { i ->
            val size = (i+1) * DEFAULT_BUFFER_SIZE
            val speed = 1000_000
            val steps = 10
            prepareSourceAndTargetFiles(size)
            copy_data_with_on_complete(speed, steps) {
                test_progress_list(it, size, speed, steps)
                test_files(size)
            }
        }
    }


    @Test
    fun progress_is_correct_on_file_size_greater_than_buffer_size() = runBlocking {
        repeat(10) { i ->
            val multiplier = i+2
            val size = multiplier * DEFAULT_BUFFER_SIZE + random.nextInt(1,DEFAULT_BUFFER_SIZE)
            val speed = multiplier * 1000
            val steps = 10
            prepareSourceAndTargetFiles(size)
            copy_data_with_on_complete(speed, steps) {
                test_progress_list(it, size, speed, steps)
                test_files(size)
            }
        }
    }


    //
    // разный размер при фиксированных скорости и числе шагов
    //
    @Test
    fun diff_size_with_constant_steps_and_speed__units_to_thousands() = runTest {
        // единицы байт: 1..9
        do_with_variable_data_size(
            speed =  1,
            steps = 1,
            dataSizeRange = 1..9,
            dataSizeInterval = 1,
            randomizeDataSize = false
        )

        // десятки байт: 10..90
        do_with_variable_data_size(
            speed =  100,
            steps = 10,
            dataSizeRange = 10..90,
            dataSizeInterval = 10
        )

        // сотни байт: 100..900
        do_with_variable_data_size(
            speed =  1000,
            steps = 10,
            dataSizeRange = 100..900,
            dataSizeInterval = 100
        )

        // тысячи байт: 1000..9000
        do_with_variable_data_size(
            speed =  10_000,
            steps = 10,
            dataSizeRange = 1000..9000,
            dataSizeInterval = 1000
        )
    }


    @Test
    fun diff_size_with_constant_steps_and_speed__tens_thousands() = runTest {
        // десятки тысяч байт: 10_000..90_000
        do_with_variable_data_size(
            speed =  100_000,
            steps = 10,
            dataSizeRange = 10_000..90_000,
            dataSizeInterval = 10_000
        )
    }


    @Test
    fun diff_size_with_constant_steps_and_speed__hundreds_thousands() = runTest {
        // сотни тысяч байт: 100_000..900_000
        do_with_variable_data_size(
            speed =  1000_000,
            steps = 10,
            dataSizeRange = 100_000..900_000,
            dataSizeInterval = 100_000
        )
    }


    @Test
    fun diff_size_with_constant_steps_and_speed__millions() = runTest {
        // мильёны байт: 1000_000..9000_000
        do_with_variable_data_size(
            speed =  10_000_000,
            steps = 10,
            dataSizeRange = 1000_000..9000_000,
            dataSizeInterval = 1000_000
        )
    }


    //
    // разная скорость при фиксированном числе шагов и размере
    //
    @Test
    fun diff_speed_with_constant_size_and_steps__units_to_thousands() = runTest {
        // единицы байт: 1..9
        do_with_variable_speed(
            dataSizeFromRange = 1..9,
            steps = 1,
            speedRange = 1..100,
            speedInterval = 10,
        )

        // десятки байт: 10..90
        do_with_variable_speed(
            dataSizeFromRange = 10..90,
            steps = 10,
            speedRange = 10..1000,
            speedInterval = 10,
        )

        // сотни байт: 100..900
        do_with_variable_speed(
            dataSizeFromRange = 100..900,
            steps = 10,
            speedRange = 10..1000,
            speedInterval = 10,
        )

        // тысячи байт: 1000..9000
        do_with_variable_speed(
            dataSizeFromRange = 1000..9000,
            steps = 10,
            speedRange = 100..1000,
            speedInterval = 10,
        )
    }


    @Test
    fun diff_speed_with_constant_size_and_steps__tens_thousands() = runTest {
        // десятки тысяч байт: 10_000..90_000
        do_with_variable_speed(
            dataSizeFromRange = 10_000..90_000,
            steps = 10,
            speedRange = 100_000..1000_000,
            speedInterval = 1000,
        )
    }


    @Test
    fun diff_speed_with_constant_size_and_steps__hundreds_thousands() = runTest {
        // сотни тысяч байт: 100_000..900_000
        do_with_variable_speed(
            dataSizeFromRange = 100_000..900_000,
            steps = 10,
            speedRange = 1000_000..9000_000,
            speedInterval = 100_000,
        )
    }


    @Test
    fun diff_speed_with_constant_size_and_steps__millions() = runTest {
        // мильёны байт: 100_000..900_000
        do_with_variable_speed(
            dataSizeFromRange = 1000_000..9000_000,
            steps = 10,
            speedRange = 1000_000..9000_000,
            speedInterval = 1000_000,
        )
    }


    //
    // разное число шагов при фиксированных размере и скорости
    //
    @Test
    fun diff_steps_with_constant_size_and_speed__units_to_thousands() {
        // "единицы байт: 1..9"
        do_with_variable_steps(
            dataSizeFromRange = 1..9,
            speed = 10,
            stepsRange = 1..10,
            stepsInterval = 1,
            randomizeStep = false
        )

        // "десятки байт: 10+..90+"
        do_with_variable_steps(
            dataSizeFromRange = 10..90,
            speed = 100,
            stepsRange = 1..100,
            stepsInterval = 10,
            randomizeStep = true
        )

        // "сотни байт: 100+..900+"
        do_with_variable_steps(
            dataSizeFromRange = 100..900,
            speed = 1000,
            stepsRange = 1..100,
            stepsInterval = 10,
            randomizeStep = true
        )

        // "тысячи байт: 1000+..9000+"
        do_with_variable_steps(
            dataSizeFromRange = 1000..9000,
            speed = 10_000,
            stepsRange = 1..100,
            stepsInterval = 10,
            randomizeStep = true
        )
    }


    @Test
    fun diff_steps_with_constant_size_and_speed__tens_thousands() {
        // "десятки тысяч байт: 10_000+..90_000+"
        do_with_variable_steps(
            dataSizeFromRange = 10_000..90_000,
            speed = 100_000,
            stepsRange = 1..100,
            stepsInterval = 10,
            randomizeStep = true
        )
    }


    @Test
    fun diff_steps_with_constant_size_and_speed__hundreds_thousands() {
        // "сотни тысяч байт: 100_000+..900_000+"
        do_with_variable_steps(
            dataSizeFromRange = 100_000..900_000,
            speed = 1000_000,
            stepsRange = 1..100,
            stepsInterval = 10,
            randomizeStep = true
        )
    }


    @Test
    fun diff_steps_with_constant_size_and_speed__millions() {
        // "мильёны байт: 1000_000+..9000_000+"
        do_with_variable_steps(
            dataSizeFromRange = 1000_000..9000_000,
            speed = 10_000_000,
            stepsRange = 1..100,
            stepsInterval = 10,
            randomizeStep = true
        )
    }



    @OptIn(ExperimentalCoroutinesApi::class)
    private fun copy_data_with_on_complete(speed: Int, steps: Int, onComplete: (progressList:List<Long>) -> Unit) = runTest {

        val progressList = mutableListOf<Long>()

        val collectingJob = launch {
            limitedStreamCopier.progressFlow.collect {
                progressList.add(it)
            }
        }
        advanceUntilIdle()

        limitedStreamCopier.copyFromStreamToStream(
            inputStream = sourceFileStream,
            outputStream = targetFileStream,
            speed = speed,
            stepsPerSecond = steps
        )
        collectingJob.cancel()

        onComplete.invoke(progressList)
    }


    private fun test_progress_list(
        progressList: List<Long>,
        dataSizeBytes: Int, // TODO: Long
        speedBytesPerSecond: Int,
        stepsPerSecond: Int,
    ) {
        test_progress_list_size(progressList, dataSizeBytes, speedBytesPerSecond, stepsPerSecond)
        test_progress_list_incrementality(progressList)
    }


    private fun test_progress_list_size(
        progressList: List<Long>,
        dataSizeBytes: Int,
        speedBytesPerSecond: Int,
        stepsPerSecond: Int
    ) {
        val bytesToBeTransferredPerStep = (1f * speedBytesPerSecond / stepsPerSecond).roundToInt()
        val operatingPortionSize = if (bytesToBeTransferredPerStep > DEFAULT_BUFFER_SIZE) DEFAULT_BUFFER_SIZE else bytesToBeTransferredPerStep

        val expectedSteps = when {
            (0 == dataSizeBytes) -> {
                0
            }
            (dataSizeBytes <= operatingPortionSize) -> {
                1
            }
            else -> {
                ceil(1f * dataSizeBytes / operatingPortionSize).roundToInt()
            }
        }

        val argumentsLog =
            "\nданные: $dataSizeBytes байт," +
            "\nскорость:$speedBytesPerSecond," +
            "\nшагов:$stepsPerSecond"

        if (expectedSteps > 1) {
            val progressStepsCountDifferenceFloat = 1f * abs(progressList.size - expectedSteps) / expectedSteps
            val progressStepsDifferenceInt = (progressStepsCountDifferenceFloat * 100).roundToInt()
            val expectedDiffPercents = 10

            val message = "${argumentsLog}\nРазмер списка прогресса (${progressList.size}) отличается от ожидаемого (${expectedSteps}) более, чем на ${expectedDiffPercents}%: на ${progressStepsDifferenceInt}%."

            Assert.assertTrue(message, progressStepsDifferenceInt <= expectedDiffPercents)
        }
        else {
            Assert.assertEquals(
                "${argumentsLog}\nРазмер списка прогресса для данных в $dataSizeBytes байт должен быть равен $expectedSteps байт.",
                expectedSteps,
                progressList.size
            )
        }
    }


    private fun test_progress_list_incrementality(progressList: List<Long>) {
        // Проверка, что значения увеличиваются.
        if (progressList.size >= 2) {
            repeat(progressList.size-1) { i ->
                val value = progressList[i]
                val nextValue = progressList[i+1]
                Assert.assertTrue(
                    "Каждое предыдущее значение ($value) меньше следующего ($nextValue);\nвесь список:\n${progressList.joinToString(",\n")}",
                    value < nextValue
                )
            }
        }
    }


    private fun do_with_variable_steps(
        dataSizeFromRange: IntRange,
        speed: Int,
        stepsRange: IntRange,
        stepsInterval: Int,
        randomizeStep: Boolean = true,
    ) {
        println("----- Копирование с вариацией шагов -----")

        val dataSize = dataSizeFromRange.random()

        var steps = stepsRange.first
        while (steps <= stepsRange.last) {

            println("Копирование ${dataSize.humanSizeBinary()} на скорости ${speed.humanSizeBinary()}/с с $steps шагами в секунду.")
            copy_and_test_with_params(
                dataSizeBytes = dataSize,
                speedBytesPerSecond = speed,
                stepsPerSecond = steps
            )

            steps += stepsInterval
            if (randomizeStep) steps += random.nextInt(stepsInterval)
        }
    }


    private fun do_with_variable_data_size(
        steps: Int,
        speed: Int,
        dataSizeRange: IntRange,
        dataSizeInterval: Int,
        randomizeDataSize: Boolean = true,
    ) {
        println("----- Копирование с вариацией размера данных -----")

        var dataSize = dataSizeRange.first
        while (dataSize <= dataSizeRange.last) {

            println("Копирование ${dataSize.humanSizeBinary()} на скорости ${speed.humanSizeBinary()}/с с $steps шагами в секунду.")
            copy_and_test_with_params(
                dataSizeBytes = dataSize,
                speedBytesPerSecond = speed,
                stepsPerSecond = steps
            )

            dataSize += dataSizeInterval
            if (randomizeDataSize) dataSize += random.nextInt(dataSizeInterval)
        }
    }


    private fun do_with_variable_speed(
        dataSizeFromRange: IntRange,
        steps: Int,
        speedRange: IntRange,
        speedInterval: Int,
        randomizeSpeed: Boolean = true,
    ) {
        println("----- Копирование с вариацией скорости -----")

        val dataSize = dataSizeFromRange.random()

        var speed = speedRange.first
        while (speed <= speedRange.last) {

            println("Копирование ${dataSize.humanSizeBinary()} на скорости ${speed.humanSizeBinary()}/с с $steps шагами в секунду.")
            copy_and_test_with_params(
                dataSizeBytes = dataSize,
                speedBytesPerSecond = speed,
                stepsPerSecond = steps
            )

            speed += speedInterval
            if (randomizeSpeed) speed += random.nextInt(speedInterval)
        }
    }


    private fun copyWithoutCheck() = runBlocking {
        limitedStreamCopier.copyFromStreamToStream(
            inputStream = sourceFileStream,
            outputStream = targetFileStream,
            speed = 1000
        )
    }


    private fun test_files(dataSize: Int) {
        Assert.assertEquals(dataSize.toLong(), sourceFile.length())
        Assert.assertEquals(dataSize.toLong(), targetFile.length())
        Assert.assertEquals(sourceFile.length(), targetFile.length())
        Assert.assertEquals(sourceFileContents, targetFileContents)
    }


    private suspend fun repeat_on_different_sizes(
        sizesList: List<Int> = listOf(1, 10, 100, 1000, 10_000, 100_000, 1000_000),
        block: suspend (fileSize: Int) -> Unit
    ) {
        sizesList.forEach { sizeRange ->
            repeat(5) { i ->
                val sizeAppendix = if (sizeRange > 1) random.nextInt(1,sizeRange) else 0
                val fileSize = (i + 1) * sizeRange + sizeAppendix
                block.invoke(fileSize)
            }
        }
    }

    private fun copy_and_test_with_params(
        dataSizeBytes: Int,
        speedBytesPerSecond: Int,
        stepsPerSecond: Int,
    ) {
        prepareSourceAndTargetFiles(dataSizeBytes)
        copy_data_with_on_complete(speedBytesPerSecond, stepsPerSecond) {
            test_progress_list(it, dataSizeBytes, speedBytesPerSecond, stepsPerSecond)
            test_files(dataSizeBytes)
        }
    }


    private val limitedStreamCopier by lazy { LimitedStreamCopierOld() }
}
