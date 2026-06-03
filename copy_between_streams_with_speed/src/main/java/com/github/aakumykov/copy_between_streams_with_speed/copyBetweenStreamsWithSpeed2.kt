package com.github.aakumykov.copy_between_streams_with_speed

import com.github.aakumykov.copy_between_streams_with_speed.ext.roundToFloatingDigits
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.math.roundToLong

val shortRandomId: String get() = UUID.randomUUID().toString().split("-")[0]

/**
 * @param logLevel 0 - No, 1 - Info, 2 - Debug
 */
fun copyBetweenStreamsWithSpeed2(
    inputStream: InputStream,
    outputStream: OutputStream,
    speedBytesPerSec: Int = -1,
    stepsPerSecond: Int = 10,
    logLevel: Int = 0,
    tag: String = "CBSWS",
    dataSize: Int? = null
) {
    fun printlnDebug(text: String) { if (logLevel >= 2) println("[$tag] $text") }
    fun printlnInfo(text: String) { if (logLevel >= 1) println("[$tag] $text") }

    val timeForStep = 1000 / stepsPerSecond
    val dataSizeForStep = (speedBytesPerSec / stepsPerSecond)
    val copyingPieceSize = dataSizeForStep.let { if (it > DEFAULT_BUFFER_SIZE) DEFAULT_BUFFER_SIZE else it }

    printlnDebug("")
    printlnDebug("dataSize: $dataSize bytes")
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

    val estimatedTimeMs = (((dataSize ?: -1)/speedBytesPerSec.toFloat())*1000).roundToLong()
    val fullCopyingTimeMs = System.currentTimeMillis() - fullCopyingStartTime
    printlnInfo("Ожидаемое время: ${estimatedTimeMs} мс, реальное время: ${fullCopyingTimeMs.toDouble()} мс (${percentOf(fullCopyingTimeMs, estimatedTimeMs)}%)")

    val realSpeed = if (fullCopyingTimeMs > 0) (bytesCopiedTotal * 1000 / (fullCopyingTimeMs)) else -1
    printlnInfo("Заданная скорость ${speedBytesPerSec}, реальная скорость $realSpeed байт/с (${percentOf(realSpeed, speedBytesPerSec.toLong())}%)")
}

