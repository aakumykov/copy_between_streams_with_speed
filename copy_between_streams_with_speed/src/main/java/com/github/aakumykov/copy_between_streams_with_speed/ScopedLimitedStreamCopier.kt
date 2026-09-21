package com.github.aakumykov.copy_between_streams_with_speed

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class ScopedLimitedStreamCopier(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val streamCopier: StreamCopier
): StreamCopier by streamCopier {

    override val progressFlow: Flow<Long>
        get() = streamCopier.progressFlow

    private var job: Job? = null

    @Throws(IllegalStateException::class, IOException::class, IllegalArgumentException::class)
    override suspend fun copyFromStreamToStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        speed: Int,
        steps: Int
    ) {
        if (null != job)
            throw IllegalStateException("Current coroutine job is not null, seems work is running.")

        job = scope.launch(dispatcher) {
            streamCopier.copyFromStreamToStream(inputStream, outputStream, speed, steps)
        }.apply {
            invokeOnCompletion {
                job?.cancel()
                job = null
                println()
            }
        }
    }
}