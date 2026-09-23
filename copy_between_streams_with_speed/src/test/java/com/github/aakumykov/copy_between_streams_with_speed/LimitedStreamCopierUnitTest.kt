package com.github.aakumykov.copy_between_streams_with_speed

import android.util.Log
import com.github.aakumykov.copy_between_streams_with_speed.stream2stream_copier.LimitedStreamCopier
import com.github.aakumykov.copy_between_streams_with_speed.utils.random
import org.junit.Assert
import org.junit.Test
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.roundToLong

class LimitedStreamCopierUnitTest {

    @Test
    fun simple_copy_test() {
        val base = 1024 * 1024 * 10

        val dataSize = base
        val speed = base * 10
        val rate = 1

        prepareSourceAndTargetFiles(dataSize)

        val startTime = System.currentTimeMillis()
        LimitedStreamCopier(speed, rate)
            .copyFromStreamToStream(sourceFileStream, targetFileStream)
        val duration = System.currentTimeMillis() - startTime

        val estimatedTime = (1000f * dataSize / speed).roundToLong()
        val diffPercents = 100f * duration / estimatedTime

        Log.d(TAG, "--------------------------------------------------------------------")
        Log.d(TAG, "dataSize:$dataSize, speed:$speed, est:$estimatedTime, real:$duration (${diffPercents})%")
        Log.d(TAG, "--------------------------------------------------------------------")

        Assert.assertTrue(sourceFile.exists())
        Assert.assertTrue(targetFile.exists())

        Assert.assertEquals(dataSize.toLong(), sourceFile.length())
        Assert.assertEquals(dataSize.toLong(), targetFile.length())

        Assert.assertEquals(sourceFileData, targetFileData)
    }

    private fun prepareSourceAndTargetFiles(sizeBytes: Int) {
        prepareTempDir()
        deleteFile(sourceFile)
        deleteFile(targetFile)
        writeRandomBytesToFile(sourceFile, sizeBytes)
    }

    private fun prepareTempDir() {
        val tempDir = File(TEMP_DIR_NAME)
        if (!tempDir.exists()) Assert.assertTrue(tempDir.mkdirs())
    }

    private fun deleteFile(file: File) {
        file.delete()
        Assert.assertFalse(file.exists())
    }

    private fun writeRandomBytesToFile(file: File, sizeBytes: Int) {
        val pieceSize = DEFAULT_BUFFER_SIZE
        val mainSteps = sizeBytes / pieceSize
        repeat(mainSteps) {
            file.appendBytes(random.nextBytes(pieceSize))
        }
        val mainSize = pieceSize * mainSteps
        val additionalSize = sizeBytes - mainSize
        file.appendBytes(random.nextBytes(additionalSize))
        Assert.assertEquals(sizeBytes.toLong(), file.length())
    }

    companion object {
        val TAG: String = LimitedStreamCopierUnitTest::class.java.simpleName
        private const val TEMP_DIR_NAME = "test_temp_dir"
        private const val SOURCE_FILE_NAME = "source.file"
        private const val TARGET_FILE_NAME = "target.file"
        private val sourceFile = File(TEMP_DIR_NAME, SOURCE_FILE_NAME)
        private val targetFile = File(TEMP_DIR_NAME, TARGET_FILE_NAME)
        private val sourceFileStream: InputStream get() = sourceFile.inputStream()
        private val targetFileStream: OutputStream get() = targetFile.outputStream()
        private val sourceFileData: String get() = sourceFile.readBytes().joinToString("")
        private val targetFileData: String get() = targetFile.readBytes().joinToString("")
    }
}