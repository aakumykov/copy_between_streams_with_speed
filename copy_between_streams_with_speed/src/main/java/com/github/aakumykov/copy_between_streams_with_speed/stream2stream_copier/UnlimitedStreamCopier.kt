package com.github.aakumykov.copy_between_streams_with_speed.stream2stream_copier

import java.io.InputStream
import java.io.OutputStream


class UnlimitedStreamCopier: Stream2StreamCopier {

    override fun copyFromStreamToStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        progressCallback: ((transferredBytes:Long) -> Unit)?,
        finishCallback: ((transferredBytes:Long) -> Unit)?,
    ) {
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
            progressCallback?.invoke(totalReadBytes)
        }
    }
}

