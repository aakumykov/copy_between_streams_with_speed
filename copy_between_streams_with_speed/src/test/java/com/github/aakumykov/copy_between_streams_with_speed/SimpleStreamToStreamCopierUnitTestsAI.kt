package com.github.aakumykov.copy_between_streams_with_speed

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random

class SimpleStreamToStreamCopierUnitTestsAI {

    private val simpleStreamToStreamCopier = SimpleStreamToStreamCopier()

    private fun copyWithRateLimitAndProgress(
        inputStream: InputStream,
        outputStream: OutputStream,
        speedBytesPerSecond: Int,
        progressRatePerSecond: Int = 1,
        progressCallback: ((totalBytesTransferred: Long, speedBytesPerSecond: Long) -> Unit)? = null,
        finishCallback: ((byteTransferred:Long, timeElapsedMs:Long, speedBytesPerSec:Long) -> Unit)? = null
    ) {
        simpleStreamToStreamCopier.copyFromStreamToStream(
            inputStream = inputStream,
            outputStream = outputStream,
            speedBytesPerSecond = speedBytesPerSecond,
            progressRatePerSecond = progressRatePerSecond,
            progressCallback = progressCallback,
            finishCallback = finishCallback
        )
    }

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    // =========================================================================
    // 1. Проверка некорректного speedBytesPerSecond
    // =========================================================================
    @Test
    fun `should throw exception when speedBytesPerSecond is not positive`() {
        val dummyIn = ByteArrayInputStream(ByteArray(10))
        val dummyOut = ByteArrayOutputStream()

        for (invalidSpeed in listOf(0, -1, -100)) {
            try {
                copyWithRateLimitAndProgress(dummyIn, dummyOut, invalidSpeed)
                fail("Ожидалось исключение для speedBytesPerSecond = $invalidSpeed")
            } catch (_: IllegalArgumentException) {
                // Ожидаемое поведение
            } catch (_: Exception) {
                // Если реализация кидает другой тип исключений (например, IllegalStateException)
                // можно заменить IllegalArgumentException на Exception::class в expected
            }
        }
    }

    // =========================================================================
    // 2. Проверка некорректного progressRatePerSecond
    // =========================================================================
    @Test
    fun `should throw exception when progressRatePerSecond is not positive`() {
        val dummyIn = ByteArrayInputStream(ByteArray(10))
        val dummyOut = ByteArrayOutputStream()

        for (invalidRate in listOf(0, -1, -5)) {
            try {
                copyWithRateLimitAndProgress(dummyIn, dummyOut, 10, invalidRate)
                fail("Ожидалось исключение для progressRatePerSecond = $invalidRate")
            } catch (_: IllegalArgumentException) {
                // Ожидаемое поведение
            } catch (_: Exception) {
                // Допускаем другие типы исключений
            }
        }
    }

    // =========================================================================
    // 3. Скопированный файл идентичен исходному
    // =========================================================================
    @Test
    fun `copied file should be identical to original`() {
        val originalBytes = Random.nextBytes(1024 * 1024) // 1 МБ
        val inFile = tempFolder.newFile("in_identical.bin").apply { writeBytes(originalBytes) }
        val outFile = tempFolder.newFile("out_identical.bin")

        inFile.inputStream().use { `in` ->
            outFile.outputStream().use { out ->
                // Скорость высокая, чтобы тест быстро прошел
                copyWithRateLimitAndProgress(`in`, out, 10 * 1024 * 1024)
            }
        }

        assertArrayEquals("Содержимое файлов должно совпадать", originalBytes, outFile.readBytes())
    }

    // =========================================================================
    // 4. Время копирования отличается от расчётного не более чем на 10%
    // =========================================================================
    @Test
    fun `copy time should be within 10 percent of expected time`() {
        val fileSize = 500_000
        val speed = 100_000 // 100 КБ/с
        val expectedTimeMs = (fileSize.toDouble() / speed * 1000).toLong() // 5000 мс

        val originalBytes = Random.nextBytes(fileSize)
        val inFile = tempFolder.newFile("in_time.bin").apply { writeBytes(originalBytes) }
        val outFile = tempFolder.newFile("out_time.bin")

        val start = System.currentTimeMillis()
        inFile.inputStream().use { `in` ->
            outFile.outputStream().use { out ->
                copyWithRateLimitAndProgress(`in`, out, speed)
            }
        }
        val elapsed = System.currentTimeMillis() - start

        val lowerBound = (expectedTimeMs * 0.9).toLong()
        val upperBound = (expectedTimeMs * 1.1).toLong()

        assertTrue(
            "Время копирования $elapsed мс вышло за пределы [${lowerBound}, ${upperBound}] мс",
            elapsed in lowerBound..upperBound
        )
    }

    // =========================================================================
    // 5. Коллбек прогресса возвращает неубывающие значения
    // =========================================================================
    @Test
    fun `progress callback values should be non-decreasing`() {
        val progressValues = CopyOnWriteArrayList<Long>()
        val fileSize = 200_000

        val inFile = tempFolder.newFile("in_progress.bin").apply { writeBytes(Random.nextBytes(fileSize)) }
        val outFile = tempFolder.newFile("out_progress.bin")

        inFile.inputStream().use { `in` ->
            outFile.outputStream().use { out ->
                copyWithRateLimitAndProgress(
                    inputStream = `in`,
                    outputStream = out,
                    speedBytesPerSecond = 100_000,
                    progressRatePerSecond = 4,
                    progressCallback = { b, _ -> progressValues.add(b) }
                )
            }
        }

        for (i in 1 until progressValues.size) {
            assertTrue(
                "Значение прогресса уменьшилось: ${progressValues[i-1]} -> ${progressValues[i]}",
                progressValues[i] >= progressValues[i-1]
            )
        }
    }

    // =========================================================================
    // 6. Финальное значение коллбека прогресса равно размеру файла
    // =========================================================================
    @Test
    fun `final progress callback value should equal file size`() {
        val progressValues = CopyOnWriteArrayList<Long>()
        val fileSize = 200_000

        val inFile = tempFolder.newFile("in_final_prog.bin").apply { writeBytes(Random.nextBytes(fileSize)) }
        val outFile = tempFolder.newFile("out_final_prog.bin")

        inFile.inputStream().use { `in` ->
            outFile.outputStream().use { out ->
                copyWithRateLimitAndProgress(
                    inputStream = `in`,
                    outputStream = out,
                    speedBytesPerSecond = 100_000,
                    progressRatePerSecond = 4,
                    progressCallback = { b, _ -> progressValues.add(b) }
                )
            }
        }

        assertTrue("Коллбек прогресса не был вызван", progressValues.isNotEmpty())
        assertEquals("Финальное значение прогресса должно равняться размеру файла",
            fileSize.toLong(), progressValues.last())
    }

    // =========================================================================
    // 7. Коллбек завершения вызывается и возвращает размер файла
    // =========================================================================
    @Test
    fun `finish callback should be called with file size`() {
        val finishValues = CopyOnWriteArrayList<Long>()
        val fileSize = 200_000

        val inFile = tempFolder.newFile("in_finish.bin").apply { writeBytes(Random.nextBytes(fileSize)) }
        val outFile = tempFolder.newFile("out_finish.bin")

        inFile.inputStream().use { `in` ->
            outFile.outputStream().use { out ->
                copyWithRateLimitAndProgress(
                    inputStream = `in`,
                    outputStream = out,
                    speedBytesPerSecond = 100_000,
                    finishCallback = { _, _, s -> finishValues.add(s) }
                )
            }
        }

        assertEquals("Коллбек завершения должен быть вызван ровно 1 раз", 1, finishValues.size)
        assertEquals("Значение в коллбеке завершения должно равняться размеру файла",
            fileSize.toLong(), finishValues.first())
    }

    // =========================================================================
    // 8. Интервалы между срабатываниями коллбека прогресса (допуск 10%)
    // =========================================================================
    @Test
    fun `intervals between progress callbacks should be within 10 percent of expected`() {
        val progressTimestamps = CopyOnWriteArrayList<Long>() // В наносекундах
        val progressRate = 4 // 4 раза в секунду -> интервал 250 мс
        val expectedIntervalMs = 1000.0 / progressRate

        val fileSize = 500_000
        val speed = 100_000 // Копирование займет 5 секунд, что даст ~20 срабатываний

        val inFile = tempFolder.newFile("in_intervals.bin").apply { writeBytes(Random.nextBytes(fileSize)) }
        val outFile = tempFolder.newFile("out_intervals.bin")

        inFile.inputStream().use { `in` ->
            outFile.outputStream().use { out ->
                copyWithRateLimitAndProgress(
                    inputStream = `in`,
                    outputStream = out,
                    speedBytesPerSecond = speed,
                    progressRatePerSecond = progressRate,
                    progressCallback = { _, _ ->
                        progressTimestamps.add(System.nanoTime())
                    }
                )
            }
        }

        // Нам нужно минимум 3 записи, чтобы проверить 2 интервала
        assertTrue("Слишком мало срабатываний коллбека для проверки интервалов", progressTimestamps.size >= 3)

        val lowerBound = (expectedIntervalMs * 0.9).toLong()
        val upperBound = (expectedIntervalMs * 1.1).toLong()

        // Проверяем все интервалы, кроме самого последнего (так как файл может завершиться
        // ровно в момент срабатывания, и последний "сон" может не состояться или быть прерван).
        for (i in 1 until progressTimestamps.size - 1) {
            val diffMs = (progressTimestamps[i] - progressTimestamps[i - 1]) / 1_000_000

            assertTrue(
                "Интервал $diffMs мс вышел за пределы [${lowerBound}, ${upperBound}] мс",
                diffMs in lowerBound..upperBound
            )
        }
    }

    // =========================================================================
    // 9. Внезапное закрытие входного или выходного потока (IOException)
    // =========================================================================
    @Test(expected = IOException::class)
    fun `should throw IOException when input stream is suddenly closed`() {
        val failingIn = object : FilterInputStream(ByteArrayInputStream(ByteArray(100_000))) {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                throw IOException("Simulated sudden input stream closure")
            }
        }
        val dummyOut = ByteArrayOutputStream()

        copyWithRateLimitAndProgress(failingIn, dummyOut, 10_000)
    }

    @Test(expected = IOException::class)
    fun `should throw IOException when output stream is suddenly closed`() {
        val dummyIn = ByteArrayInputStream(ByteArray(100_000))
        val failingOut = object : FilterOutputStream(ByteArrayOutputStream()) {
            override fun write(b: ByteArray, off: Int, len: Int) {
                throw IOException("Simulated sudden output stream closure")
            }
        }

        copyWithRateLimitAndProgress(dummyIn, failingOut, 10_000)
    }
}