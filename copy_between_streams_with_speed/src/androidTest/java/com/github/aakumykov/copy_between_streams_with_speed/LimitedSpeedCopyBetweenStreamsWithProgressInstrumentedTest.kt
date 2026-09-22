package com.github.aakumykov.copy_between_streams_with_speed

import com.github.aakumykov.copy_between_streams_with_speed.stream2stream_copier.LimitedStreamCopier
import com.github.aakumykov.copy_between_streams_with_speed.utils.humanDecimalPlaces
import com.github.aakumykov.copy_between_streams_with_speed.utils.humanSizeBinary
import com.github.aakumykov.copy_between_streams_with_speed.utils.random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test
import java.io.FileNotFoundException
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

class LimitedSpeedCopyBetweenStreamsWithProgressInstrumentedTest : TestBase() {

    companion object {
        const val DEFAULT_PROGRESS_LIST_SIZE_DIFF_PERCENTS = 10
    }

    /**
    План теста:
    1) проверить граничные условия (в данном случае исключения):
        - нулевая скорость [throws_exception_on_zero_speed]
        - отрицательная скорость [throws_exception_on_negative_speed]
        - нулевая частота прогресса [throws_exception_on_zero_rate]
        - отрицательная частота прогресса [throws_exception_on_negative_rate]
        - частота прогресса больше скорости [throws_exception_on_rate_greater_than_speed]
        - оцуцтвует исходный файл [thrown_FNFE_on_no_source_file]
    2) простое копирование файла [file_simply_copied], при котором он:
        - копируется;
        - совпадает по размеру;
        - совпадает по содержимому;
        - исходный файл сохраняется.
    3) прогресс копирования:
        - при копировании файла нулевого размера данные о прогрессе пусты [progress_is_empty_on_zero_size_file]
        - при копировании файла ненулевого размера в трёх вариантах
          относительно размера буфера копирования ([DEFAULT_BUFFER_SIZE]):
            а) размер файла меньше буфера [progress_is_correct_on_file_size_lower_than_buffer_size];
            б) размер файла равен размеру буфера [progress_is_correct_on_file_size_equals_buffer_size];
            в) размер файла кратен размеру буфера [progress_is_correct_on_file_size_proportional_buffer_size];
            г) размер файла больше размера буфера [progress_is_correct_on_file_size_greater_than_buffer_size].
            Данные о прогрессе:
                -- приходят;
                -- первое значение меньше последнего;
                -- расположены в порядке возрастания.
    4) вариации аргументов:
        - разная скорость при фиксированном числе шагов и размере
            [diff_speed_with_constant_size_and_rate__units_to_thousands];
            [diff_speed_with_constant_size_and_rate__tens_thousands];
            [diff_speed_with_constant_size_and_rate__hundreds_thousands];
            [diff_speed_with_constant_size_and_rate__millions];
        - разное число шагов при фиксированных размере и скорости
            [diff_rate_with_constant_size_and_speed__units_to_thousands];
            [diff_rate_with_constant_size_and_speed__tens_thousands];
            [diff_rate_with_constant_size_and_speed__hundreds_thousands];
            [diff_rate_with_constant_size_and_speed__millions];
        - разный размер при фиксированных скорости и числе шагов
            [diff_size_with_constant_rate_and_speed__units_to_thousands].
            [diff_size_with_constant_rate_and_speed__tens_thousands].
            [diff_size_with_constant_rate_and_speed__hundreds_thousands].
            [diff_size_with_constant_rate_and_speed__millions].
     */


    //
    // Испытание простого копирования.
    //
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun file_simply_copied() = runBlocking {
        repeat_on_different_sizes {
            runTest {
                val fileSize = 10272
                println("размер файла: $fileSize")

                prepareSourceAndTargetFiles(fileSize)

                limitedStreamCopier(1000, 1)
                    .copyFromStreamToStream(newSourceFileStream, newTargetFileStream,)

                test_files(fileSize)
            }
        }
    }


    //
    // Испытание неправильных аргументов
    //
    @Test
    fun thrown_FNFE_on_no_source_file() {
        Assert.assertThrows(FileNotFoundException::class.java) {
            clearSourceFile()
            prepareTargetFile()
            copyWithoutCheck()
        }
    }

    //
    // Не получится так просто проверить с оцуцтвием целевого файла,
    // так как он создаётся по требованию,
    // а сделать файловую систему только для чтения так просто нельзя.
    //


    @Test
    fun throws_exception_on_zero_speed() {
        checkOnExceptionWithSpeed(0)
    }

    @Test
    fun throws_exception_on_negative_speed() {
        checkOnExceptionWithSpeed(-1)
    }


    private fun checkOnExceptionWithSpeed(speed: Int) {
        Assert.assertThrows(IllegalArgumentException::class.java) {
            prepareSourceAndTargetFiles()
            runBlocking {
                limitedStreamCopier(speed = speed, rate = 1).copyFromStreamToStream(
                    inputStream = newSourceFileStream,
                    outputStream = newTargetFileStream,
                )
            }
        }
    }


    @Test
    fun throws_exception_on_zero_rate() {
        checkOnExceptionWithRate(0)
    }


    @Test
    fun throws_exception_on_negative_rate() {
        checkOnExceptionWithRate(-1)
    }


    private fun checkOnExceptionWithRate(rate: Int) {
        Assert.assertThrows(IllegalArgumentException::class.java) {
            prepareSourceAndTargetFiles()
            runBlocking {
                limitedStreamCopier(speed = 1, rate = rate).copyFromStreamToStream(
                    inputStream = newSourceFileStream,
                    outputStream = newTargetFileStream,
                )
            }
        }
    }


    @Test
    fun throws_exception_on_rate_greater_than_speed() {
        Assert.assertThrows(IllegalArgumentException::class.java) {
            prepareSourceAndTargetFiles()
            limitedStreamCopier(1, 2).copyFromStreamToStream(
                inputStream = newSourceFileStream,
                outputStream = newTargetFileStream,
            )
        }
    }

    //
    // Испытание прогресса
    //
    @Test
    fun progress_is_empty_on_zero_size_file() = runBlocking {
        val size = 0
        val speed = 1000
        val progressRate = 10
        prepareSourceAndTargetFiles(size)
        copy_data_with_on_complete(speed,progressRate) {
            test_progress_list(it, size, speed, progressRate)
            test_files(0)
        }
    }


    @Test
    fun progress_is_correct_on_sizes_lowe_than_10() = runBlocking {
        repeat(9) { i ->
            val size = i + 10
            val speed = size * 10
            val rate = 10
            prepareSourceAndTargetFiles(size)
            copy_data_with_on_complete(speed,rate) {
                test_progress_list(it, size, speed, rate)
                test_files(size)
            }
        }
    }


    @Test
    fun progress_is_correct_on_sizes_10_to_100() = runBlocking {
        repeat(9) { tens ->
            val size = (tens+1) * 10 + random.nextInt(0,10)
            val speed = 1000
            val progressRate = 10
            prepareSourceAndTargetFiles(size)
            copy_data_with_on_complete(speed,progressRate) {
                test_progress_list(it, size, speed, progressRate)
                test_files(size)
            }
        }
    }


    @Test
    fun progress_is_correct_on_sizes_100_to_1000() = runBlocking {
        repeat(9) { tens ->
            val size = (tens+1) * 100 + random.nextInt(0,100)
            val speed = 1000
            val progressRate = 10
            prepareSourceAndTargetFiles(size)
            copy_data_with_on_complete(speed,progressRate) {
                test_progress_list(it, size, speed, progressRate)
                test_files(size)
            }
        }
    }


    @Test
    fun progress_is_correct_on_file_size_lower_than_buffer_size() = runBlocking {
        repeat(10) { i ->
            val size = (i+1) * 10 + random.nextInt(10)
            val speed = 1000
            val progressRate = 10
            prepareSourceAndTargetFiles(size)
            copy_data_with_on_complete(speed,progressRate) {
                test_progress_list(it, size, speed, progressRate)
                test_files(size)
            }
        }
    }


    @Test
    fun progress_is_correct_on_file_size_equals_buffer_size() = runBlocking {
        val size = DEFAULT_BUFFER_SIZE
        val speed = size * 2
        val progressRate = 10
        prepareSourceAndTargetFiles(size)
        copy_data_with_on_complete(speed,progressRate) {
            test_progress_list(it, size, speed, progressRate)
            test_files(size)
        }
    }


    @Test
    fun progress_is_correct_on_file_size_proportional_buffer_size() = runBlocking {
        repeat(10) { i ->
            val size = (i+1) * DEFAULT_BUFFER_SIZE
            val speed = 1000_000
            val progressRate = 10
            prepareSourceAndTargetFiles(size)
            copy_data_with_on_complete(speed,progressRate) {
                test_progress_list(it, size, speed, progressRate)
                test_files(size)
            }
        }
    }


    @Test
    fun progress_is_correct_on_file_size_greater_than_buffer_size() = runBlocking {
        repeat(10) { i ->
            val multiplier = i+2
            val size = multiplier * DEFAULT_BUFFER_SIZE + random.nextInt(1,DEFAULT_BUFFER_SIZE)
            val speed = multiplier * 1000
            val progressRate = 10
            prepareSourceAndTargetFiles(size)
            copy_data_with_on_complete(speed,progressRate) {
                test_progress_list(it, size, speed, progressRate)
                test_files(size)
            }
        }
    }


    //
    // разный размер при фиксированных скорости и числе шагов
    //
    @Test
    fun diff_size_with_constant_rate_and_speed__1_9() = runTest {
        // единицы байт: 1..9
        do_with_variable_data_size(
            speed = 1,
            progressRate = 1,
            dataSizeRange = 1..9,
            dataSizeInterval = 1,
            randomizeDataSize = false
        )
    }

    @Test
    fun diff_size_with_constant_rate_and_speed__10_90() = runTest {
        // десятки байт: 10..90
        do_with_variable_data_size(
            speed = 100,
            progressRate = 10,
            dataSizeRange = 10..90,
            dataSizeInterval = 10
        )

    }

    @Test
    fun diff_size_with_constant_rate_and_speed__100_900() = runTest {
        // сотни байт: 100..900
        do_with_variable_data_size(
            speed = 1000,
            progressRate = 10,
            dataSizeRange = 100..900,
            dataSizeInterval = 100
        )
    }

    @Test
    fun diff_size_with_constant_rate_and_speed__1000_9000() = runTest {
        // тысячи байт: 1000..9000
        do_with_variable_data_size(
            speed =  10_000,
            progressRate = 10,
            dataSizeRange = 1000..9000,
            dataSizeInterval = 1000
        )
    }


    @Test
    fun diff_size_with_constant_rate_and_speed__tens_thousands() = runTest {
        // десятки тысяч байт: 10_000..90_000
        do_with_variable_data_size(
            speed =  100_000,
            progressRate = 10,
            dataSizeRange = 10_000..90_000,
            dataSizeInterval = 10_000
        )
    }


    @Test
    fun diff_size_with_constant_rate_and_speed__hundreds_thousands() = runTest {
        // сотни тысяч байт: 100_000..900_000
        do_with_variable_data_size(
            speed =  1000_000,
            progressRate = 10,
            dataSizeRange = 100_000..900_000,
            dataSizeInterval = 100_000
        )
    }


    @Test
    fun diff_size_with_constant_rate_and_speed__millions() = runTest {
        // мильёны байт: 1000_000..9000_000
        do_with_variable_data_size(
            speed =  10_000_000,
            progressRate = 10,
            dataSizeRange = 1000_000..9000_000,
            dataSizeInterval = 1000_000
        )
    }


    //
    // разная скорость при фиксированном числе шагов и размере
    //
    @Test
    fun diff_speed_with_constant_size_and_rate__units_to_thousands() = runTest {
        // единицы байт: 1..9
        do_with_variable_speed(
            dataSizeFromRange = 1..9,
            progressRate = 1,
            speedRange = 1..100,
            speedInterval = 10,
        )

        // десятки байт: 10..90
        do_with_variable_speed(
            dataSizeFromRange = 10..90,
            progressRate = 10,
            speedRange = 10..1000,
            speedInterval = 10,
        )

        // сотни байт: 100..900
        do_with_variable_speed(
            dataSizeFromRange = 100..900,
            progressRate = 10,
            speedRange = 10..1000,
            speedInterval = 10,
        )

        // тысячи байт: 1000..9000
        do_with_variable_speed(
            dataSizeFromRange = 1000..9000,
            progressRate = 10,
            speedRange = 100..1000,
            speedInterval = 10,
        )
    }


    @Test
    fun diff_speed_with_constant_size_and_rate__tens_thousands() = runTest {
        // десятки тысяч байт: 10_000..90_000
        do_with_variable_speed(
            dataSizeFromRange = 10_000..90_000,
            progressRate = 10,
            speedRange = 100_000..1000_000,
            speedInterval = 1000,
        )
    }


    @Test
    fun diff_speed_with_constant_size_and_rate__hundreds_thousands() = runTest {
        // сотни тысяч байт: 100_000..900_000
        do_with_variable_speed(
            dataSizeFromRange = 100_000..900_000,
            progressRate = 10,
            speedRange = 1000_000..9000_000,
            speedInterval = 100_000,
        )
    }


    @Test
    fun diff_speed_with_constant_size_and_rate__millions() = runTest {
        // мильёны байт: 100_000..900_000
        do_with_variable_speed(
            dataSizeFromRange = 1000_000..9000_000,
            progressRate = 10,
            speedRange = 1000_000..9000_000,
            speedInterval = 1000_000,
        )
    }


    //
    // разное число шагов при фиксированных размере и скорости
    //
    @Test
    fun diff_rate_with_constant_size_and_speed__units_to_thousands() {
        // "единицы байт: 1..9"
        do_with_variable_rate(
            dataSizeFromRange = 1..9,
            speed = 10,
            rateRange = 1..10,
            rateInterval = 1,
            randomizeStep = false
        )

        // "десятки байт: 10+..90+"
        do_with_variable_rate(
            dataSizeFromRange = 10..90,
            speed = 100,
            rateRange = 1..100,
            rateInterval = 10,
            randomizeStep = true
        )

        // "сотни байт: 100+..900+"
        do_with_variable_rate(
            dataSizeFromRange = 100..900,
            speed = 1000,
            rateRange = 1..100,
            rateInterval = 10,
            randomizeStep = true
        )

        // "тысячи байт: 1000+..9000+"
        do_with_variable_rate(
            dataSizeFromRange = 1000..9000,
            speed = 10_000,
            rateRange = 1..100,
            rateInterval = 10,
            randomizeStep = true
        )
    }


    @Test
    fun diff_rate_with_constant_size_and_speed__tens_thousands() {
        // "десятки тысяч байт: 10_000+..90_000+"
        do_with_variable_rate(
            dataSizeFromRange = 10_000..90_000,
            speed = 100_000,
            rateRange = 1..100,
            rateInterval = 10,
            randomizeStep = true
        )
    }


    @Test
    fun diff_rate_with_constant_size_and_speed__hundreds_thousands() {
        // "сотни тысяч байт: 100_000+..900_000+"
        do_with_variable_rate(
            dataSizeFromRange = 100_000..900_000,
            speed = 1000_000,
            rateRange = 1..100,
            rateInterval = 10,
            randomizeStep = true
        )
    }


    @Test
    fun diff_rate_with_constant_size_and_speed__millions() {
        // "мильёны байт: 1000_000+..9000_000+"
        do_with_variable_rate(
            dataSizeFromRange = 1000_000..9000_000,
            speed = 10_000_000,
            rateRange = 1..100,
            rateInterval = 10,
            randomizeStep = true
        )
    }



    @OptIn(ExperimentalCoroutinesApi::class)
    private fun copy_data_with_on_complete(
        speed: Int,
        rate: Int,
        onComplete: (progressList:List<Long>) -> Unit
    ) = runTest {

        val progressList = mutableListOf<Long>()

        limitedStreamCopier(speed,rate).copyFromStreamToStream(
            inputStream = newSourceFileStream,
            outputStream = newTargetFileStream,
            progressCallback = { transferredBytes ->
                progressList.add(transferredBytes)
            }
        )

        onComplete.invoke(progressList)
    }


    private fun test_progress_list(
        progressList: List<Long>,
        dataSizeBytes: Int, // TODO: Long
        speedBytesPerSecond: Int,
        progressRate: Int,
        expectedProgressListDiffPercents: Int = DEFAULT_PROGRESS_LIST_SIZE_DIFF_PERCENTS
    ) {
        test_progress_list_size(
            progressList,
            dataSizeBytes,
            speedBytesPerSecond,
            progressRate,
            expectedProgressListDiffPercents
        )
        test_progress_list_incrementality(progressList)
    }


    private fun test_progress_list_size(
        progressList: List<Long>,
        dataSizeBytes: Int,
        speedBytesPerSecond: Int,
        progressRate: Int,
        expectedProgressListDiffPercents: Int
    ) {
        val expectedCopyingTime = 1f * dataSizeBytes / speedBytesPerSecond
        val expectedProgressShots = ceil(expectedCopyingTime * progressRate).roundToInt()

        val argumentsLog =
            "\nданные: $dataSizeBytes байт," +
            "\nскорость:$speedBytesPerSecond," +
            "\nшагов:$progressRate"

        if (expectedProgressShots > 1) {
            val progressStepsDifferenceFloat = 1f * abs(progressList.size - expectedProgressShots) / expectedProgressShots
            val progressStepsDifferenceInt = (progressStepsDifferenceFloat * 100).roundToInt()

            val message = "${argumentsLog}\n" +
                    "Размер списка прогресса (${progressList.size}) " +
                    "отличается от ожидаемого (${expectedProgressShots}) " +
                    "более, чем на ${expectedProgressListDiffPercents}%: " +
                    "на ${progressStepsDifferenceInt}%."

            println("$progressStepsDifferenceInt <= $expectedProgressListDiffPercents")
//            Assert.assertTrue(message, progressStepsDifferenceInt <= expectedProgressListDiffPercents)
        }
        else {
            println("$expectedProgressShots == ${progressList.size}")
            /*Assert.assertEquals(
                "${argumentsLog}\nРазмер списка прогресса для данных в $dataSizeBytes байт должен быть равен $expectedProgressShots байт.",
                expectedProgressShots,
                progressList.size
            )*/
        }
    }


    private fun test_progress_list_incrementality(progressList: List<Long>) {
        // Проверка, что значения увеличиваются.
        if (progressList.size >= 2) {
            repeat(progressList.size-1) { i ->
                val value = progressList[i]
                val nextValue = progressList[i+1]
                Assert.assertTrue(
                    "Каждое предыдущее значение ($value) меньше следующего ($nextValue);\nвесь список:\n${progressList.joinToString(",\n")}",
                    value < nextValue
                )
            }
        }
    }


    private fun do_with_variable_rate(
        dataSizeFromRange: IntRange,
        speed: Int,
        rateRange: IntRange,
        rateInterval: Int,
        randomizeStep: Boolean = true,
    ) {
        println("----- Копирование с вариацией шагов -----")

        val dataSize = dataSizeFromRange.random()

        var rate = rateRange.first
        while (rate <= rateRange.last) {

            println("Копирование ${dataSize.humanSizeBinary()} на скорости ${speed.humanSizeBinary()}/с с $rate шагами в секунду.")
            copy_and_test_with_params(
                dataSizeBytes = dataSize,
                speed = speed,
                progressRate = rate
            )

            rate += rateInterval
            if (randomizeStep) rate += random.nextInt(rateInterval)
        }
    }


    private fun do_with_variable_data_size(
        progressRate: Int,
        speed: Int,
        dataSizeRange: IntRange,
        dataSizeInterval: Int,
        randomizeDataSize: Boolean = true,
    ) {
        println("DEBUG_PROGRESS, ----- Копирование с вариацией размера данных -----")

        var dataSize = dataSizeRange.first
        while (dataSize <= dataSizeRange.last) {

            println("DEBUG_PROGRESS, Копирование ${dataSize.humanDecimalPlaces} на скорости ${speed.humanDecimalPlaces}/с с $progressRate шагами в секунду.")
            copy_and_test_with_params(
                dataSizeBytes = dataSize,
                speed = speed,
                progressRate = progressRate
            )

            dataSize += dataSizeInterval
            if (randomizeDataSize) dataSize += random.nextInt(dataSizeInterval)
        }
    }


    private fun do_with_variable_speed(
        dataSizeFromRange: IntRange,
        progressRate: Int,
        speedRange: IntRange,
        speedInterval: Int,
        randomizeSpeed: Boolean = true,
    ) {
        println("----- Копирование с вариацией скорости -----")

        val dataSize = dataSizeFromRange.random()

        var speed = speedRange.first
        while (speed <= speedRange.last) {

            println("Копирование ${dataSize.humanSizeBinary()} на скорости ${speed.humanSizeBinary()}/с с $progressRate шагами в секунду.")
            copy_and_test_with_params(
                dataSizeBytes = dataSize,
                speed = speed,
                progressRate = progressRate
            )

            speed += speedInterval
            if (randomizeSpeed) speed += random.nextInt(speedInterval)
        }
    }


    private fun copyWithoutCheck() = runBlocking {
        limitedStreamCopier(100,10).copyFromStreamToStream(
            inputStream = newSourceFileStream,
            outputStream = newTargetFileStream,
        )
    }


    private fun test_files(dataSize: Int) {
        Assert.assertEquals(dataSize.toLong(), sourceFile.length())
        Assert.assertEquals(dataSize.toLong(), targetFile.length())
        Assert.assertEquals(sourceFile.length(), targetFile.length())
        Assert.assertEquals(sourceFileContents, targetFileContents)
    }


    private suspend fun repeat_on_different_sizes(
        sizesList: List<Int> = listOf(1, 10, 100, 1000, 10_000, 100_000, 1000_000),
        block: suspend (fileSize: Int) -> Unit
    ) {
        sizesList.forEach { sizeRange ->
            repeat(5) { i ->
                val sizeAppendix = if (sizeRange > 1) random.nextInt(1,sizeRange) else 0
                val fileSize = (i + 1) * sizeRange + sizeAppendix
                block.invoke(fileSize)
            }
        }
    }

    private fun copy_and_test_with_params(
        dataSizeBytes: Int,
        speed: Int,
        progressRate: Int,
    ) {
        prepareSourceAndTargetFiles(dataSizeBytes)
        copy_data_with_on_complete(speed,progressRate) {
            test_progress_list(it, dataSizeBytes, speed, progressRate)
            test_files(dataSizeBytes)
        }
    }

    private fun limitedStreamCopier(speed: Int, rate: Int): LimitedStreamCopier {
        return LimitedStreamCopier(
            speedBytesPerSecond = speed,
            progressRatePerSecond = rate
        )
    }
}
