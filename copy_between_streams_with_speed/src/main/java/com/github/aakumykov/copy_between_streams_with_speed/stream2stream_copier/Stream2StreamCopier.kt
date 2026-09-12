package com.github.aakumykov.copy_between_streams_with_speed.stream2stream_copier

import java.io.InputStream
import java.io.OutputStream

interface Stream2StreamCopier {

    fun copyFromStreamToStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        progressCallback: ((transferredBytes: Long) -> Unit)? = null,
        finishCallback: ((transferredBytes: Long) -> Unit)? = null,
    )

    fun setSpeedBytesPerSec(value: Int)
}