package com.github.aakumykov.copy_between_streams_with_speed

import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.math.roundToLong

val shortRandomId: String get() = UUID.randomUUID().toString().split("-")[0]

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
}

