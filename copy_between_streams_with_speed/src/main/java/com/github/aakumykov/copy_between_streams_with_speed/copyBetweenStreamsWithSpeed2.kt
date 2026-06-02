package com.github.aakumykov.copy_between_streams_with_speed

import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.math.roundToInt

val shortRandomId: String get() = UUID.randomUUID().toString().split("-")[0]

/**
 * @param logLevel 0 - No, 1 - Error, 2 - Info, 3 - Debug
 */
fun copyBetweenStreamsWithSpeed2(
    inputStream: InputStream,
    outputStream: OutputStream,
    speedBytesPerSec: Int = -1,
    stepsPerSecond: Int = 10,
    logLevel: Int = 0,
    tag: String = "CBSWS",
    prefix: String = ""
) {
    fun printlnDebug(text: String) { if (logLevel >= 3) println("[$tag] $text") }
    fun printDebug(text: String) { if (logLevel >= 3) print("[$tag] $text") }

    fun printlnInfo(text: String) { if (logLevel >= 2) println("[$tag] $text") }
    fun printInfo(text: String) { if (logLevel >= 2) print("[$tag] $text") }

    fun printlnError(text: String) { if (logLevel >= 1) println("[$tag] $text") }
    fun printError(text: String) { if (logLevel >= 1) print("[$tag] $text") }


    val copyingPieceBytes = speedBytesPerSec / stepsPerSecond
    val timeForStep = 1000 / stepsPerSecond

    printlnDebug("Копирование данных со скоростью $speedBytesPerSec байт/с")
    printDebug("За $stepsPerSecond шагов в секунду"); printlnDebug("частями по $copyingPieceBytes байт.")
    printlnDebug("Времени на 1 шаг: $timeForStep мс")

    var totalBytesCopied: Int = 0
    var readBytes: Int
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

    val fullCopyingStartTime = System.currentTimeMillis()

    while (true) {
        val startTime = System.currentTimeMillis()
        readBytes = inputStream.read(buffer, 0, copyingPieceBytes)

        if (-1 == readBytes)
            break

        totalBytesCopied += readBytes

        outputStream.write(readBytes)
        val pieceTime = System.currentTimeMillis() - startTime
        printDebug("(${shortRandomId}) Время, затраченное на копирование $readBytes байт - $pieceTime мс")

        val sleepLackTime = timeForStep - pieceTime
        if (sleepLackTime > 0) {
            printlnDebug(" досыпаем $sleepLackTime мс...")
            Thread.sleep(sleepLackTime)
        }


    }

    printlnDebug("Всего байт скопировано: $totalBytesCopied")

    val fullCopyingTimeMs = System.currentTimeMillis() - fullCopyingStartTime
    printlnDebug("Всего затрачено времени: ${fullCopyingTimeMs} мс")

    val realSpeed = if (fullCopyingTimeMs > 0) (totalBytesCopied * 1000 / (fullCopyingTimeMs)) else -1
    val speedPercent = ((realSpeed.toDouble() / speedBytesPerSec) * 100).roundToInt()
    printlnDebug("Реальная скорость $realSpeed байт/с (${speedPercent}%)")

    val realPrefix = if (prefix.isNotEmpty()) "${prefix}, " else prefix
    printlnInfo("${realPrefix}скорость: $speedBytesPerSec байт/с, реальная: $realSpeed (${speedPercent}%)")
}

