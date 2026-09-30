package com.github.aakumykov.copy_between_streams_with_speed

import android.util.Log
import com.github.aakumykov.copy_between_streams_with_speed.utils.KILOBYTES
import com.github.aakumykov.copy_between_streams_with_speed.utils.MEGABYTES
import com.github.aakumykov.copy_between_streams_with_speed.utils.humanDecimalPlaces
import com.github.aakumykov.copy_between_streams_with_speed.utils.humanSizeBinary
import com.github.aakumykov.copy_between_streams_with_speed.utils.random
import org.junit.Assert
import org.junit.Test
import kotlin.math.min

class SimpleStreamCopyDurations : TestBase() {

    @Test
    fun a() {
        val dataSize = 10.MEGABYTES
        listOf(
//            1,2,3,4,5,6,7,8,9,
            10,
//            20,30,40,50,60,70,80,90,100
        ).forEach { pieceSize ->
            doCopy(dataSize, DEFAULT_BUFFER_SIZE)
        }
        println("")
    }


    @Test
    fun simple_copier_with_callback() {

        val dataSize = 1.MEGABYTES
        prepareSourceAndTargetFiles(dataSize)

        val commonStartTimeNs = System.nanoTime()

        SimpleStreamToStreamCopier().copyWithRateLimitAndProgress(
            newSourceFileStream,
            newTargetFileStream,
            10.KILOBYTES.toLong()
        ) { bytesTransferred ->
            logProgress("[${System.nanoTime()}] прогресс: " +
                    "${bytesTransferred.humanDecimalPlaces} " +
                    "(${bytesTransferred.humanSizeBinary()})")
        }

        val commonDurationNs = System.nanoTime() - commonStartTimeNs
        val commonDurationMs = commonDurationNs / 1_000_000f
        println("Продолжительность записи: ${commonDurationNs.humanDecimalPlaces} нс ($commonDurationMs мс)")
    }


    private fun doCopy(dataSize: Int, pieceSize: Int) {

        prepareSourceAndTargetFiles(dataSize)

        val bufferSize = min(pieceSize, dataSize)
        val dataBuffer = ByteArray(bufferSize)

        val commonStartTimeNs = System.nanoTime()

        newSourceFileStream.use { inputStream ->
            newTargetFileStream.use { outputStream ->

                var readBytes: Int

                while (true) {
                    readBytes = inputStream.read(dataBuffer)

                    if (-1 == readBytes) {
                        break
                    }

                    val startTimeNs = System.nanoTime()

                    outputStream.write(dataBuffer, 0, readBytes)

                    val durationNs = System.nanoTime() - startTimeNs

                    logD("продолжительность записи $readBytes байт: ${durationNs.humanDecimalPlaces} нс")
                }

                outputStream.flush()
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
        val TAG: String = SimpleStreamCopyDurations::class.java.simpleName
    }
}