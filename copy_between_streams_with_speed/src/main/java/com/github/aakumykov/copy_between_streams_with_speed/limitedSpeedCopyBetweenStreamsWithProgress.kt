package com.github.aakumykov.copy_between_streams_with_speed

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

@OptIn(FlowPreview::class)
@Throws(IllegalArgumentException::class, IOException::class)
fun unlimitedSpeedCopyBetweenStreamsWithProgress(
    inputStream: InputStream,
    outputStream: OutputStream,
    speed: Long,
): Flow<Long> {

    if (speed <= 0)
        throw IllegalArgumentException("Speed must be greater than zero.")

    val stepsPerSecond
}