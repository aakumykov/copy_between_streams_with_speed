package com.github.aakumykov.copy_between_streams_with_speed

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
        - отрицательная  скорость [throws_exception_on_negative_speed]
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

     */


    //
    // Испытание простого копирования.
    //
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun file_simply_copied() = runBlocking {
        repeat_on_different_sizes { fileSize ->
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
                    speedBytesPerSecond = 1_000_000
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
                    speedBytesPerSecond = 0
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
                    speedBytesPerSecond = -1
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
                    speedBytesPerSecond = 10,
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
        copy_data(speed, steps) {
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
            copy_data(1000, 10) {
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
            copy_data(1000, 10) {
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
            copy_data(1000, 10) {
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
            copy_data(speed, steps) {
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
        copy_data(speed, steps) {
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
            copy_data(speed, steps) {
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
            copy_data(speed, steps) {
                test_progress_list(it, size, speed, steps)
                test_files(size)
            }
        }
    }


    private fun copyWithoutCheck() = runBlocking {
        limitedStreamCopier.copyFromStreamToStream(
            inputStream = sourceFileStream,
            outputStream = targetFileStream,
            speedBytesPerSecond = 1000
        )
    }


    private fun test_files(dataSize: Int) {
        Assert.assertEquals(dataSize.toLong(), sourceFile.length())
        Assert.assertEquals(dataSize.toLong(), targetFile.length())
        Assert.assertEquals(sourceFile.length(), targetFile.length())
        Assert.assertEquals(sourceFileContents, targetFileContents)
    }


    /*@OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun copy_and_test_progress_list(
        scope: CoroutineScope,
        dataSizeBytes: Int,
        speedBytesPerSecond: Int,
        stepsPerSecond: Int
    ) = runTest {
        prepareSourceAndTargetFiles(dataSizeBytes)

        val progressList = mutableListOf<Long>()

        val collectingJob = scope.launch (Dispatchers.IO) {
            limitedStreamCopier.progressFlow.collect {
                progressList.add(it)
            }
        }
        advanceUntilIdle()

        limitedStreamCopier.copyFromStreamToStream(
            inputStream = sourceFileStream,
            outputStream = targetFileStream,
            speedBytesPerSecond = speedBytesPerSecond,
            stepsPerSecond = stepsPerSecond
        )
        collectingJob.cancel()

        // TODO: Double

        val bytesToBeTransferredPerStep = (1f * speedBytesPerSecond / stepsPerSecond).roundToInt()

        val expectedSteps = if (dataSizeBytes < bytesToBeTransferredPerStep) 1
                            else (1f * dataSizeBytes / bytesToBeTransferredPerStep).roundToInt()

        val argumentsLogs =
                "данные: $dataSizeBytes байт,\n" +
                "скорость:$speedBytesPerSecond,\n" +
                "шагов:$stepsPerSecond"

        if (expectedSteps > 1) {
                val progressStepsCountDifferenceFloat =
                    1f * abs(progressList.size - expectedSteps) / expectedSteps
                val progressStepsDifferenceInt = (progressStepsCountDifferenceFloat * 100).roundToInt()

                val expectedDiffPercents = 10

                Assert.assertTrue(
                    "${argumentsLogs}\nРазмер списка прогресса (${progressList.size}) отличается от ожидаемого (${expectedSteps}) более, чем на ${expectedDiffPercents}%: на ${progressStepsDifferenceInt}%",
                    progressStepsDifferenceInt <= expectedDiffPercents
                )
            }
        else {
            Assert.assertEquals(
                "${argumentsLogs}\nРазмер списка прогресса для данных $dataSizeBytes байт равен $expectedSteps",
                expectedSteps,
                progressList.size
            )
        }

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
    }*/

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


    @OptIn(ExperimentalCoroutinesApi::class)
    private fun copy_data(speed: Int, steps: Int, onComplete: (progressList:List<Long>) -> Unit) = runTest {

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
            speedBytesPerSecond = speed,
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


    private val limitedStreamCopier by lazy { LimitedStreamCopier() }
}
