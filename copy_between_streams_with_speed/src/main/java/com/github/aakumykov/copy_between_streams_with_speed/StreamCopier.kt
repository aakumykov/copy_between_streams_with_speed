package com.github.aakumykov.copy_between_streams_with_speed

import kotlinx.coroutines.flow.Flow
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

interface StreamCopier {

    val progressFlow: Flow<Long>

    @Throws(IllegalStateException::class, IOException::class, IllegalArgumentException::class)
    suspend fun copyFromStreamToStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        speed: Int,
        stepsPerSecond: Int = 10
    )
}