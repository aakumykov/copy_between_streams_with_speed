package com.github.aakumykov.copy_between_streams_with_speed

import com.github.aakumykov.copy_between_streams_with_speed.utils.random
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import java.io.FileNotFoundException
import kotlin.math.roundToInt

class LimitedSpeedCopyBetweenStreamsWithProgressInstrumentedTest : TestBase() {

    @Test
    fun file_simply_copied() = runBlocking {
        val fileSize = 10
        val speed = (1f * fileSize / 2).roundToInt()
        prepareSourceAndTargetFiles(fileSize)
        limitedSpeedCopyBetweenStreamsWithProgress(
            inputStream = sourceFileStream,
            outputStream = targetFileStream,
            speedBytesPerSecond = speed
        ).collect()
        Assert.assertEquals(fileSize.toLong(), sourceFile.length())
        Assert.assertEquals(fileSize.toLong(), targetFile.length())
        Assert.assertEquals(sourceFile.length(), targetFile.length())
        Assert.assertEquals(sourceFileContents, targetFileContents)
    }

    @Test
    fun thrown_FNFE_on_no_source_file() {
        Assert.assertThrows(FileNotFoundException::class.java) {
            clearSourceFile()
            prepareTargetFile()
            copyWithoutCheck()
        }
    }

    /*
    // Это не получается протестировать, так как целевой файл создаётся по требованию,
    // а сделать файловую систему только для чтения так просто нельзя...
    @Test
    fun thrown_FNFE_on_no_target_file() {
        Assert.assertThrows(FileNotFoundException::class.java) {
            clearTargetFile()
            prepareSourceFile(100)
            copyWithoutCheck()
        }
    }*/

    @Test
    fun throws_exception_on_zero_speed() {
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


    @Test
    fun flow_not_returns_progress_on_zero_data_size() = runBlocking {
        test_progress_list(0, 30, 10)
    }


    @Test
    fun f() = runBlocking {
        test_progress_list(100, 30, stepsPerSecond = 3)
    }


    @Test
    fun flow_returns_progress_on_data_size_lower_than_buffer_size() = runBlocking {
        repeat(10) { i ->
            val size = (i+1) * 10 + random.nextInt(10)
            test_progress_list(size, size, 10)
        }
    }


    @Test
    fun flow_returns_progress_on_data_size_equals_buffer_size() = runBlocking {
        test_progress_list(DEFAULT_BUFFER_SIZE, 1000, 10)
    }


    @Test
    fun flow_returns_progress_on_data_size_multiplied_buffer_size() = runBlocking {
        repeat(10) { i ->
            val size = i * DEFAULT_BUFFER_SIZE
            test_progress_list(size, size, 10)
        }
    }


    @Test
    fun flow_returns_progress_on_data_size_greater_than_buffer() = runBlocking {
        repeat(10) { i ->
            val multiplier = i+1
            val dataSize = multiplier * DEFAULT_BUFFER_SIZE + random.nextInt(1,10)
            val expectedProgressValuesCount = multiplier+1
//            test_progress_list(dataSize, expectedProgressValuesCount)
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
        dataSize: Int,
        speedBytesPerSecond: Int,
        stepsPerSecond: Int
    ) {
        prepareSourceAndTargetFiles(dataSize)

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
        val expectedSteps = (1f * dataSize / bytesToBeTransferredPerStep).roundToInt()
        val expectedSecondsOfWork = (1f * dataSize / speedBytesPerSecond).roundToInt()
        val expectedProgressListSize = (expectedSecondsOfWork * expectedSteps)//.roundToInt()

        Assert.assertEquals("Размер списка прогресса соответствует ожидаемому", expectedProgressListSize, progressList.size)

        if (progressList.size >= 2) {
            repeat(progressList.size-1) { i ->
                val value = progressList[i]
                val nextValue = progressList[i+1]
                Assert.assertTrue("Каждое предыдущее значение меньше следующего", value < nextValue)
            }
        }
    }
}