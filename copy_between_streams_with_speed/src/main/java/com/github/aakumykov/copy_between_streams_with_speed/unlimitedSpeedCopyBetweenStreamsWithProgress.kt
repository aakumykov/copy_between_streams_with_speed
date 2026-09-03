package com.github.aakumykov.copy_between_streams_with_speed

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.InputStream
import java.io.OutputStream

@OptIn(FlowPreview::class)
fun unlimitedSpeedCopyBetweenStreamsWithProgress(
    inputStream: InputStream,
    outputStream: OutputStream,
): Flow<Long> {

    val bufferSize = DEFAULT_BUFFER_SIZE
    val dataBuffer = ByteArray(bufferSize)

    return callbackFlow {
        var totalReadBytes: Long = 0
        while (true) {
            val readBytes = inputStream.read(dataBuffer, 0, bufferSize)
            if (-1 == readBytes) {
                break
            }
            totalReadBytes += readBytes
            outputStream.write(dataBuffer, 0, readBytes)
            trySend(totalReadBytes)
        }
        close()
        awaitClose { }
    }
}