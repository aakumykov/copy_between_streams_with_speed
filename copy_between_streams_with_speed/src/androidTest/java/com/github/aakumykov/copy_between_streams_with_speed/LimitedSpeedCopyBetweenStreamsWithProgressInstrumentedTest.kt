package com.github.aakumykov.copy_between_streams_with_speed

import com.github.aakumykov.copy_between_streams_with_speed.utils.random
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import java.io.FileNotFoundException
import kotlin.math.abs
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
    // Испытание просто копирования
    //
    @Test
    fun file_simply_copied() = runBlocking {
        val fileSize = 2048
        prepareSourceAndTargetFiles(fileSize)
        limitedSpeedCopyBetweenStreamsWithProgress(
            inputStream = sourceFileStream,
            outputStream = targetFileStream,
            speedBytesPerSecond = 500,
        ).collect()
        Assert.assertEquals(fileSize.toLong(), sourceFile.length())
        Assert.assertEquals(fileSize.toLong(), targetFile.length())
        Assert.assertEquals(sourceFile.length(), targetFile.length())
        Assert.assertEquals(sourceFileContents, targetFileContents)
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
        prepareSourceAndTargetFiles()
        Assert.assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                limitedSpeedCopyBetweenStreamsWithProgress(
                    inputStream = sourceFileStream,
                    outputStream = targetFileStream,
                    speedBytesPerSecond = 0
                ).collect()
            }
        }
    }


    @Test
    fun throws_exception_on_negative_speed() {
        Assert.assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                limitedSpeedCopyBetweenStreamsWithProgress(
                    inputStream = sourceFileStream,
                    outputStream = targetFileStream,
                    speedBytesPerSecond = -1
                ).collect()
            }
        }
    }


    @Test
    fun throws_exception_on_steps_greater_then_speed() {
        prepareSourceAndTargetFiles(10)
        Assert.assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                limitedSpeedCopyBetweenStreamsWithProgress(
                    inputStream = sourceFileStream,
                    outputStream = targetFileStream,
                    speedBytesPerSecond = 10,
                    stepsPerSecond = 20
                ).collect()
            }
        }
    }


    //
    // Испытание прогресса
    //
    @Test
    fun progress_is_empty_on_zero_size_file() = runBlocking {
        test_progress_list(0, 30, 10)
    }


    @Test
    fun progress_is_correct_on_file_size_lower_than_buffer_size() = runBlocking {
        repeat(10) { i ->
            val size = (i+1) * 10 + random.nextInt(10)
            test_progress_list(size, 100, 10)
        }
    }


    @Test
    fun progress_is_correct_on_file_size_equals_buffer_size() = runBlocking {
        test_progress_list(DEFAULT_BUFFER_SIZE, 100, stepsPerSecond = 10)
    }


    @Test
    fun progress_is_correct_on_file_size_proportional_buffer_size() = runBlocking {
        repeat(10) { i ->
            val dataSize = (i+1) * DEFAULT_BUFFER_SIZE
            test_progress_list(dataSize, 100, stepsPerSecond = 10)
        }
    }


    @Test
    fun progress_is_correct_on_file_size_greater_than_buffer_size() = runBlocking {
        repeat(10) { i ->
            val multiplier = i+1
            val dataSize = multiplier * DEFAULT_BUFFER_SIZE + random.nextInt(1,10)
            test_progress_list(dataSize, multiplier * 100, stepsPerSecond = 10)
        }
    }



    private fun copyWithoutCheck() {
        runBlocking {
            limitedSpeedCopyBetweenStreamsWithProgress(
                inputStream = sourceFileStream,
                outputStream = targetFileStream,
                speedBytesPerSecond = 10
            ).collect()
        }
    }


    private suspend fun test_progress_list(
        dataSizeBytes: Int,
        speedBytesPerSecond: Int,
        stepsPerSecond: Int
    ) {
        prepareSourceAndTargetFiles(dataSizeBytes)

        val progressList = mutableListOf<Long>()

        limitedSpeedCopyBetweenStreamsWithProgress(
            inputStream = sourceFileStream,
            outputStream = targetFileStream,
            speedBytesPerSecond = speedBytesPerSecond,
            stepsPerSecond = stepsPerSecond
        ).collect {
            progressList.add(it)
        }

        // TODO: Double

        val bytesToBeTransferredPerStep = (1f * speedBytesPerSecond / stepsPerSecond).roundToInt()
        val expectedSteps = (1f * dataSizeBytes / bytesToBeTransferredPerStep).roundToInt()

        val progressStepsDifference = abs(progressList.size - expectedSteps)

        Assert.assertTrue(
            "Размер списка прогресса отличается от ожидаемого не более, чем на 2 элемента",
            progressStepsDifference <= 2
        )

        if (progressList.size >= 2) {
            repeat(progressList.size-1) { i ->
                val value = progressList[i]
                val nextValue = progressList[i+1]
                Assert.assertTrue("Каждое предыдущее значение меньше следующего", value < nextValue)
            }
        }
    }
}