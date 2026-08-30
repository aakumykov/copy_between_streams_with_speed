package com.github.aakumykov.copy_between_streams_with_speed

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.roundToLong

class CertainSpeedStreamCopier(
    private val desiredSpeedBytesPerSec: Int = -1,
    private val progressUpdateRatePerSec: Int = 1,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    val transferredBytes: Flow<Long> get() = _transferredBytes
    private val _transferredBytes: MutableStateFlow<Long> = MutableStateFlow(0)

    val speedBytesPerSec: Flow<Long> get() = _speed
    private val _speed: MutableStateFlow<Long> = MutableStateFlow(0)

    private var currentlyTransferredBytes: Long = -1
    private var currentSpeedBytesPerSec: Long = -1
    private var progressUpdatingJob: Job? = null

    @Throws(IOException::class, RuntimeException::class)
    fun copyWithSpeed(
        inputStream: InputStream,
        outputStream: OutputStream,
    ) {
        scope.launch (dispatcher) {
            startProgressUpdatingJob(this)
            try {
                copyBetweenStreamsWithSpeed(
                    inputStream = inputStream,
                    outputStream = outputStream,
                    progressCallback = { transferredBytes, speedBytesPerSec ->
                        currentlyTransferredBytes = transferredBytes
                        currentSpeedBytesPerSec = speedBytesPerSec
                    }
                )
            } catch (e: Exception) {
                throw e
            } finally {
                stopProgressUpdatingJob()
            }
        }
    }

    private fun startProgressUpdatingJob(parentScope: CoroutineScope) {
        if (null == progressUpdatingJob) {
            progressUpdatingJob = parentScope.launch (dispatcher) {
                while(isActive) {
                    if (-1L != currentlyTransferredBytes) {
                        _transferredBytes.emit(currentlyTransferredBytes)
                    }
                    if (-1L != currentSpeedBytesPerSec) {
                        _speed.emit(currentSpeedBytesPerSec)
                    }
                    delay(progressUpdateDelayMs)
                }
            }
        } else {
            throw RuntimeException("Progress updating job already exists.")
        }
    }

    private fun stopProgressUpdatingJob() {
        progressUpdatingJob?.cancel(CancellationException("stopProgressUpdatingJob"))
        progressUpdatingJob = null
    }

    private val progressUpdateDelayMs: Long get() {
        return (1000f / progressUpdateRatePerSec).roundToLong()
    }
}