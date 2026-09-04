package com.github.aakumykov.copy_between_streams_with_speed

import com.github.aakumykov.copy_between_streams_with_speed.utils.random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

        repeat_on_different_sizes { fileSize ->

            println("размер файла: $fileSize")

            prepareSourceAndTargetFiles(fileSize)


            launch (Dispatchers.IO) {
                limitedStreamCopier.progressFlow.collect {
                    println(it)
                }
            }.also {
                limitedStreamCopier.copyFromStreamToStream(
                    inputStream = sourceFileStream,
                    outputStream = targetFileStream,
                    speedBytesPerSecond = 1000
                )
                it.cancel()
            }

            test_files(fileSize)
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
        copy_and_test_progress_list(this,0, 30, 10)
        test_files(0)
    }


    @Test
    fun progress_is_correct_on_file_size_lower_than_buffer_size() = runBlocking {
        repeat(10) { i ->
            val size = (i+1) * 10 + random.nextInt(10)
            copy_and_test_progress_list(this,size, 1000, 10)
            test_files(size)
        }
    }


    @Test
    fun progress_is_correct_on_file_size_equals_buffer_size() = runBlocking {
        val size = DEFAULT_BUFFER_SIZE
        copy_and_test_progress_list(this,size, 1000, stepsPerSecond = 10)
        test_files(size)
    }


    @Test
    fun progress_is_correct_on_file_size_proportional_buffer_size() = runBlocking {
        repeat(10) { i ->
            val dataSize = (i+1) * DEFAULT_BUFFER_SIZE
            copy_and_test_progress_list(this,dataSize, 1000, stepsPerSecond = 10)
            test_files(dataSize)
        }
    }


    @Test
    fun progress_is_correct_on_file_size_greater_than_buffer_size() = runBlocking {
        repeat(2) { i ->
            val multiplier = i+1
            val dataSize = multiplier * DEFAULT_BUFFER_SIZE + random.nextInt(1,10)
            copy_and_test_progress_list(this,dataSize, multiplier * 1000, stepsPerSecond = 10)
            test_files(dataSize)
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


    private suspend fun copy_and_test_progress_list(
        scope: CoroutineScope,
        dataSizeBytes: Int,
        speedBytesPerSecond: Int,
        stepsPerSecond: Int
    ) {
        prepareSourceAndTargetFiles(dataSizeBytes)

        val progressList = mutableListOf<Long>()

        scope.launch (Dispatchers.IO) {
            limitedStreamCopier.progressFlow.collect {
                progressList.add(it)
            }
        }.also {
            limitedStreamCopier.copyFromStreamToStream(
                inputStream = sourceFileStream,
                outputStream = targetFileStream,
                speedBytesPerSecond = 1000
            )
            it.cancel()
        }.join()

        // TODO: Double

        val bytesToBeTransferredPerStep = (1f * speedBytesPerSecond / stepsPerSecond).roundToInt()
        val expectedSteps = (1f * dataSizeBytes / bytesToBeTransferredPerStep).roundToInt()

        val progressStepsDifference = abs(progressList.size - expectedSteps)

        Assert.assertTrue(
            "Размер списка прогресса (${progressList.size}) отличается от ожидаемого (${expectedSteps}) более, чем на 2 элемента: на $progressStepsDifference",
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

    private val limitedStreamCopier by lazy { LimitedStreamCopier() }
}
