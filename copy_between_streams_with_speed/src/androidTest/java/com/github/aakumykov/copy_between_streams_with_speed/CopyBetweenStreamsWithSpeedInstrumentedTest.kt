package com.github.aakumykov.copy_between_streams_with_speed

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.aakumykov.copy_between_streams_with_speed.utils.percent
import com.github.aakumykov.copy_between_streams_with_speed.utils.random
import com.github.aakumykov.copy_between_streams_with_speed.utils.repeatFromTo
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToLong

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
      [progress_callback_is_invoked_at_least_one_time_on_data_bugger_than_buffer_size];

    - коллбек прогресса НЕ вызывается, если размер файла равен нулю
      [progress_callback_not_invoked_on_zero_file_size];

    - коллбек прогресса вызывается правильное количество раз (НУЖНО ЛИ?)
      [progress_callback_invoked_expected_times];

    Б) Исключения:
     - нулевая скорость
       [exception_thrown_on_zero_speed_argument];

    В) При заданной скоростиреальная скорость отдичается не сильно
       (зависит от максимальной скорости работы накопителя)
    [real_speed_matches_expected_speed_on_low_data_size]

    Г) При заданной скорости реальное время соответствует ожидаемому
    (с небольшой погрешностью)
    [work_time_matches_estimated_time_on_low_data_size]
    [work_time_matches_estimated_time_on_big_data_size]

    Д) Разные значения других аргументов:
     - скорости [works_with_different_speed_values];
     - частота дискретизации
    */

    companion object {
        val TAG: String = CopyBetweenStreamsWithSpeedInstrumentedTest::class.java.simpleName
        const val DEFAULT_DATA_SIZE = 1024
    }

    private val appContext by lazy { InstrumentationRegistry.getInstrumentation().targetContext }
    private val testsDir: File = appContext.cacheDir

    private val sourceDir: File = testsDir
    private val targetDir: File = testsDir

    private val sourceFileName = "the_source.file"
    private val targetFileName = "the_target.file"

    private val sourceFile = File(sourceDir, sourceFileName)
    private val targetFile = File(targetDir, targetFileName)

    private val sourceFileContents: String get() = fileContents(sourceFile)
    private val targetFileContents: String get() = fileContents(targetFile)

    private val sourceFileStream: InputStream get() = sourceFile.inputStream()
    private val targetFileStream: OutputStream get() = targetFile.outputStream()


    private fun fileContents(file: File): String = file.readBytes().asString

    val ByteArray.asString: String get() = this.joinToString("")

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
        Assert.assertEquals(0L, sourceFile.length())

        targetFile.createNewFile()
        Assert.assertTrue(targetFile.exists())
        Assert.assertEquals(0L, targetFile.length())

        sourceFile.writeBytes(testData)
        Assert.assertEquals(testData.size.toLong(), sourceFile.length())
        Assert.assertEquals(testData.asString, sourceFileContents)
    }


    @Test
    fun test_of_this_test_system() {
        val size = 10
        val testData = testData(size)
        prepareSourceAndTargetFiles(testData)

        sourceFileStream.copyTo(targetFileStream)

        Assert.assertEquals(
            size.toLong(),
            targetFile.length()
        )
        Assert.assertEquals(
            testData.asString,
            targetFileContents
        )
        Assert.assertEquals(
            sourceFileContents,
            targetFileContents
        )
    }


    @Test
    fun source_file_is_copied_to_target_file() {
        repeat(100) { fileSizeWithZero ->
            println("размер данных: $fileSizeWithZero")
            val testData = testData(fileSizeWithZero)
            prepareSourceAndTargetFiles(testData)
            copyBetweenStreamsWithSpeed(
                inputStream = sourceFileStream,
                outputStream = targetFileStream,
                speedBytesPerSec = 100
            )
            // Проверяю, что после работы функции copyBetweenStreamsWithSpeed
            // исходный и конечный файлы содержат верные данные.
            Assert.assertEquals(
                testData.asString,
                targetFileContents
            )
            Assert.assertEquals(
                testData.asString,
                sourceFileContents
            )
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
                finishCallback = { _,_,_ ->
                    isInvoked.set(true)
                }
            )
            Assert.assertTrue(isInvoked.get())
        }
    }


    @Test
    fun progress_callback_is_invoked_at_least_one_time_on_data_bugger_than_buffer_size() {
        repeat(10) { i ->
            val nonZeroFileSize = DEFAULT_BUFFER_SIZE * (i+1)
            val isInvoked = AtomicBoolean(false)
            prepareSourceAndTargetFiles(testData(nonZeroFileSize))
            copyBetweenStreamsWithSpeed(
                inputStream = sourceFileStream,
                outputStream = targetFileStream,
                dataTransferStepsPerSecond = 10,
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
        val dataTransferStepsPerSecond = 10

        var counter = 0

        prepareSourceAndTargetFiles(testData(dataSizeByte))

        copyBetweenStreamsWithSpeed(
            inputStream = sourceFileStream,
            outputStream = targetFileStream,
            progressCallback = { totalBytesTransferred, speed ->
                counter++
                println("totalBytesTransferred: $totalBytesTransferred, speed: $speed")
            },
            dataTransferStepsPerSecond = dataTransferStepsPerSecond,
            speedBytesPerSec = speedByteSec,
        )

        Assert.assertEquals(
            (dataSizeByte / speedByteSec) * dataTransferStepsPerSecond,
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
                speedBytesPerSec = 0
            )
        }
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
                dataTransferStepsPerSecond = 1
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
        dataTransferStepsPerSecond: Int = 10,
    ): Pair<Int,List<Float>> {

        val percentList = mutableListOf<Float>()
        var size = 0

        repeatFromTo(fromDataSize, toDataSize + 1, dataStep) { dataSizeBytes ->

            size = dataSizeBytes

            val speedBytesPerSec = baseSpeedBytesPerSec * speedMultiplier

            val realTimeMs = doCopy(
                dataSizeBytes = dataSizeBytes,
                speedBytesPerSec = speedBytesPerSec,
                dataTransferStepsPerSecond = dataTransferStepsPerSecond,
            )

            val estimatedTimeMs = (dataSizeBytes.toFloat() / speedBytesPerSec) * 1000

            val differencePercents = ((realTimeMs.toFloat() / estimatedTimeMs) * 100)

            percentList.add(differencePercents)
        }

        return Pair(size, percentList.sortedDescending())
    }


    /**
     * @return Затраченное на копирование время, мс.
     */
    private fun doCopy(
        dataSizeBytes: Int,
        speedBytesPerSec: Int,
        dataTransferStepsPerSecond: Int = 10,
    ): Long {

        println("doCopy(dataSizeBytes:$dataSizeBytes, speedBytesPerSec:$speedBytesPerSec, dataTransferStepsPerSecond:$dataTransferStepsPerSecond)")

        prepareSourceAndTargetFiles(testData(dataSizeBytes))

        val startTime = System.currentTimeMillis()

        sourceFileStream.use { inputStream ->
            targetFileStream.use { outputStream ->
                copyBetweenStreamsWithSpeed(
                    inputStream = inputStream,
                    outputStream = outputStream,
                    speedBytesPerSec = speedBytesPerSec,
                    dataTransferStepsPerSecond = dataTransferStepsPerSecond,
                )
            }
        }

        return System.currentTimeMillis() - startTime
    }


    @Test
    fun works_with_different_speed_values() {

        repeat(100) { i ->

            val speedBytesPerSec = i+2
            val discretization = i+1

            prepareSourceAndTargetFiles(testData(100))

            copyBetweenStreamsWithSpeed(
                inputStream = sourceFileStream,
                outputStream = targetFileStream,
                speedBytesPerSec = speedBytesPerSec,
                dataTransferStepsPerSecond = discretization,
            )

            Assert.assertEquals(sourceFileContents, targetFileContents)
        }
    }


    @Test
    fun simple_speed_test() {
        val size = 1024
        val speed = size / 2
        val timeMs = (size.toFloat() / speed).roundToLong() * 1000
        prepareSourceAndTargetFiles(testData(size))
        copyBetweenStreamsWithSpeed(
            sourceFileStream,
            targetFileStream,
            speedBytesPerSec = speed,
            finishCallback = { _,realTimeMs,realSpeed ->
                val speedPercent = percent(realSpeed, speed.toLong())
                val timePercent = percent(realTimeMs, timeMs)
                Log.d(TAG, "size: $size")
                Log.d(TAG, "speed: $speed, realSpeed: $realSpeed ($speedPercent)%")
                Log.d(TAG, "expectedTime: $timeMs, realTime: $realTimeMs ($timePercent)%")
            }
        )
    }


    @Test
    fun real_speed_matches_expected_speed_on_low_data_size() {
        listOf(
//            1, 10, 100, 1000, 10_000, 100_000, 1000_000
            100_000
        ).forEach { sizeMultiplier ->
            println("----- множитель размера: $sizeMultiplier -----")
            repeat(2) { i ->
                val dataSize = (i+1) * sizeMultiplier
                val expectedSpeed = sizeMultiplier * 10
                testRealSpeed(dataSize, expectedSpeed)
            }
        }
    }

    private fun testRealSpeed(dataSize: Int, expectedSpeed: Int) {
        prepareSourceAndTargetFiles(testData(dataSize))
        copyBetweenStreamsWithSpeed(
            sourceFileStream,
            targetFileStream,
            speedBytesPerSec = expectedSpeed,
            finishCallback = { _,_,realSpeedBytesPerSec ->
                val speedPercentage = percent(realSpeedBytesPerSec, expectedSpeed.toLong())
                println("данные: $dataSize, скорость: $expectedSpeed, реальная скорость: $realSpeedBytesPerSec ($speedPercentage)%")
                Assert.assertTrue(speedPercentage >= 80.toDouble() && speedPercentage <= 110)
            }
        )
    }
}