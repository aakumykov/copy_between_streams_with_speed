package com.github.aakumykov.copy_between_streams_with_speed

import com.github.aakumykov.copy_between_streams_with_speed.utils.random
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test

class UnlimitedSpeedCopyBetweenStreamsWithProgressInstrumentedTest : TestBase() {

    @Test
    fun file_copied() = runBlocking {
        val fileSize = 10
        prepareSourceAndTargetFiles(fileSize)
        unlimitedSpeedCopyBetweenStreamsWithProgress(
            inputStream = sourceFileStream,
            outputStream = targetFileStream,
        ).collect {  }
        Assert.assertEquals(fileSize.toLong(), sourceFile.length())
        Assert.assertEquals(fileSize.toLong(), targetFile.length())
        Assert.assertEquals(sourceFile.length(), targetFile.length())
        Assert.assertEquals(sourceFileContents, targetFileContents)
    }


    @Test
    fun flow_returns_progress_on_zero_data_size() = runBlocking {
        test_progress_list(0, 0)
    }


    @Test
    fun flow_returns_progress_on_data_size_lower_than_buffer() = runBlocking {
        repeat(10) { i ->
            val size = i * 10 + random.nextInt(10)
            test_progress_list(size, 1)
        }
    }


    @Test
    fun flow_returns_progress_on_data_size_equals_buffer_size() = runBlocking {
        test_progress_list(DEFAULT_BUFFER_SIZE, 1)
    }


    @Test
    fun flow_returns_progress_on_data_size_multiplied_buffer_size() = runBlocking {
        repeat(10) { i ->
            val size = i * DEFAULT_BUFFER_SIZE
            test_progress_list(size, i)
        }
    }


    @Test
    fun flow_returns_progress_on_data_size_greater_than_buffer() = runBlocking {
        repeat(10) { i ->
            val multiplier = i+1
            val dataSize = multiplier * DEFAULT_BUFFER_SIZE + random.nextInt(1,10)
            val expectedProgressValuesCount = multiplier+1
            test_progress_list(dataSize, expectedProgressValuesCount)
        }
    }


    private suspend fun test_progress_list(dataSize: Int, expectedProgressListSize: Int) {
        prepareSourceAndTargetFiles(dataSize)

        val progressList = mutableListOf<Long>()

        unlimitedSpeedCopyBetweenStreamsWithProgress(
            inputStream = sourceFileStream,
            outputStream = targetFileStream,
        ).collect { progressList.add(it) }

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