package com.github.aakumykov.copy_between_streams_with_speed

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.aakumykov.copy_between_streams_with_speed.utils.random
import org.junit.Assert
import org.junit.runner.RunWith
import java.io.File
import java.io.InputStream
import java.io.OutputStream

@RunWith(AndroidJUnit4::class)
abstract class TestBase {

    protected val appContext: Context by lazy { InstrumentationRegistry.getInstrumentation().targetContext }
    protected val testsDir: File = appContext.cacheDir

    protected val sourceDir: File = testsDir
    protected val targetDir: File = testsDir

    protected val sourceFileName = "the_source.file"
    protected val targetFileName = "the_target.file"

    protected val sourceFile = File(sourceDir, sourceFileName)
    protected val targetFile = File(targetDir, targetFileName)

    protected val sourceFileContents: String get() = fileContents(sourceFile)
    protected val targetFileContents: String get() = fileContents(targetFile)

    protected val sourceFileStream: InputStream get() = sourceFile.inputStream()
    protected val targetFileStream: OutputStream get() = targetFile.outputStream()

    protected val storageFreeSpace: Long = appContext.cacheDir.usableSpace

    protected fun fileContents(file: File): String = file.readBytes().asString

    protected val ByteArray.asString: String get() = this.joinToString("")


    protected fun prepareSourceAndTargetFiles(dataSizeBytes: Int = 100) {
        prepareSourceFile(dataSizeBytes)
        prepareTargetFile()
    }


    protected fun clearSourceFile() {
        // Выполнение "очистки" (удаления файлов) в блоке @After не срабатывало, ---------
        // поэтому производится здесь.
        sourceFile.delete()
        Assert.assertFalse(sourceFile.exists())
    }

    protected fun clearTargetFile() {
        targetFile.delete()
        Assert.assertFalse(targetFile.exists())
    }

    protected fun prepareSourceFile(dataSizeBytes: Int) {
        clearSourceFile()

        sourceFile.createNewFile()
        Assert.assertTrue(sourceFile.exists())
        Assert.assertEquals(0L, sourceFile.length())

        writeTestDataToFile(sourceFile, dataSizeBytes)
        Assert.assertEquals(dataSizeBytes.toLong(), sourceFile.length())
    }

    protected fun prepareTargetFile() {
        // Выполнение "очистки" (удаления файлов) в блоке @After не срабатывало, ---------
        // поэтому производится здесь.
        clearTargetFile()
        targetFile.createNewFile()
        Assert.assertTrue(targetFile.exists())
        Assert.assertEquals(0L, targetFile.length())
    }


    protected fun writeTestDataToFile(file: File, dataSizeBytes: Int) {
        val pieceSize = DEFAULT_BUFFER_SIZE
        val mainSteps = dataSizeBytes / pieceSize
        val additionalBytesCount = dataSizeBytes - (mainSteps * pieceSize)
        file.outputStream().use { outputStream ->
            repeat(mainSteps) {
                outputStream.write(random.nextBytes(pieceSize))
            }
            outputStream.write(random.nextBytes(additionalBytesCount))
        }
    }
}