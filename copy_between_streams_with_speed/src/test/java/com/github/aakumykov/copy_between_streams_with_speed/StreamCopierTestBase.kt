package com.github.aakumykov.copy_between_streams_with_speed

import com.github.aakumykov.copy_between_streams_with_speed.utils.random
import org.junit.Assert
import java.io.File
import java.io.InputStream
import java.io.OutputStream

open class StreamCopierTestBase() {

    protected fun prepareSourceAndTargetFiles(sizeBytes: Int) {
        prepareTempDir()
        deleteFile(sourceFile)
        deleteFile(targetFile)
        writeRandomBytesToFile(sourceFile, sizeBytes)
    }

    protected fun prepareTempDir() {
        val tempDir = File(TEMP_DIR_NAME)
        if (!tempDir.exists()) Assert.assertTrue(tempDir.mkdirs())
    }

    protected fun deleteFile(file: File) {
        file.delete()
        Assert.assertFalse(file.exists())
    }

    protected fun writeRandomBytesToFile(file: File, sizeBytes: Int) {
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
        protected const val TEMP_DIR_NAME = "test_temp_dir"
        protected const val SOURCE_FILE_NAME = "source.file"
        protected const val TARGET_FILE_NAME = "target.file"
        @JvmStatic protected val sourceFile = File(TEMP_DIR_NAME, SOURCE_FILE_NAME)
        @JvmStatic protected val targetFile = File(TEMP_DIR_NAME, TARGET_FILE_NAME)
        @JvmStatic protected val sourceFileStream: InputStream get() = sourceFile.inputStream()
        @JvmStatic protected val targetFileStream: OutputStream get() = targetFile.outputStream()
        @JvmStatic protected val sourceFileData: String get() = sourceFile.readBytes().joinToString("")
        @JvmStatic protected val targetFileData: String get() = targetFile.readBytes().joinToString("")
    }
}