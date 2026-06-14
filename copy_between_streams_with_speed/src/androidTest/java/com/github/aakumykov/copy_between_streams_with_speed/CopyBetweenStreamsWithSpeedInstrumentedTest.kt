package com.github.aakumykov.copy_between_streams_with_speed

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.aakumykov.copy_between_streams_with_speed.ext.roundToFloatingDigits
import com.github.aakumykov.copy_between_streams_with_speed.utils.humanReadableByteCount
import com.github.aakumykov.copy_between_streams_with_speed.utils.humanSizeBinary
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
import kotlin.math.roundToInt

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

    В) При заданной скорости реальная скорость отличается не сильно
       (зависит от максимальной скорости работы накопителя)
    [constant_speed_various_data_size]

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

    private val storageFreeSpace: Long = appContext.cacheDir.usableSpace

    private fun fileContents(file: File): String = file.readBytes().asString

    val ByteArray.asString: String get() = this.joinToString("")


    private fun prepareSourceAndTargetFiles(dataSizeBytes: Int) {

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

        writeTestDataToFile(sourceFile, dataSizeBytes)
        Assert.assertEquals(dataSizeBytes.toLong(), sourceFile.length())
    }

    private fun writeTestDataToFile(file: File, dataSizeBytes: Int) {
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

    private val deviceStorageSpeedBytesPerSec: Int by lazy {
        buildList {
            repeat(10) { i ->
                val startTimeMs = System.currentTimeMillis()
                val dataSize = i * 1_000_000
                prepareSourceAndTargetFiles(dataSize)
                sourceFileStream.use { sS ->
                    targetFileStream.use { tS ->
                        sS.copyTo(tS)
                    }
                }
                val copyTimeSeconds = (System.currentTimeMillis() - startTimeMs).toFloat() / 1000
                val deviceSpeed = (dataSize.toFloat() / copyTimeSeconds).roundToInt()
                add(deviceSpeed)
            }
        }.max()
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

        prepareSourceAndTargetFiles(dataSizeBytes)

        val startTime = System.currentTimeMillis()

        sourceFileStream.use { inputStream ->
            targetFileStream.use { outputStream ->
                copyBetweenStreamsWithSpeed(
                    inputStream = inputStream,
                    outputStream = outputStream,
                    speedBytesPerSec = speedBytesPerSec,
                    stepsPerSecond = dataTransferStepsPerSecond,
                )
            }
        }

        return System.currentTimeMillis() - startTime
    }


    @Test
    fun test_of_this_test_system() {
        val size = 10
        prepareSourceAndTargetFiles(size)

        sourceFileStream.copyTo(targetFileStream)

        Assert.assertEquals(
            size.toLong(),
            targetFile.length()
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
            prepareSourceAndTargetFiles(fileSizeWithZero)
            copyBetweenStreamsWithSpeed(
                inputStream = sourceFileStream,
                outputStream = targetFileStream,
                speedBytesPerSec = 100
            )
            Assert.assertEquals(
                fileSizeWithZero,
                sourceFile.length()
            )
            Assert.assertEquals(
                fileSizeWithZero,
                targetFile.length()
            )
            Assert.assertEquals(
                sourceFileContents,
                targetFileContents
            )
        }
    }


    @Test
    fun finish_callback_is_invoked() {
        repeat(100) { fileSizeWithZero ->
            val isInvoked = AtomicBoolean(false)
            prepareSourceAndTargetFiles(fileSizeWithZero)
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
            prepareSourceAndTargetFiles(nonZeroFileSize)
            copyBetweenStreamsWithSpeed(
                inputStream = sourceFileStream,
                outputStream = targetFileStream,
                stepsPerSecond = 10,
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
        prepareSourceAndTargetFiles(0)
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

        prepareSourceAndTargetFiles(dataSizeByte)

        copyBetweenStreamsWithSpeed(
            inputStream = sourceFileStream,
            outputStream = targetFileStream,
            progressCallback = { totalBytesTransferred, speed ->
                counter++
                println("totalBytesTransferred: $totalBytesTransferred, speed: $speed")
            },
            stepsPerSecond = dataTransferStepsPerSecond,
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


    @Test
    fun works_with_different_speed_values() {

        repeat(100) { i ->

            val speedBytesPerSec = i+2
            val discretization = i+1

            prepareSourceAndTargetFiles(100)

            copyBetweenStreamsWithSpeed(
                inputStream = sourceFileStream,
                outputStream = targetFileStream,
                speedBytesPerSec = speedBytesPerSec,
                stepsPerSecond = discretization,
            )

            Assert.assertEquals(sourceFileContents, targetFileContents)
        }
    }


    /*@Test
    fun simple_speed_test() {
        val size = 1024 * 1024
        val speed = 1024 * 1024
        val timeMs = (size.toFloat() / speed).roundToLong() * 1000
        prepareSourceAndTargetFiles(size)
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
    }*/


    @Test
    fun constant_speed_various_data_size() {

        val minSpeedPercentage: Double = 60.toDouble()
        val maxSpeedPercentage: Double = 120.toDouble()

        Log.d(TAG, "места в хранилище: ${storageFreeSpace.humanSizeBinary()}")

        val expectedSpeedBytesPerSec = (deviceStorageSpeedBytesPerSec * 0.9f).roundToInt()
        Log.d(TAG, "используемая скорость: ${expectedSpeedBytesPerSec.humanSizeBinary()}/с")

        val maxDataSize = (expectedSpeedBytesPerSec * 10).toLong().coerceAtMost(storageFreeSpace)
        Log.d(TAG, "максимальный размер данных: ${maxDataSize.humanSizeBinary()}")

        val dataSizeStep = (expectedSpeedBytesPerSec.toFloat() / 10).roundToInt()
        Log.d(TAG, "шаг данных: ${dataSizeStep.humanSizeBinary()}")

        Assert.assertTrue(storageFreeSpace > dataSizeStep.toLong())

        var dataSizeBytes = 0

        while(dataSizeBytes < maxDataSize) {
            prepareSourceAndTargetFiles(dataSizeBytes)
            sourceFileStream.use { sS ->
                targetFileStream.use { tS ->
                    copyBetweenStreamsWithSpeed(
                        inputStream = sS,
                        outputStream = tS,
                        speedBytesPerSec = expectedSpeedBytesPerSec,
                        finishCallback = { _,_,realSpeedBytesPerSec ->
                            val speedPercentage = percent(realSpeedBytesPerSec, expectedSpeedBytesPerSec.toLong())
                            val isAnomaly = 0L != realSpeedBytesPerSec && speedPercentage !in minSpeedPercentage..maxSpeedPercentage
                            val anomalyMark = if (isAnomaly) "АНОМАЛИЯ " else ""
                            Log.d(TAG, "${anomalyMark}(${speedPercentage.roundToFloatingDigits(2)}%) данные: ${humanReadableByteCount(dataSizeBytes, decimalNotation = false)}" +
                                    ", заданная скорость: ${humanReadableByteCount(expectedSpeedBytesPerSec, decimalNotation = false)}/с" +
                                    ", реальная скорость: ${humanReadableByteCount(realSpeedBytesPerSec, decimalNotation = false)}")
                            Assert.assertFalse(isAnomaly)
                        }
                    )
                }
            }
            dataSizeBytes += dataSizeStep
        }
    }


    @Test
    fun test_100b_with_low_speeds() {
        test_with_constant_size_and_various_speed(
            dataSizeBytes = 100,
            speedFromBytesPerSec = 1,
            speedToBytesPerSec = 100,
            speedStep = 10,
        )
    }

    @Test
    fun test_1kb_with_low_speeds() {
        test_with_constant_size_and_various_speed(
            dataSizeBytes = 1.kilobytes,
            speedFromBytesPerSec = 1.kilobytes,
            speedToBytesPerSec = 100.kilobytes,
            speedStep = 10.kilobytes,
        )
    }

    @Test
    fun test_1mb_with_low_speeds() {
        test_with_constant_size_and_various_speed(
            dataSizeBytes = 1.megabytes,
            speedFromBytesPerSec = 10.kilobytes,
            speedToBytesPerSec = 100.kilobytes,
            speedStep = 10.kilobytes,
        )
    }


    @Test
    fun test_1mb_with_medium_speeds() {
        test_with_constant_size_and_various_speed(
            dataSizeBytes = 1.megabytes,
            speedFromBytesPerSec = 100.kilobytes,
            speedToBytesPerSec = 1.megabytes,
            speedStep = 100.kilobytes,
        )
    }


    @Test
    fun test_1mb_with_hi_speeds() {
        val speedFrom = 100.kilobytes
        val speedTo = deviceStorageSpeedBytesPerSec
        val speedStep = (deviceStorageSpeedBytesPerSec - speedFrom) / 10

        test_with_constant_size_and_various_speed(
            dataSizeBytes = 1.megabytes,
            speedFromBytesPerSec = speedFrom,
            speedToBytesPerSec = speedTo,
            speedStep = speedStep,
        )
    }

    @Test
    fun test_100mb_with_10mb_speed() {
        test_size_with_speed(100.megabytes, 10.megabytes)
    }

    @Test
    fun test_1mb_with_speed_2mb() {
        test_size_with_speed(1.megabytes, 2.megabytes)
    }


    @Test
    fun test_with_various_steps_from_1_to_10() {
        val stepsFrom = 1
        val stepsTo = 10
        val stepForSteps = 1
        repeat(stepsTo-stepsFrom){ i ->
            val steps = (i+1) * stepsFrom + stepForSteps
            test_size_with_speed_and_steps(1.megabytes, 2.megabytes, steps)
        }
    }

    @Test
    fun test_with_various_steps_from_10_to_100() {
        val stepsFrom = 10
        val stepsTo = 100
        val stepForSteps = 10
        repeat(stepsTo-stepsFrom){ i ->
            val steps = (i+1) * stepsFrom + stepForSteps
            test_size_with_speed_and_steps(1.megabytes, 2.megabytes, steps)
        }
    }


    private fun test_size_with_speed(sizeBytes: Int, speedBytesPerSec: Int, stepsPerSecond: Int = 100) {
        prepareSourceAndTargetFiles(sizeBytes)
        copyBetweenStreamsWithSpeed(
            inputStream = sourceFileStream,
            outputStream = targetFileStream,
            speedBytesPerSec = speedBytesPerSec,
            stepsPerSecond = stepsPerSecond,
            finishCallback = { _,_,speed ->
                Log.d(TAG, "${speed.humanSizeBinary()}/с (${percent(speed,speedBytesPerSec.toLong()).roundToFloatingDigits(2)}%)")
            }
        )
    }


    private fun test_size_with_speed_and_steps(
        sizeBytes: Int, speedBytesPerSec: Int, stepsPerSecond: Int = 100
    ) {
        prepareSourceAndTargetFiles(sizeBytes)
        copyBetweenStreamsWithSpeed(
            inputStream = sourceFileStream,
            outputStream = targetFileStream,
            speedBytesPerSec = speedBytesPerSec,
            stepsPerSecond = stepsPerSecond,
            finishCallback = { _,_,speed ->
                Log.d(TAG, "${stepsPerSecond} шагов ${speed.humanSizeBinary()}/с (${percent(speed,speedBytesPerSec.toLong()).roundToFloatingDigits(2)}%)")
            }
        )
    }


    private fun test_with_constant_size_and_various_speed(
        dataSizeBytes: Int,
        speedFromBytesPerSec: Int,
        speedToBytesPerSec: Int,
        speedStep: Int
    ) {
        Log.d(TAG, "===== constant_data_size_various_speed_hi_values =====")
        Log.d(TAG, "размер данных: ${dataSizeBytes.humanSizeBinary()}")
        Log.d(TAG, "скорость от ${speedFromBytesPerSec.humanSizeBinary()}/с до ${speedToBytesPerSec.humanSizeBinary()}/с")
        Log.d(TAG, "шаг скорости: ${speedStep.humanSizeBinary()}")

        prepareSourceAndTargetFiles(dataSizeBytes)
        var speed = speedFromBytesPerSec

        while(speed <= speedToBytesPerSec) {
            copyBetweenStreamsWithSpeed(
                inputStream = sourceFileStream,
                outputStream = targetFileStream,
                speedBytesPerSec = speed,
                finishCallback = { _,_, realSpeedBytesPerSec ->
                    val speedPercent = percent(realSpeedBytesPerSec, speed.toLong())
                    Log.d(TAG, "${speedPercent.roundToFloatingDigits(2)}%: ${dataSizeBytes.humanSizeBinary()}, ${speed.humanSizeBinary()}/с")
                }
            )
            speed += speedStep
            deleteTargetFile()
        }
    }

    private fun deleteTargetFile() {
        targetFile.delete()
        Assert.assertFalse(targetFile.exists())
    }
}

val Int.megabytes: Int get() = this * 1024 * 1024
val Int.kilobytes: Int get() = this * 1024