package com.github.aakumykov.copy_between_streams_with_speed

import java.io.InputStream
import java.io.OutputStream
import kotlin.math.roundToLong

fun copyBetweenStreamsWithSpeed2(
    inputStream: InputStream,
    outputStream: OutputStream,
    speedBytesPerSec: Int = -1,
    stepsPerSecond: Int = 10,
    debug: Boolean = false
) {
    fun printlnDebug(text: String, tag: String = "CBSWS2") { if (debug) println("[$tag] $text") }
    fun printDebug(text: String, tag: String = "CBSWS2") { if (debug) print("[$tag] $text") }

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
        printDebug("Время, затраченное на копирование $readBytes байт - $pieceTime мс")

        val sleepLackTime = timeForStep - pieceTime
        if (sleepLackTime > 0) {
            printlnDebug(" досыпаем $sleepLackTime мс...")
            Thread.sleep(sleepLackTime)
        }


    }

    printlnDebug("Всего байт скопировано: $totalBytesCopied")

    val fullCopyingTimeMs = System.currentTimeMillis() - fullCopyingStartTime
    val fullCopyingTimeSec = (fullCopyingTimeMs.toDouble() / 1000).roundToLong()
    printlnDebug("Всего затрачено времени: ${fullCopyingTimeSec}с")

    val realSpeed = if (fullCopyingTimeSec > 0) (totalBytesCopied / (fullCopyingTimeSec)) else -1
    printlnDebug("Реальная скорость $realSpeed байт/с")
}

