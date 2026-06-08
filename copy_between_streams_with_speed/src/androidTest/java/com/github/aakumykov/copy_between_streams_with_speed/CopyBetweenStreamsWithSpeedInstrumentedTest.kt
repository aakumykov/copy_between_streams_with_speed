package com.github.aakumykov.copy_between_streams_with_speed

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.aakumykov.copy_between_streams_with_speed.utils.estimateTimeMs
import com.github.aakumykov.copy_between_streams_with_speed.utils.humanReadable
import com.github.aakumykov.copy_between_streams_with_speed.utils.humanReadableByteCount
import com.github.aakumykov.copy_between_streams_with_speed.utils.millisecondsToDHMSN
import com.github.aakumykov.copy_between_streams_with_speed.utils.percentOf
import com.github.aakumykov.copy_between_streams_with_speed.utils.random
import com.github.aakumykov.copy_between_streams_with_speed.utils.repeatFromTo
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
class CopyBetweenStreamsWithSpeedInstrumentedTest {

    /**
    План теста:no
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

            copyBetweenStreamsWithSpeed2(
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


    /*@Test
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
    }*/


    @Test
    fun progress_callback_is_invoked_at_least_one_time_on_non_zero_file() {
        repeat(100) { i ->
            val nonZeroFileSize = i+1
            val isInvoked = AtomicBoolean(false)
            prepareSourceAndTargetFiles(testData(nonZeroFileSize))
            copyBetweenStreamsWithSpeed2(
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
        copyBetweenStreamsWithSpeed2(
            inputStream = sourceFileStream,
            outputStream = targetFileStream,
            progressCallback = { _,_ ->
                isInvoked.set(true)
            }
        )
        Assert.assertFalse(isInvoked.get())
    }


    /*@Test
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
            speed = speedByteSec,
            printDebug = false
        )

        Assert.assertEquals(
            (dataSizeByte / speedByteSec) * discretizationHz,
            counter
        )
    }*/


    @Test
    fun exception_thrown_on_zero_speed_argument() {
        Assert.assertThrows(IllegalArgumentException::class.java) {
            prepareSourceAndTargetFiles(testData(0))
            copyBetweenStreamsWithSpeed2(
                inputStream = sourceFileStream,
                outputStream = targetFileStream,
                speed = 0
            )
        }
    }


    /*@Test
    fun exception_thrown_if_discretization_greater_than_speed() {
        Assert.assertThrows(IllegalArgumentException::class.java) {
            prepareSourceAndTargetFiles(testData(0))
            copyBetweenStreamsWithSpeed(
                inputStream = sourceFileStream,
                outputStream = targetFileStream,
                speed = 10,
                discretizationHz = 20
            )
        }
    }*/


    /*@Test
    fun simple_10kb_copy_test() {

        val size = 10_000
        val speedBytesPerSec = 50_000
        val estimatedTimeMs = estimateTimeMs(size, speedBytesPerSec)

        val realTimeMs = doCopy(
            size,
            speedBytesPerSec,
        )

        val percent = percent(realTimeMs, estimatedTimeMs)

        println("RESULT: estTime: $estimatedTimeMs, realTime: $realTimeMs (${percent}%)")
    }*/


    /*@Test
    fun simple_100b_copy_test() {

        val size = 100
        val speedBytesPerSec = 200
        val discretizationHz = 10

        val estimatedTimeMs = estimateTimeMs(size, speedBytesPerSec)

        val realTimeMs = doCopy(
            size,
            speedBytesPerSec,
            discretizationHz,
        )

        val percent = percent(realTimeMs, estimatedTimeMs)

        println("RESULT: estTime: $estimatedTimeMs, realTime: $realTimeMs (${percent}%)")
    }*/

    @Test
    fun sprintf_test() {
        repeat(5) {
            System.currentTimeMillis().milliseconds.toComponents { days, hours, minutes, seconds, nanoseconds ->
                println(
                    String.format(
                        Locale.getDefault(),
                        "%02d дней, %02d часов, %02d минут, %02d секунд, %02d миллисекунд",
                        days,
                        hours,
                        minutes,
                        seconds,
                        (nanoseconds.toDouble() / 1_000_000).roundToLong()
                    )
                )
            }
        }
    }


    @Test
    fun data_1mbit_speed_900kbit(){
        doCopy(
            dataSizeBytes = 1000_000,
            speedBytesPerSec = 900_000,
            logPrefix = "data_1mbit_speed_900kbit",
            logLevel = 2
        )
    }


    @Test
    fun data_1mbit_speed_100kbit(){
        doCopy(
            dataSizeBytes = 1000_000,
            speedBytesPerSec = 100_000,
            logPrefix = "data_1mbit_speed_100kbit",
            logLevel = 1
        )
    }

    @Test
    fun data_1mbit_speed_1mbit(){
        doCopy(
            dataSizeBytes = 1000_000,
            speedBytesPerSec = 1000_000,
            logPrefix = "data_1mbit_speed_1mbit",
            logLevel = 1
        )
    }

    @Test
    fun data_10mbit_speed_10mbit(){
        doCopy(
            dataSizeBytes = 10_000_000,
            speedBytesPerSec = 10_000_000,
            logPrefix = "data_10mbit_speed_10mbit",
            logLevel = 1
        )
    }

    @Test
    fun data_size_with_speed_test(){

        val dataSize = 12_000_000
        val speedBytesPerSec = 1_000_000
        val logTag = "data_size_with_speed_test"

        val estimatedTimeMs = estimateTimeMs(dataSize,speedBytesPerSec)

        println("$logTag данные: ${humanReadableByteCount(dataSize)}, скорость: ${humanReadableByteCount(speedBytesPerSec)}/c, оценочное время: ${estimatedTimeMs.humanReadable}")

        doCopy(
            dataSizeBytes = dataSize,
            speedBytesPerSec = speedBytesPerSec,
            logPrefix = logTag,
            logLevel = 1,
            finishCallback = { transferredBytes, timeElapsedMs, realSpeedBytesPerSec ->
                println("$logTag [ФИНИШ] подано   данных: $dataSize (${dataSize.humanReadable})")
                println("$logTag [ФИНИШ] передано данных: $transferredBytes (${transferredBytes.humanReadable})")

                println("$logTag [ФИНИШ] расчётное время: $estimatedTimeMs мс (${millisecondsToDHMSN(estimatedTimeMs)})")
                println("$logTag [ФИНИШ] реальное  время: $timeElapsedMs мс (${millisecondsToDHMSN(timeElapsedMs)})")

                println("$logTag [ФИНИШ] заданная скорость: $speedBytesPerSec байт/с (${humanReadableByteCount(speedBytesPerSec, floatingDigits = 3)}/с)")
                println("$logTag [ФИНИШ] реальная скорость: $realSpeedBytesPerSec байт/с [${humanReadableByteCount(realSpeedBytesPerSec, floatingDigits = 3)}/с] -= ${percentOf(realSpeedBytesPerSec, speedBytesPerSec.toLong())}% =-")
            }
        )
    }

    @Test
    fun simple_stream_to_stream_copy() {
        val dataSizeMb = 10 * 1024 * 1024
        repeat(5) { i ->
            println("simple_stream_to_stream_copy: повторение-${i+1}")
            prepareSourceAndTargetFiles(testData(dataSizeMb))
            sourceFileStream.use { sourceFileStream ->
                targetFileStream.use { targetFileStream ->
                    sourceFileStream.copyTo(targetFileStream)
                }
            }
        }
    }

    @Test
    fun run_test_1mb_several_times() {
        repeat(4) { i ->
            println("------------ запуск $i ------------")
            test_1mb_with_diff_speeds()
        }
    }

    @Test
    fun test_1mb_with_diff_speeds() {
        val size = 1_000_000
        val logPrefix = "test_1mb_with_diff_speeds"

        listOf(10, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000).forEach { speedBase ->
            println("------ база скорости $speedBase ----")
            for (i in 1..10) {
                val speed = i * speedBase * 1000
                doCopy(
                    dataSizeBytes = size,
                    speedBytesPerSec = speed,
                    logPrefix = logPrefix
                )
            }
        }
    }


    @Test
    fun run_test_2mb_several_times() {
        repeat(4) { i ->
            println("------------ запуск $i ------------")
            test_2mb_with_diff_speeds()
        }
    }

    @Test
    fun test_2mb_with_diff_speeds() {
        val size = 1_000_000
        val logPrefix = "test_2mb_with_diff_speeds"

        listOf(10, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000).forEach { speedBase ->
            println("------ база скорости $speedBase ----")
            for (i in 1..10) {
                val speed = i * speedBase * 1000
                doCopy(
                    dataSizeBytes = size,
                    speedBytesPerSec = speed,
                    logPrefix = logPrefix
                )
            }
        }
    }


    @Test
    fun run_test_10mb_several_times() {
        repeat(4) { i ->
            println("------------ запуск $i ------------")
            test_10mb_with_diff_speeds()
        }
    }

    @Test
    fun test_10mb_with_diff_speeds() {
        val size = 1_000_000
        val logPrefix = "test_10mb_with_diff_speeds"

        listOf(10, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000).forEach { speedBase ->
            println("------ база скорости $speedBase ----")
            for (i in 1..10) {
                val speed = i * speedBase * 1000
                doCopy(
                    dataSizeBytes = size,
                    speedBytesPerSec = speed,
                    logPrefix = logPrefix
                )
            }
        }
    }


    fun doCopy(
        dataSizeBytes: Int,
        speedBytesPerSec: Int,
        logPrefix: String,
        logLevel: Int = 1,
        progressCallback: ((transferredBytes:Long, speedBytesPerSec:Long) -> Unit)? = null,
        finishCallback: ((transferredBytes:Long, timeElapsedMs:Long, speedBytesPerSec:Long) -> Unit)? = null,
    ) {
        prepareSourceAndTargetFiles(testData(dataSizeBytes))

        sourceFileStream.use { sS ->
            targetFileStream.use { tS ->
                copyBetweenStreamsWithSpeed2(
                    inputStream = sS,
                    outputStream = tS,
                    speed = speedBytesPerSec,
                    logLevel = logLevel,
                    logPrefix = logPrefix,
                    preKnownInputDataSizeBytes = dataSizeBytes,
                    progressCallback = progressCallback,
                    finishCallback = finishCallback,
                )
            }
        }
    }


    @Test
    fun test_megabytes() {
        val BYTES_IN_MEGABITE = 1_000_000
        val BYTES_IN_KILOBYTE = 1_000

        val logPrefix = "test_megabytes"

        repeatFromTo(1,10) { sizeMultiplier ->
            val sizeMb = sizeMultiplier * BYTES_IN_MEGABITE

            repeatFromTo(1,6) { speedMultiplier ->
                val speedKb = speedMultiplier * 1000

                println("${logPrefix}: ----- data ${humanReadableByteCount(sizeMb)}, speed ${humanReadableByteCount(speedKb)}/c -----")

                prepareSourceAndTargetFiles(testData(sizeMb))

                sourceFileStream.use { sS ->
                    targetFileStream.use { tS ->
                        copyBetweenStreamsWithSpeed2(
                            inputStream = sS,
                            outputStream = tS,
                            speed = speedKb * BYTES_IN_KILOBYTE,
                            logLevel = 1,
                            logPrefix = logPrefix,
                            preKnownInputDataSizeBytes = sizeMb * BYTES_IN_MEGABITE
                        )
                    }
                }
            }
        }
    }


    @Test
    fun CBSWS2() {
        val dataSize = 10_000_000
        val speed = 5_000_000
        val repeats = 1
        val logLevel = 1

        repeat(repeats) {
            prepareSourceAndTargetFiles(testData(dataSize))
            sourceFileStream.use { sS ->
                targetFileStream.use { tS ->
                    copyBetweenStreamsWithSpeed2(
                        inputStream = sS,
                        outputStream = tS,
                        speed = speed,
                        logLevel = logLevel,
                        preKnownInputDataSizeBytes = dataSize
                    )
                }
            }
        }
    }


    @Test
    fun test_multiple_size_and_speed() {

        val list = 1..10
        val sizeMultiplier = 1..10
        val speed = 10_000

        list.forEach { i ->
            sizeMultiplier.forEach { sm ->
                val dataSize = i * sm

                prepareSourceAndTargetFiles(testData(dataSize))

                sourceFileStream.use { sS ->
                    targetFileStream.use { tS ->
                        copyBetweenStreamsWithSpeed2(
                            inputStream = sS,
                            outputStream = tS,
                            speed = speed,
                            logLevel = 2,
                            preKnownInputDataSizeBytes = dataSize
                        )
                    }
                }
            }
        }
    }


    @Test
    fun no_speed_limit() {
        val size = 1_000_000

        prepareSourceAndTargetFiles(testData(size))
        sourceFileStream.use { sS ->
            targetFileStream.use { tS ->
                copyBetweenStreamsWithSpeed2(
                    inputStream = sS,
                    outputStream = tS,
                    logLevel = 1,
                    preKnownInputDataSizeBytes = size
                )
            }
        }
    }

    /*@Test
    fun size_1000_speed_9000() {
        copyDataSimple(size = 1000, speed = 9000, printDebug = true)
    }*/


    /*@Test
    fun data_sizes_and_speed_test() {
        listOf(10, 100, 1000, 10_000, 100_000).forEach { dataSizeMultiplier ->
            data_sizes_test(dataSizeMultiplier)
        }
    }

    fun data_sizes_test(dataSizeMultiplier: Int, printDebug: Boolean = false) {
        println("===================== data_sizes_test($dataSizeMultiplier) =====================")
        repeatFromTo(1,10) { sizeBase ->
            val size = sizeBase * dataSizeMultiplier
            repeatFromTo(1,11) { speedMultiplier ->
                val speed = size * speedMultiplier
//                println("------------------- speed: $speed ------------------")
                copyDataSimple(size = size, speed = speed, printDebug = printDebug)
            }
        }
    }*/

    /*fun copyDataSimple(size: Int, speed: Int, discretizationHz: Int = 10, printDebug: Boolean = false) {

        val estimatedTimeMs = estimateTimeMs(size, speed)

        val realTimeMs = doCopy(
            size,
            speed,
            discretizationHz,
            printDebug
        )

        val percent = percent(realTimeMs, estimatedTimeMs)
        val percentInt = percent.roundToInt()

        val anomalySign = when {
            (percentInt <= 90) -> "<----- "
            (percentInt > 110) -> "-----> "
            else -> ""
        }

        println("RESULT: size: $size, speed: $speed, est: $estimatedTimeMs, real: $realTimeMs ${anomalySign}(${percent.roundToFloatingDigits(2)}%)")
    }*/

    /*@Test
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
    }*/

    /*@Test
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
    }*/

    /**
     * @return Pair<DataSizeBytes,WorkTimePercentsOfEstimated>
     */
    /*private fun test_data_sizes(
        fromDataSize: Int,
        toDataSize: Int,
        dataStep: Int,
        baseSpeedBytesPerSec: Int,
        speedMultiplier: Int,
        discretizationHz: Int = 10,
        printDebug: Boolean = false,
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
                discretizationHz = discretizationHz,
                printDebug = printDebug
            )

            val estimatedTimeMs = (dataSizeBytes.toFloat() / speedBytesPerSec) * 1000

            val differencePercents = ((realTimeMs.toFloat() / estimatedTimeMs) * 100)

            percentList.add(differencePercents)
        }

        return Pair(size, percentList.sortedDescending())
    }*/


    /**
     * @return Затраченное на копирование время, мс.
     */
    /*private fun doCopy(
        dataSizeBytes: Int,
        speedBytesPerSec: Int,
        discretizationHz: Int = 10,
        printDebug: Boolean = false
    ): Long {

        println("doCopy(dataSizeBytes:$dataSizeBytes, speedBytesPerSec:$speedBytesPerSec, discretizationHz:$discretizationHz)")

        prepareSourceAndTargetFiles(testData(dataSizeBytes))

        val startTime = System.currentTimeMillis()

        sourceFileStream.use { inputStream ->
            targetFileStream.use { outputStream ->
                copyBetweenStreamsWithSpeed(
                    inputStream = inputStream,
                    outputStream = outputStream,
                    speed = speedBytesPerSec,
                    discretizationHz = discretizationHz,
                    printDebug = printDebug
                )
            }
        }

        return System.currentTimeMillis() - startTime
    }*/


    /*@Test
    fun works_with_different_speed_values() {
        repeat(100) { i ->
            val speed = i+2
            val discretization = i+1
            prepareSourceAndTargetFiles(testData(100))
            copyBetweenStreamsWithSpeed(
                inputStream = sourceFileStream,
                outputStream = targetFileStream,
                speed = speed,
                discretizationHz = discretization,
                printDebug = true
            )
            Assert.assertEquals(sourceFileContents, targetFileContents)
        }
    }*/
}