package com.github.aakumykov.copy_between_streams_with_speed

import java.io.InputStream
import java.io.OutputStream
import kotlin.math.roundToLong

class ThrottledCallbackUnlimitedStreamCopier (
    private val unlimitedStreamCopier: UnlimitedStreamCopier
): Stream2StreamCopier by unlimitedStreamCopier {

    private val currentTimeMs: Long get() = System.currentTimeMillis()

    fun copyFromStreamToStreamWithCallbackRate(
        inputStream: InputStream,
        outputStream: OutputStream,
        progressCallback: ((transferredBytes:Long) -> Unit)? = null,
        progressCallbackRate: Int = 10,
        finishCallback: ((transferredBytes:Long) -> Unit)? = null,
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

        copyFromStreamToStream(
            inputStream = inputStream,
            outputStream = outputStream,
            progressCallback = progressCallbackWrapper,
            finishCallback = finishCallbackWrapper
        )
    }
}