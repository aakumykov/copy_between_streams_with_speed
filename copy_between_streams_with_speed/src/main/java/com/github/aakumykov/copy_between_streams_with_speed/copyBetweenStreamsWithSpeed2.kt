package com.github.aakumykov.copy_between_streams_with_speed

import com.github.aakumykov.copy_between_streams_with_speed.ext.roundToFloatingDigits
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
    dataSize: Int? = null
) {
    fun printlnDebug(text: String) { if (logLevel >= 3) println("[$tag] $text") }
    fun printDebug(text: String) { if (logLevel >= 3) print("[$tag] $text") }

    fun printlnInfo(text: String) { if (logLevel >= 2) println("[$tag] $text") }
    fun printInfo(text: String) { if (logLevel >= 2) print("[$tag] $text") }

    fun printlnError(text: String) { if (logLevel >= 1) println("[$tag] $text") }
    fun printError(text: String) { if (logLevel >= 1) print("[$tag] $text") }

    val dataSizeLogPrefix = if (null != dataSize) "Данные: $dataSize байт, " else ""

    val amountNeedToBeCopiedBeforeSleep = (speedBytesPerSec / stepsPerSecond)
    val amountToCopyingAtOnce = amountNeedToBeCopiedBeforeSleep.let { if (it > DEFAULT_BUFFER_SIZE) DEFAULT_BUFFER_SIZE else it }

    val timeForStep = 1000 / stepsPerSecond

    printlnDebug("${dataSizeLogPrefix}скорость $speedBytesPerSec байт/с")
    printlnDebug("$stepsPerSecond шагов в секунду, $amountNeedToBeCopiedBeforeSleep байт за шаг, времени на 1 шаг: $timeForStep мс")
    printlnDebug("Ожидаемое время: ${((dataSize ?: -1)/speedBytesPerSec.toDouble()).roundToFloatingDigits(3)} с")
    printlnDebug("Размер \"черпачка\": $amountToCopyingAtOnce байт")

    val buffer = ByteArray(amountToCopyingAtOnce)
    var totalBytesCopied = 0
    var bytesCopiedBeforeSleep = 0
    var readBytes: Int
    var debugCounter = 1

    val fullCopyingStartTime = System.currentTimeMillis()

    while (true) {
        val startTime = System.currentTimeMillis()

        readBytes = inputStream.read(buffer, 0, amountToCopyingAtOnce)
        if (-1 == readBytes) break
        outputStream.write(readBytes)

        bytesCopiedBeforeSleep += readBytes
        totalBytesCopied += readBytes

        val pieceTime = System.currentTimeMillis() - startTime
        printlnDebug("(${debugCounter++}) Время, затраченное на копирование $readBytes байт - $pieceTime мс")

        if (bytesCopiedBeforeSleep >= amountNeedToBeCopiedBeforeSleep) {

            val sleepLackTime = timeForStep - (System.currentTimeMillis() - startTime)
            if (sleepLackTime > 0) {
                printlnDebug(" досыпаем $sleepLackTime мс...")
                Thread.sleep(sleepLackTime)
            }

            bytesCopiedBeforeSleep = 0
        }
    }

    printlnDebug("Всего байт скопировано: $totalBytesCopied")

    val fullCopyingTimeMs = System.currentTimeMillis() - fullCopyingStartTime
    printlnDebug("Всего затрачено времени: ${fullCopyingTimeMs.toDouble()/1000} мс")

    val realSpeed = if (fullCopyingTimeMs > 0) (totalBytesCopied * 1000 / (fullCopyingTimeMs)) else -1
    val speedPercent = ((realSpeed.toDouble() / speedBytesPerSec) * 100).roundToInt()
    printlnDebug("Реальная скорость $realSpeed байт/с (${speedPercent}%)")

    printlnInfo("${dataSizeLogPrefix}скорость: $speedBytesPerSec байт/с, реальная: $realSpeed (${speedPercent}%)")
}

