package com.github.aakumykov.copy_between_streams_with_speed

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.aakumykov.copy_between_streams_with_speed.utils.random
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class CopyBetweenStreamsWithSpeedInstrumentedTest {

    /**
    План теста:
    А) Обычная работа:
    - файл копируется, содержимое совпадает, с размером 0 до Н байт
      [source_file_is_copied_to_target_file];

    - вызывается коллбек окончания работы
      [finish_callback_is_invoked];

    - коллбек прогресса вызывается по крайней мере один раз, если размер не равен нулю
      [progress_callback_is_invoked_at_least_one_time_on_non_zero_file];

    - коллбек прогресса НЕ вызывается, если размер файла равен нулю
      [progress_callback_not_invoked_on_zero_file_size];

    - коллбек прогресса вызывается правильное количество раз
      [progress_callback_invoked_expected_times];

    Б) Исключения:
     - нулевая скорость
       [exception_thrown_on_zero_speed_argument];

     - значение дискретизации выше значения скорости
       [exception_thrown_if_discretization_greater_than_speed];

    В) Разные значения необязательных аргументов:
     - скорости [works_with_different_speed_values];
     - размер буфера
     - частота дискретизации
     */

    companion object {
        const val DEFAULT_DATA_SIZE = 1024
    }

    private val appContext by lazy { InstrumentationRegistry.getInstrumentation().targetContext }
    private val testsDir: File = appContext.cacheDir

    private val sourceDir: File get() = testsDir
    private val targetDir: File get() = testsDir

    private val sourceFileName = "the_source.file"
    private val targetFileName = "the_target.file"

    private val sourceFile get() = File(sourceDir, sourceFileName)
    private val targetFile get() = File(targetDir, targetFileName)

    private val sourceFileContents: String get() = sourceFile.readBytes().joinToString("")
    private val targetFileContents: String get() = targetFile.readBytes().joinToString("")

    private val sourceFileStream: InputStream get() = sourceFile.inputStream()
    private val targetFileStream: OutputStream get() = targetFile.outputStream()


    private fun testData(size: Int = DEFAULT_DATA_SIZE): ByteArray {
        return random.nextBytes(size).apply {
            Assert.assertEquals(size, this.size)
        }

    }

    private fun prepareSourceAndTargetFiles(testData: ByteArray) {

        //
        // -------- Выполнение "очистки" (удаления файлов) в блоке @After не срабатывало, ---------
        //          поэтому производится здесь.
        sourceFile.delete()
        Assert.assertFalse(sourceFile.exists())

        targetFile.delete()
        Assert.assertFalse(targetFile.exists())
        // ----------------------------------------------------------------------------------------

        sourceFile.createNewFile()
        Assert.assertTrue(sourceFile.exists())

        targetFile.createNewFile()
        Assert.assertTrue(targetFile.exists())

        sourceFile.writeBytes(testData)
        Assert.assertEquals(testData.size.toLong(), sourceFile.length())

        Assert.assertEquals(0L, targetFile.length())
    }


    @Test
    fun source_file_is_copied_to_target_file() {
        repeat(100) { fileSizeWithZero ->
            println("размер фйла: $fileSizeWithZero")

            prepareSourceAndTargetFiles(testData(fileSizeWithZero))

            copyBetweenStreamsWithSpeed(
                inputStream = sourceFileStream,
                outputStream = targetFileStream
            )

            Assert.assertEquals(
                sourceFile.length(),
                targetFile.length()
            )
            Assert.assertEquals(sourceFileContents, targetFileContents)
        }
    }


    @Test
    fun finish_callback_is_invoked() {
        repeat(100) { fileSizeWithZero ->
            val isInvoked = AtomicBoolean(false)
            prepareSourceAndTargetFiles(testData(fileSizeWithZero))
            copyBetweenStreamsWithSpeed(
                inputStream = sourceFileStream,
                outputStream = targetFileStream,
                finishCallback = { _,_ ->
                    isInvoked.set(true)
                }
            )
            Assert.assertTrue(isInvoked.get())
        }
    }


    @Test
    fun progress_callback_is_invoked_at_least_one_time_on_non_zero_file() {
        repeat(100) { i ->
            val nonZeroFileSize = i+1
            val isInvoked = AtomicBoolean(false)
            prepareSourceAndTargetFiles(testData(nonZeroFileSize))
            copyBetweenStreamsWithSpeed(
                inputStream = sourceFileStream,
                outputStream = targetFileStream,
                progressCallback = { _,_ ->
                    isInvoked.set(true)
                }
            )
            Assert.assertTrue(isInvoked.get())
        }
    }


    @Test
    fun progress_callback_not_invoked_on_zero_file_size() {
        val isInvoked = AtomicBoolean(false)
        prepareSourceAndTargetFiles(testData(0))
        copyBetweenStreamsWithSpeed(
            inputStream = sourceFileStream,
            outputStream = targetFileStream,
            progressCallback = { _,_ ->
                isInvoked.set(true)
            }
        )
        Assert.assertFalse(isInvoked.get())
    }


    @Test
    fun progress_callback_invoked_expected_times() {

        val dataSizeByte = 1000
        val speedByteSec = 100
        val discretizationHz = 10

        var counter = 0

        prepareSourceAndTargetFiles(testData(dataSizeByte))

        copyBetweenStreamsWithSpeed(
            inputStream = sourceFileStream,
            outputStream = targetFileStream,
            progressCallback = { totalBytesTransferred, speed ->
                counter++
                println("totalBytesTransferred: $totalBytesTransferred, speed: $speed")
            },
            discretizationHz = discretizationHz,
            speedBytesPerSecond = speedByteSec,
            printDebug = false
        )

        Assert.assertEquals(
            (dataSizeByte / speedByteSec) * discretizationHz,
            counter
        )
    }


    @Test
    fun exception_thrown_on_zero_speed_argument() {
        Assert.assertThrows(IllegalArgumentException::class.java) {
            prepareSourceAndTargetFiles(testData(0))
            copyBetweenStreamsWithSpeed(
                inputStream = sourceFileStream,
                outputStream = targetFileStream,
                speedBytesPerSecond = 0
            )
        }
    }


    @Test
    fun exception_thrown_if_discretization_greater_than_speed() {
        Assert.assertThrows(IllegalArgumentException::class.java) {
            prepareSourceAndTargetFiles(testData(0))
            copyBetweenStreamsWithSpeed(
                inputStream = sourceFileStream,
                outputStream = targetFileStream,
                speedBytesPerSecond = 10,
                discretizationHz = 20
            )
        }
    }


    @Test
    fun works_with_different_speed_values() {
        repeat(100) { i ->
            val speed = i+2
            val discretization = i+1
            prepareSourceAndTargetFiles(testData(100))
            copyBetweenStreamsWithSpeed(
                inputStream = sourceFileStream,
                outputStream = targetFileStream,
                speedBytesPerSecond = speed,
                discretizationHz = discretization,
                printDebug = true
            )
            Assert.assertEquals(sourceFileContents, targetFileContents)
        }
    }
}