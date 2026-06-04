package com.github.aakumykov.copy_between_streams_with_speed

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

    stepsPerSecond: Int = 10,

    logLevel: Int = 0,
    logPrefix: String = "CBSWS",
    preKnownInputDataSizeBytes: Int? = null
) {
    fun printlnDebug(text: String) { if (logLevel >= 2) println("[$logPrefix] $text") }
    fun printlnInfo(text: String) { if (logLevel >= 1) println("[$logPrefix] $text") }

    val timeForStep = 1000 / stepsPerSecond
    val dataSizeForStep = (speedBytesPerSec / stepsPerSecond)//.let { if (it < DEFAULT_BUFFER_SIZE) }
    val copyingPieceSize = dataSizeForStep.let { if (it > DEFAULT_BUFFER_SIZE) DEFAULT_BUFFER_SIZE else it }

    printlnDebug("")
    printlnDebug("dataSize: $preKnownInputDataSizeBytes bytes")
    printlnDebug("speed: $speedBytesPerSec bytes/sec")

    printlnDebug("timeForStep: $timeForStep ms")
    printlnDebug("dataSizeForStep: $dataSizeForStep bytes")
    printlnDebug("copyingPieceSize: $copyingPieceSize bytes")
    printlnDebug("")

    var bytesCopiedTotal = 0
    var bytesCopiedForStep = 0
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

            val stepTime = System.currentTimeMillis() - stepStartTime
            val sleepingLackTime = timeForStep - stepTime
            if (sleepingLackTime > 0) {
                printlnDebug("${debugCounter++}) скопировано $bytesCopiedForStep за $stepTime мс, досыпаем $sleepingLackTime мс...")
                Thread.sleep(sleepingLackTime)
            }
            bytesCopiedForStep = 0
        }
    }

    printlnDebug("")
    printlnInfo("Всего байт скопировано: $bytesCopiedTotal")

    if (null != preKnownInputDataSizeBytes) {
        val requestedSpeedBytesPerNs: Float = speedBytesPerSec.toFloat() / 1_000_000_000

        val estimatedTimeNs: Long = (preKnownInputDataSizeBytes/requestedSpeedBytesPerNs).roundToLong()
        // realCopyingTimeNs может быть ноль!
        val realCopyingTimeNs: Long = (System.currentTimeMillis() - fullCopyingStartTime).milliseconds.inWholeNanoseconds

        val realSpeedBytesPerNs: Float = if (realCopyingTimeNs > 0) preKnownInputDataSizeBytes / realCopyingTimeNs.toFloat() else -1F

        printlnInfo("Ожидаемое время: $estimatedTimeNs нс, " +
                "реальное время: $realCopyingTimeNs нс " +
                "(${percentOf(realCopyingTimeNs, estimatedTimeNs)}%)")

        printlnInfo("Заданная скорость ${requestedSpeedBytesPerNs} байт/нс, " +
                "реальная скорость $realSpeedBytesPerNs байт/нс " +
                "(${percentOf(realSpeedBytesPerNs, requestedSpeedBytesPerNs)}%)")
    }
}

