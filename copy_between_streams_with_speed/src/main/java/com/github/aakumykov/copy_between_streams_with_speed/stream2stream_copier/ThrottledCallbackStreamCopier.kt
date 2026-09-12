package com.github.aakumykov.copy_between_streams_with_speed.stream2stream_copier

import java.io.InputStream
import java.io.OutputStream
import kotlin.math.roundToLong

class ThrottledCallbackStreamCopier (
    private val progressCallbackRate: Int = 10,
    private val streamCopier: Stream2StreamCopier
): Stream2StreamCopier by streamCopier {

    private val currentTimeMs: Long get() = System.currentTimeMillis()

    override fun copyFromStreamToStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        progressCallback: ((transferredBytes:Long) -> Unit)?,
        finishCallback: ((transferredBytes:Long) -> Unit)?,
    ) {
        val minimumCallbackPeriodMs = (1000f / progressCallbackRate).roundToLong()

        var startTimeMs = currentTimeMs
        var progressCallbackTriggeredAtLeastOnce = false

        val progressCallbackWrapper: ((transferredBytes:Long) -> Unit) = { transferredBytes ->
            if (null != progressCallback) {
                val durationMs = currentTimeMs - startTimeMs
                if (durationMs >= minimumCallbackPeriodMs) {
                    progressCallback.invoke(transferredBytes)
                    progressCallbackTriggeredAtLeastOnce = true
                    startTimeMs = currentTimeMs
                }
                if (!progressCallbackTriggeredAtLeastOnce) {
                    progressCallback.invoke(transferredBytes)
                }
            }
        }

        val finishCallbackWrapper: ((transferredBytes:Long) -> Unit)? = if (null != finishCallback) { transferredBytes ->
            finishCallback.invoke(transferredBytes)
        } else null

        streamCopier.copyFromStreamToStream(
            inputStream = inputStream,
            outputStream = outputStream,
            progressCallback = progressCallbackWrapper,
            finishCallback = finishCallbackWrapper
        )
    }
}