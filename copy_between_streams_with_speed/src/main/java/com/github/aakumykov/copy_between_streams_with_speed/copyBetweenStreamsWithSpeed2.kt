package com.github.aakumykov.copy_between_streams_with_speed

import com.github.aakumykov.copy_between_streams_with_speed.ext.roundToFloatingDigits
import com.github.aakumykov.copy_between_streams_with_speed.utils.humanReadable
import com.github.aakumykov.copy_between_streams_with_speed.utils.humanReadableByteCount
import com.github.aakumykov.copy_between_streams_with_speed.utils.percentOf
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds

/**
 * @param logLevel 0 - No, 1 - Info, 2 - Debug
 */
fun copyBetweenStreamsWithSpeed2(
    inputStream: InputStream,
    outputStream: OutputStream,
    speedBytesPerSec: Int = -1, // FIXME: обрабатывать ситуацию, когда скорость не ограничена.

    progressCallback: ((transferredBytes:Long, speedBytesPerSec:Long) -> Unit)? = null,
    finishCallback: ((transferredBytes:Long, timeElapsedMs:Long, speedBytesPerSec:Long) -> Unit)? = null,

    stepsPerSecond: Int = 100,

    logLevel: Int = 0,
    logPrefix: String = "CBSWS",
    preKnownInputDataSizeBytes: Int? = null
) {
    val nanosecondsInSecond = 1_000_000_000

    fun printlnDebug(text: String) { if (logLevel >= 2) println("[$logPrefix] $text") }
    fun printlnInfo(text: String) { if (logLevel >= 1) println("[$logPrefix] $text") }

    val timeForStep = 1000 / stepsPerSecond
    val dataSizeForStep = (speedBytesPerSec / stepsPerSecond)//.let { if (it < DEFAULT_BUFFER_SIZE) }
    val copyingPieceSize = dataSizeForStep.let { if (it > DEFAULT_BUFFER_SIZE) DEFAULT_BUFFER_SIZE else it }

    printlnDebug("")
    printlnDebug("dataSize: ${humanReadableByteCount(preKnownInputDataSizeBytes)}")
    printlnDebug("speed: ${humanReadableByteCount(speedBytesPerSec)}/s")

    printlnDebug("timeForStep: $timeForStep ms")
    printlnDebug("dataSizeForStep: ${humanReadableByteCount(dataSizeForStep)}")
    printlnDebug("copyingPieceSize: ${humanReadableByteCount(copyingPieceSize)}")
    printlnDebug("")

    var bytesCopiedTotal: Long = 0
    var bytesCopiedForStep: Long = 0
    var readBytes: Int
    val buffer = ByteArray(copyingPieceSize)
    var debugCounter = 1

    val fullCopyingStartTime = System.currentTimeMillis()

    while (true) {
        val stepStartTime = System.currentTimeMillis()

        readBytes = inputStream.read(buffer, 0, copyingPieceSize)
        if (-1 == readBytes) break
        outputStream.write(readBytes)

        bytesCopiedForStep += readBytes
        bytesCopiedTotal += readBytes

        if (bytesCopiedForStep >= dataSizeForStep) {

            val bytesOverrunPercentage: Float = (bytesCopiedForStep.toFloat() / dataSizeForStep)

            val stepTime = System.currentTimeMillis()
            val stepDuration = stepTime - stepStartTime
            val sleepingLackTime = (bytesOverrunPercentage * timeForStep - stepDuration).roundToLong()
            if (sleepingLackTime > 0) {
                printlnDebug("${debugCounter++}) скопировано $bytesCopiedForStep за $stepDuration мс, досыпаем $sleepingLackTime мс...")
                Thread.sleep(sleepingLackTime)
            }

            val stepSpeedBytesPerSec:Long = (bytesCopiedForStep.toFloat() / stepTime).roundToLong()
            progressCallback?.invoke(bytesCopiedTotal, stepSpeedBytesPerSec)

            bytesCopiedForStep = 0
        }
    }

    val realCopyingDurationMs: Long = System.currentTimeMillis() - fullCopyingStartTime

    val realSpeedBytesPerSec: Long = (
            bytesCopiedTotal / (realCopyingDurationMs.toFloat()/1000)
    ).roundToLong()

    printlnDebug("")
    printlnDebug("Всего байт скопировано: ${humanReadableByteCount(bytesCopiedTotal)}")

    if (null != preKnownInputDataSizeBytes) {
        val requestedSpeedBytesPerNs: Float = speedBytesPerSec.toFloat() / nanosecondsInSecond

        val estimatedTimeNs: Long = (preKnownInputDataSizeBytes/requestedSpeedBytesPerNs).roundToLong()
        // realCopyingTimeNs может быть ноль(!)
        val realCopyingDurationNs: Long = realCopyingDurationMs.milliseconds.inWholeNanoseconds

        printlnDebug("Ожидаемое время: ${estimatedTimeNs.humanReadable} нс, " +
                "реальное время: ${realCopyingDurationNs.humanReadable} нс " +
                "(${percentOf(realCopyingDurationNs, estimatedTimeNs)}%)")

        val realSpeedBytesPerNs: Float = if (realCopyingDurationNs > 0) preKnownInputDataSizeBytes / realCopyingDurationNs.toFloat() else -1F

        val requestedSpeedBytesPerSec: Long = (requestedSpeedBytesPerNs * nanosecondsInSecond).roundToLong()
        val realSpeedBytesPerSec: Long = (realSpeedBytesPerNs * nanosecondsInSecond).roundToLong()
        val speedPercents: Float = percentOf(realSpeedBytesPerNs, requestedSpeedBytesPerNs)
        val speedPercentsAccent = speedPercents.roundToLong().let { if (it !in 81..<120) "-----> " else "" }

        printlnInfo("Размер: ${humanReadableByteCount(preKnownInputDataSizeBytes)}," +
                "заданная скорость ${humanReadableByteCount(requestedSpeedBytesPerSec)}/с, " +
                "реальная скорость ${humanReadableByteCount(realSpeedBytesPerSec)}/с " +
                "${speedPercentsAccent}(${speedPercents.roundToFloatingDigits(3)}%)")
    }

    finishCallback?.invoke(
        bytesCopiedTotal,
        realCopyingDurationMs,
        realSpeedBytesPerSec
    )
}

