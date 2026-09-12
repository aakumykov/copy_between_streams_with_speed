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
        bufferSize: Int,
        progressCallback: ((stepPortionOfData: Long, transferredBytes:Long) -> Unit)?,
        finishCallback: ((transferredBytes:Long) -> Unit)?,
    ) {
        val minimumCallbackPeriodMs = (1000f / progressCallbackRate).roundToLong()

        var startTimeMs = currentTimeMs

        val progressCallbackWrapper: ((stepPortionOfData: Long, transferredBytes:Long) -> Unit) = { stepPortionOfData, transferredBytes ->
            if (null != progressCallback) {
                val durationMs = currentTimeMs - startTimeMs
                if (durationMs >= minimumCallbackPeriodMs) {
                    progressCallback.invoke(stepPortionOfData, transferredBytes)
                    startTimeMs = currentTimeMs
                }
                else if (stepPortionOfData < bufferSize) {
                    progressCallback.invoke(stepPortionOfData, transferredBytes)
                }
            }
        }

        val finishCallbackWrapper: ((transferredBytes:Long) -> Unit) = { transferredBytes ->
            finishCallback?.invoke(transferredBytes)
        }

        streamCopier.copyFromStreamToStream(
            inputStream = inputStream,
            outputStream = outputStream,
            progressCallback = progressCallbackWrapper,
            finishCallback = finishCallbackWrapper
        )
    }
}