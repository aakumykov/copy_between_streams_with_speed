package com.github.aakumykov.copy_between_streams_with_speed

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

class SimpleStreamToStreamCopierUnitTestsAI {

    private val simpleStreamToStreamCopier = SimpleStreamToStreamCopier()

    private fun copyWithRateLimitAndProgress(
        inputStream: InputStream,
        outputStream: OutputStream,
        speedBytesPerSecond: Int,
        progressIntervalMs: Long = 1000,
        onProgress: (totalBytesTransferred: Long) -> Unit
    ) {
        simpleStreamToStreamCopier.copyFromStreamToStream(
            inputStream = inputStream,
            outputStream = outputStream,
            speedBytesPerSecond = speedBytesPerSecond,
            progressIntervalMs = progressIntervalMs,
            progressCallback = onProgress
        )
    }

    // =====================================================================
    // Вспомогательные потоки для теста 6
    // =====================================================================

    /** Поток, который бросает IOException при попытке чтения */
    private class FailingInputStream : InputStream() {
        override fun read(): Int = throw IOException("Input stream is closed")
        override fun read(b: ByteArray, off: Int, len: Int): Int =
            throw IOException("Input stream is closed")
    }

    /** Поток, который бросает IOException при попытке записи */
    private class FailingOutputStream : OutputStream() {
        override fun write(b: Int) = throw IOException("Output stream is closed")
        override fun write(b: ByteArray, off: Int, len: Int) =
            throw IOException("Output stream is closed")
    }

    // =====================================================================
    // 0. Некорректные входные параметры → исключения
    // =====================================================================

    @Test(expected = IllegalArgumentException::class)
    fun `0a - throws when bytesPerSecond is zero or negative`() {
        val input = ByteArrayInputStream(ByteArray(100))
        val output = ByteArrayOutputStream()
        copyWithRateLimitAndProgress(input, output, speedBytesPerSecond = 0) { _ -> }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `0b - throws when progressIntervalMs is negative`() {
        val input = ByteArrayInputStream(ByteArray(100))
        val output = ByteArrayOutputStream()
        copyWithRateLimitAndProgress(
            input, output,
            speedBytesPerSecond = 1000,
            progressIntervalMs = -1
        ) { _ -> }
    }

    // =====================================================================
    // 1. Скопированный поток идентичен исходному
    // =====================================================================

    @Test
    fun `1 - copied data is identical to source`() {
        val sourceData = ByteArray(100_000) { (it % 256).toByte() }
        val input = ByteArrayInputStream(sourceData)
        val output = ByteArrayOutputStream()

        copyWithRateLimitAndProgress(
            inputStream = input,
            outputStream = output,
            speedBytesPerSecond = 10_000_000, // высокая скорость, чтобы не ждать
            progressIntervalMs = 10L
        ) { _ -> }

        assertArrayEquals(sourceData, output.toByteArray())
    }

    // =====================================================================
    // 2. Время копирования ≈ расчётному (±10%)
    // =====================================================================

    @Test
    fun `2 - copy time is within 10 percent of expected`() {
        val dataSize = 500_000L              // 500 КБ
        val speed = 500_000                 // 500 КБ/с
        val expectedMs = TimeUnit.SECONDS.toMillis(
            (dataSize.toDouble() / speed).toLong()
        ) // 1000 мс

        val input = ByteArrayInputStream(ByteArray(dataSize.toInt()))
        val output = ByteArrayOutputStream()

        val start = System.nanoTime()
        copyWithRateLimitAndProgress(
            inputStream = input,
            outputStream = output,
            speedBytesPerSecond = speed,
            progressIntervalMs = 50L
        ) { _ -> }
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)

        val lowerBound = (expectedMs * 0.9).toLong()
        val upperBound = (expectedMs * 1.1).toLong()

        assertTrue(
            "Elapsed time ${elapsedMs}ms is outside expected range [$lowerBound, $upperBound]ms",
            elapsedMs in lowerBound..upperBound
        )
    }

    // =====================================================================
    // 3. Значения прогресса не убывают
    // =====================================================================

    @Test
    fun `3 - progress values are monotonically non-decreasing`() {
        val input = ByteArrayInputStream(ByteArray(200_000))
        val output = ByteArrayOutputStream()
        val progressValues = mutableListOf<Long>()

        copyWithRateLimitAndProgress(
            inputStream = input,
            outputStream = output,
            speedBytesPerSecond = 1_000_000,
            progressIntervalMs = 20L
        ) { bytes -> progressValues.add(bytes) }

        assertTrue("Progress callback was never called", progressValues.isNotEmpty())

        for (i in 1 until progressValues.size) {
            assertTrue(
                "Progress decreased at index $i: " +
                        "${progressValues[i - 1]} -> ${progressValues[i]}",
                progressValues[i] >= progressValues[i - 1]
            )
        }
    }

    // =====================================================================
    // 4. Финальное значение прогресса = размер файла
    // =====================================================================

    @Test
    fun `4 - final progress value equals source size`() {
        val sourceData = ByteArray(150_000)
        val input = ByteArrayInputStream(sourceData)
        val output = ByteArrayOutputStream()
        val progressValues = mutableListOf<Long>()

        copyWithRateLimitAndProgress(
            inputStream = input,
            outputStream = output,
            speedBytesPerSecond = 1_000_000,
            progressIntervalMs = 20L
        ) { bytes -> progressValues.add(bytes) }

        assertEquals(
            sourceData.size.toLong(),
            progressValues.lastOrNull()
                ?: error("Progress callback was never called")
        )
    }

    // =====================================================================
    // 5. Интервалы между вызовами callback ≈ заданному (±10%)
    // =====================================================================

    @Test
    fun `5 - intervals between progress callbacks are within 10 percent of configured`() {
        val dataSize = 2_000_000              // 2 МБ
        val speed = 2_000_000                // 2 МБ/с → ~1 сек работы
        val intervalMs = 100L

        val input = ByteArrayInputStream(ByteArray(dataSize))
        val output = ByteArrayOutputStream()
        // Собираем времена вызовов callback в миллисекундах от старта
        val callTimesMs = mutableListOf<Long>()
        val start = System.nanoTime()

        copyWithRateLimitAndProgress(
            inputStream = input,
            outputStream = output,
            speedBytesPerSecond = speed,
            progressIntervalMs = intervalMs
        ) { _ ->
            callTimesMs.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start))
        }

        // Первый вызов происходит почти мгновенно (т.к. lastProgressTimeNs = 0),
        // поэтому анализируем интервалы начиная со 2-го вызова.
        assertTrue(
            "Expected at least 3 progress calls, got ${callTimesMs.size}",
            callTimesMs.size >= 3
        )

        val lowerBound = (intervalMs * 0.9).toLong()
        val upperBound = (intervalMs * 1.1).toLong()

        for (i in 2 until callTimesMs.size) {
            val interval = callTimesMs[i] - callTimesMs[i - 1]
            assertTrue(
                "Interval #$i = ${interval}ms is outside [$lowerBound, $upperBound]ms",
                interval in lowerBound..upperBound
            )
        }
    }

    // =====================================================================
    // 6. Внезапное закрытие потоков → IOException
    // =====================================================================

    @Test(expected = IOException::class)
    fun `6a - throws IOException when input stream is abruptly closed`() {
        val output = ByteArrayOutputStream()
        copyWithRateLimitAndProgress(
            inputStream = FailingInputStream(),
            outputStream = output,
            speedBytesPerSecond = 1_000_000
        ) { _ -> }
    }

    @Test(expected = IOException::class)
    fun `6b - throws IOException when output stream is abruptly closed`() {
        val input = ByteArrayInputStream(ByteArray(10_000))
        copyWithRateLimitAndProgress(
            inputStream = input,
            outputStream = FailingOutputStream(),
            speedBytesPerSecond = 1_000_000
        ) { _ -> }
    }
}