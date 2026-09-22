package com.github.aakumykov.copy_between_streams_with_speed

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.aakumykov.copy_between_streams_with_speed.utils.humanDecimalPlaces
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

    protected val newSourceFileStream: InputStream get() = sourceFile.inputStream()
    protected val newTargetFileStream: OutputStream get() = targetFile.outputStream()

    protected val storageFreeSpace: Long = appContext.cacheDir.usableSpace

    protected fun fileContents(file: File): String = file.readBytes().asString

    protected val ByteArray.asString: String get() = this.joinToString("")


    protected fun prepareSourceAndTargetFiles(dataSizeBytes: Int = 100): String {
        prepareSourceFile(dataSizeBytes)
        prepareTargetFile()
        return sourceFileContents
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
        println("prepareSourceFile(${dataSizeBytes})")
        clearSourceFile()

        sourceFile.createNewFile()
        Assert.assertTrue(sourceFile.exists())
        Assert.assertEquals(0L, sourceFile.length())

        writeTestDataToFile(sourceFile, dataSizeBytes)
        Assert.assertEquals(dataSizeBytes.toLong(), sourceFile.length())
    }

    protected fun prepareTargetFile() {
        println("prepareTargetFile()")
        // Выполнение "очистки" (удаления файлов) в блоке @After не срабатывало, ---------
        // поэтому производится здесь.
        clearTargetFile()
        targetFile.createNewFile()
        Assert.assertTrue(targetFile.exists())
        Assert.assertEquals(0L, targetFile.length())
    }


    protected fun writeTestDataToFile(file: File, dataSizeBytes: Int) {
        println("writeTestDataToFile(${dataSizeBytes.humanDecimalPlaces}) СТАРТ")

        val pieceSize = DEFAULT_BUFFER_SIZE
        val mainSteps = dataSizeBytes / pieceSize

        var alreadyWritten = 0

        file.outputStream().use { outputStream ->

            fun writeAndDisplay(data: ByteArray) {
                outputStream.write(data)
                val count = data.size
                alreadyWritten += count
                println("записано ${count}, всего ${alreadyWritten.humanDecimalPlaces}")
            }

            repeat(mainSteps) {
                writeAndDisplay(random.nextBytes(pieceSize))
            }

            val additionalBytesCount = dataSizeBytes - alreadyWritten
            writeAndDisplay(random.nextBytes(additionalBytesCount))
        }

        Assert.assertEquals(
            dataSizeBytes.toLong(),
            file.length()
        )

        println("writeTestDataToFile(${dataSizeBytes.humanDecimalPlaces}) ФИНИШ")
    }
}