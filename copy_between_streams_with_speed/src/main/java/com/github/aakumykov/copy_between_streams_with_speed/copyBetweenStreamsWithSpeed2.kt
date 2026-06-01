package com.github.aakumykov.copy_between_streams_with_speed

import java.io.InputStream
import java.io.OutputStream

fun copyBetweenStreamsWithSpeed2(
    inputStream: InputStream,
    outputStream: OutputStream,
    speedBytesPerSec: Int = -1,
    stepsPerSecond: Int = 10,
    debug: Boolean = false
) {
    fun printDebug(text: String, tag: String = "CBSWS2") { if (debug) println("[$tag] $text") }

    val copyingPieceBytes = speedBytesPerSec / stepsPerSecond
    val timeForStep = 1000 / stepsPerSecond

    printDebug("Копирование данных со скоростью $speedBytesPerSec байт/с")
    printDebug("за $stepsPerSecond шагов в секунду")
    printDebug("частями по $copyingPieceBytes байт.")
    printDebug("Времени на 1 шаг: $timeForStep мс")

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
            printDebug(" досыпаем $sleepLackTime мс...")
            Thread.sleep(sleepLackTime)
        }


    }

    val fullCopyingTime = System.currentTimeMillis() - fullCopyingStartTime
    printDebug("Всего затрачено ${millisecondsToDHMSN(fullCopyingTime)}")

    val realSpeed = totalBytesCopied / fullCopyingTime.toDouble()
    printDebug("Реальная скорость $realSpeed байт/с")
}

