package com.github.aakumykov.copy_between_streams_with_speed

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.sample
import java.io.InputStream
import java.io.OutputStream

@OptIn(FlowPreview::class)
fun copyBetweenStreamsWithSpeedUnlimited(
    inputStream: InputStream,
    outputStream: OutputStream,
): Flow<Long> {

    val progressReturnFrequency = 10
    val bufferSize = DEFAULT_BUFFER_SIZE
    val dataBuffer = ByteArray(bufferSize)

    return callbackFlow {
        var totalReadBytes: Long = -1
        while (true) {
            val readCount = inputStream.read(dataBuffer, 0, bufferSize)
            if (-1 == readCount) {
                trySend(totalReadBytes)
                return@callbackFlow
            }
            totalReadBytes += readCount
            outputStream.write(dataBuffer, 0, readCount)
            trySend(totalReadBytes)
        }
    }.sample(10L)
}