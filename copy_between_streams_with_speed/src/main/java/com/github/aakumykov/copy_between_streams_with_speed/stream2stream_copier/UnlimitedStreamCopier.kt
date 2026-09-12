package com.github.aakumykov.copy_between_streams_with_speed.stream2stream_copier

import java.io.InputStream
import java.io.OutputStream
import kotlin.math.roundToLong


class UnlimitedStreamCopier(
    private val progressRatePerSecond: Int,
): Stream2StreamCopier {

    override fun copyFromStreamToStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        progressCallback: ((transferredBytes: Long) -> Unit)?,
        finishCallback: ((transferredBytes: Long) -> Unit)?,
    ) {
        val minimumProgressCallbackPeriodMs = (1000f / progressRatePerSecond).roundToLong()
        var lastProgressPublishTimeMs: Long = 0

        fun publishProgressIfItsTime(totalDataRead: Long) {
            val interval = System.currentTimeMillis() - lastProgressPublishTimeMs
            if (interval >= minimumProgressCallbackPeriodMs) {
                progressCallback?.invoke(totalDataRead)
                lastProgressPublishTimeMs = System.currentTimeMillis()
            }
        }

        val bufferSize = DEFAULT_BUFFER_SIZE
        val dataBuffer = ByteArray(bufferSize)

        var totalReadBytes: Long = 0

        while (true) {
            val readBytes = inputStream.read(dataBuffer, 0, bufferSize)
            if (-1 == readBytes) {
                finishCallback?.invoke(totalReadBytes)
                break
            }
            totalReadBytes += readBytes
            outputStream.write(dataBuffer, 0, readBytes)

            publishProgressIfItsTime(totalReadBytes)
        }
    }

    override fun setSpeedBytesPerSec(value: Int) {

    }
}

