package com.github.aakumykov.copy_between_streams_with_speed

import android.util.Log
import com.github.aakumykov.copy_between_streams_with_speed.ext.roundToFloatingDigits
import com.github.aakumykov.copy_between_streams_with_speed.utils.KILOBYTES
import com.github.aakumykov.copy_between_streams_with_speed.utils.MEGABYTES
import com.github.aakumykov.copy_between_streams_with_speed.utils.humanDecimalPlaces
import com.github.aakumykov.copy_between_streams_with_speed.utils.humanSizeBinary
import org.junit.Assert
import org.junit.Test
import kotlin.math.min

class SimpleStreamCopyDurationsTest : TestBase() {

    @Test
    fun a() {
        val dataSize = 10.MEGABYTES
        listOf(
//            1,2,3,4,5,6,7,8,9,
            10,
//            20,30,40,50,60,70,80,90,100
        ).forEach { pieceSize ->
            doCopy(dataSize, DEFAULT_BUFFER_SIZE) { b ->
                println("[${System.nanoTime()}] передано: ${b.humanDecimalPlaces}")
            }
        }
        println("")
    }

    @Test
    fun simple_copier_with_callback_repeat() {
        repeat(10) {
            simple_copier_with_callback()
        }
    }

    @Test
    fun simple_copier_with_callback() {

        val dataSize = 1.MEGABYTES
        val speed = 100.KILOBYTES

        prepareSourceAndTargetFiles(dataSize)

        val expectedCopyTimeNs = dataSize.toDouble() * 1000_000_000 / speed
        val commonStartTimeNs = System.nanoTime()

        SimpleStreamToStreamCopier()
            .copyFromStreamToStream(
//            .copyWithRateLimitAndProgressAI(
            inputStream = newSourceFileStream,
            outputStream = newTargetFileStream,
//            progressIntervalMs = 0,
            speedBytesPerSecond = speed,
        ) { bytesTransferred ->
            logProgress("[${System.nanoTime()}] прогресс: " +
                    "${bytesTransferred.humanDecimalPlaces} " +
                    "(${bytesTransferred.humanSizeBinary()})")
        }

        val commonDurationNs = System.nanoTime() - commonStartTimeNs
        val commonDurationMs = commonDurationNs / 1_000_000f
        val realDurationPercent = 100.0 * commonDurationNs / expectedCopyTimeNs
        println("Продолжительность записи: " +
                "${commonDurationNs.humanDecimalPlaces} нс " +
                "($commonDurationMs мс) " +
                "[${realDurationPercent.roundToFloatingDigits(2)}%]")
    }

    private fun copyWithCopierClass(
        dataSize: Long,
        speed: Int,
        onProgress: ((byteTransferred: Long) -> Unit)? = null
    ) {

    }


    private fun doCopy(dataSize: Int,
                       pieceSize: Int,
                       progressIntervalMs: Long = 1000,
                       onProgress: ((byteTransferred: Long) -> Unit)? = null
    ) {

        prepareSourceAndTargetFiles(dataSize)

        val bufferSize = min(pieceSize, dataSize)
        val dataBuffer = ByteArray(bufferSize)

        val commonStartTimeNs = System.nanoTime()

        var totalBytesWritten: Long = 0
        var lastProgressTimeNs: Long = commonStartTimeNs

        newSourceFileStream.use { inputStream ->
            newTargetFileStream.use { outputStream ->

                var readBytes: Int

                while (true) {
                    readBytes = inputStream.read(dataBuffer)

                    if (-1 == readBytes)
                        break

                    val startTimeNs = System.nanoTime()

                    outputStream.write(dataBuffer, 0, readBytes)
                    totalBytesWritten += readBytes

                    val dataCopyDurationNs = System.nanoTime() - startTimeNs
                    logD("продолжительность записи $readBytes байт: ${dataCopyDurationNs.humanDecimalPlaces} нс")

                    if ((System.nanoTime() - lastProgressTimeNs) > progressIntervalMs * 1000_000) {
                        onProgress?.invoke(totalBytesWritten)
                        lastProgressTimeNs = System.nanoTime()
                    }
                }

                outputStream.flush()
                onProgress?.invoke(totalBytesWritten)
            }
        }

        val commonDurationNs = System.nanoTime() - commonStartTimeNs
        val commonDurationMs = commonDurationNs / 1_000_000f
        println("Продолжительность записи ${dataSize.humanSizeBinary()} байт кусками по ${pieceSize.humanSizeBinary()}: " +
                "${commonDurationNs.humanDecimalPlaces} нс ($commonDurationMs мс)")

        Assert.assertEquals(
            "Размер файлов совпадает",
            sourceFile.length(),
            targetFile.length()
        )

        Assert.assertEquals(
            "Содержимое файлов совпадает",
            sourceFileContents,
            targetFileContents
        )
    }

    private fun logD(text: String) {
//        Log.d(TAG, text)
    }

    private fun logI(text: String) {
        Log.i(TAG, text)
    }

    private fun logProgress(text: String){
        println(text)
    }

    companion object {
        val TAG: String = SimpleStreamCopyDurationsTest::class.java.simpleName
    }
}