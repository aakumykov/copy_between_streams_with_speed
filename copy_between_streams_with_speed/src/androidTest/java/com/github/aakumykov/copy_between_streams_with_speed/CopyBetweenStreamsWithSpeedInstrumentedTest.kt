package com.github.aakumykov.copy_between_streams_with_speed

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.aakumykov.copy_between_streams_with_speed.utils.random
import com.github.aakumykov.copy_between_streams_with_speed.utils.repeatFromTo
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

    В) При заданной скорости реальное время соответствует ожидаемому
    (с небольшой погрешностью) [work_time_matches_estimated_time]

    Г) Разные значения других аргументов:
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
    fun simple_copy_test() {

        val size = 10_000
        val speed = 50_000
        val estimatedTimeMs = size.toFloat() / speed

        val realTimeMs = doCopy(
            size,
            speed,
            100
        )

        val percent = (realTimeMs / estimatedTimeMs) * 100

        println("estTime: $estimatedTimeMs, realTime: $realTimeMs (${percent}%)")
    }

    @Test
    fun work_time_matches_estimated_time_on_low_data_size() {

        val dataSizeFrom = 1
        val dataSizeTo=  10
        val step = 1
        val baseSpeed = 1

        repeatFromTo(1,11) { speedMultiplier ->

            val res = test_data_sizes(
                dataSizeFrom,
                dataSizeTo,
                step,
                baseSpeed,
                speedMultiplier = speedMultiplier,
                discretizationHz = 1
            )

            println("---- данные: ${res.first}, скорость x${speedMultiplier} ----")
            println(res.second.joinToString("\n"))
            println()
        }
    }

    @Test
    fun work_time_matches_estimated_time_on_big_data_size() {

        val dataSizeFrom = 10_000
        val dataSizeTo=  20_000
        val step = 1000
        val baseSpeed = 10_000

        println("============= ОБЪЁМ ДАННЫХ (${dataSizeFrom}-${dataSizeTo} БАЙТ, ШАГ $step, БАЗОВАЯ СКОРОСТЬ $baseSpeed) =============")
        println()

        repeatFromTo(1,11) { speedMultiplier ->

            val res = test_data_sizes(
                fromDataSize = dataSizeFrom,
                toDataSize = dataSizeTo,
                dataStep = step,
                baseSpeedBytesPerSec = baseSpeed,
                speedMultiplier = speedMultiplier
            )

            println("---- данные: ${res.first}, скорость x${speedMultiplier} ----")
            println(res.second.joinToString("\n"))
            println()
        }
    }

    /**
     * @return Pair<DataSizeBytes,WorkTimePercentsOfEstimated>
     */
    private fun test_data_sizes(
        fromDataSize: Int,
        toDataSize: Int,
        dataStep: Int,
        baseSpeedBytesPerSec: Int,
        speedMultiplier: Int,
        discretizationHz: Int = 10,
    ): Pair<Int,List<Float>> {

        val dataSizeFrom = fromDataSize
        val dataSizeTo = toDataSize

        val percentList = mutableListOf<Float>()
        var size = 0

        repeatFromTo(dataSizeFrom,dataSizeTo+1, dataStep) { dataSizeBytes ->

            size = dataSizeBytes

            val speedBytesPerSec = baseSpeedBytesPerSec * speedMultiplier

            val realTimeMs = doCopy(
                dataSizeBytes = dataSizeBytes,
                speedBytesPerSec = speedBytesPerSec,
                discretizationHz = discretizationHz
            )

            val estimatedTimeMs = (dataSizeBytes.toFloat() / speedBytesPerSec) * 1000

            val differencePercents = ((realTimeMs.toFloat() / estimatedTimeMs) * 100)

            percentList.add(differencePercents)

            /*println("dataSize: $dataSizeBytes, " +
                    "speed: $speedBytesPerSec, " +
                    "x${speedMultiplier}, " +
                    "estimatedTimeMs: $estimatedTimeMs, " +
                    "realTimeMs: $realTimeMs (${differencePercents}%)")*/
        }

        /*println("----- Процентовки для скорости x${speedMultiplier} для данных ${dataSizeFrom}-${dataSizeTo} байт -----")
        println(
            percentList.sortedDescending().joinToString("\n")
        )
        println("------------")*/

        return Pair(size, percentList.sortedDescending())
    }


    /**
     * @return Затраченное на копирование время, мс.
     */
    private fun doCopy(
        dataSizeBytes: Int,
        speedBytesPerSec: Int,
        discretizationHz: Int = 10
    ): Long {

        println("doCopy(dataSizeBytes:$dataSizeBytes, speedBytesPerSec:$speedBytesPerSec, discretizationHz:$discretizationHz)")

        prepareSourceAndTargetFiles(testData(dataSizeBytes))

        val startTime = System.currentTimeMillis()

        sourceFileStream.use { inputStream ->
            targetFileStream.use { outputStream ->
                copyBetweenStreamsWithSpeed(
                    inputStream = inputStream,
                    outputStream = outputStream,
                    speedBytesPerSecond = speedBytesPerSec,
                    discretizationHz = discretizationHz
                )
            }
        }

        val realTimeMs = System.currentTimeMillis() - startTime

        return realTimeMs
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