package com.github.aakumykov.copy_between_streams_with_speed

import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.roundToLong

// TODO: сделать возможность ПАУЗЫ через speed=0 ?
// FIXME: разобраться с Int/Long

/**
 * @param inputStream
 * @param outputStream
 * @param speedBytesPerSecond Must be greater than 0 or -1 to disable speed control.
 * @param discretizationHz Count of parts data transfer will be splat within second to achieve
 * more smooth process. [progressCallback] will be called same times.
 * @param bufferSizeBytes Default is [DEFAULT_BUFFER_SIZE]
 * @param progressCallback Returns bytes transferred and speed bytes/sec.
 * @param finishCallback Called on work finished, returns bytes transferred size and elapsed time ms.
 */
@Throws(java.lang.IllegalArgumentException::class)
fun copyBetweenStreamsWithSpeed(
    inputStream: InputStream,
    outputStream: OutputStream,
    speedBytesPerSecond: Int = -1,
    discretizationHz: Int = 10,
    bufferSizeBytes: Int = DEFAULT_BUFFER_SIZE,
    progressCallback: ((totalBytesTransferred:Long, speed:Int) -> Unit)? = null,
    finishCallback: ((Long,Long) -> Unit)? = null,
    printDebug: Boolean = false,
){
    fun plnDebug(text: String, tag: String = "copyBetweenStreamsWithSpeed") { if (printDebug) println("[$tag] $text") }

    plnDebug("0.0.3")

    /**
     * @return Pair<sleepDiffMs:Long, speed:Int>
     */
    fun calcSleepAndSpeed(
        dataSizeNeedToBeTransferredBeforeSleep: Long,
        dataSizeRealTransferredBeforeSleep: Long,
        pieceStartTime: Long,
        pieceFinishTime: Long,
        steps: Int,
    ): Pair<Long,Int> {
        val timeWindowToTransferPieceMs = (
                (dataSizeRealTransferredBeforeSleep.toDouble() / dataSizeNeedToBeTransferredBeforeSleep) * 1000 / steps
        ).roundToLong()
        plnDebug("timeWindowToTransferPieceMs: $timeWindowToTransferPieceMs")

        val realPieceTransferTimeMs = pieceFinishTime - pieceStartTime
        val timeToCalcSpeedMs = if (realPieceTransferTimeMs > 1000) realPieceTransferTimeMs else timeWindowToTransferPieceMs

        val sleepDiffMs = timeWindowToTransferPieceMs - realPieceTransferTimeMs
        val speed = (dataSizeRealTransferredBeforeSleep.toDouble() / (timeToCalcSpeedMs / 1000)).toInt()

        return Pair(sleepDiffMs, speed)
    }

    fun calcOperationPortionSize(
        speedBytesPerSecond: Int,
        transferStepsPerSecond: Int,
        bufferSize: Int = bufferSizeBytes,
    ): Int {
        val operationPortionSize: Int =
            if (speedBytesPerSecond in 1..<bufferSize) speedBytesPerSecond
            else bufferSize
        return (operationPortionSize / transferStepsPerSecond)
    }

    fun calcDataSizeNeedToBeTransferredBeforeSleep(
        speedBytesPerSecond: Int,
        transferStepsPerSecond: Int,
        bufferSize: Int = bufferSizeBytes
    ): Int {
        val secondSize: Int = if (speedBytesPerSecond > 0) speedBytesPerSecond else bufferSize
        return secondSize / transferStepsPerSecond
    }


    plnDebug("------------------ СТАРТ ------------------")

    plnDebug("inputStream = $inputStream, " +
            "outputStream = $outputStream, " +
            "speedBytesPerSecond = $speedBytesPerSecond, " +
            "discretizationHz = $discretizationHz, " +
            "bufferSizeBytes = $bufferSizeBytes, " +
            "progressCallback = $progressCallback, " +
            "finishCallback = $finishCallback, " +
            "printDebug = $printDebug")

    if (0 == speedBytesPerSecond)
        throw IllegalArgumentException("Speed cannot be zero.")

    if (-1 != speedBytesPerSecond && discretizationHz > speedBytesPerSecond)
        throw IllegalArgumentException("Data transfer discretization per second ($discretizationHz) cannot be grater than speed per second ($speedBytesPerSecond)")

    val operationPortionSize = calcOperationPortionSize(
        speedBytesPerSecond = speedBytesPerSecond,
        transferStepsPerSecond = discretizationHz,
        bufferSize = bufferSizeBytes
    )
    plnDebug("operationPortionSize: $operationPortionSize")

    val dataSizeNeedToBeTransferredBeforeSleep = calcDataSizeNeedToBeTransferredBeforeSleep(
        speedBytesPerSecond = speedBytesPerSecond,
        transferStepsPerSecond = discretizationHz,
        bufferSize = bufferSizeBytes
    )
    plnDebug("dataSizeNeedToBeTransferredBeforeSleep: $dataSizeNeedToBeTransferredBeforeSleep")

    val dataBuffer = ByteArray(operationPortionSize)
    var readBytes: Int

    var bytesTransferredBeforeSleep: Long = 0
    var totalBytesTransferred: Long = 0

    val globalStartTime = System.currentTimeMillis()

    while (true) {
        val pieceStartTime = System.currentTimeMillis()

        readBytes = inputStream.read(dataBuffer,0,operationPortionSize)
        if (-1 == readBytes)
            break
        outputStream.write(dataBuffer,0,readBytes)

        bytesTransferredBeforeSleep += readBytes
        totalBytesTransferred += readBytes

        val timings = calcSleepAndSpeed(
            pieceStartTime = pieceStartTime,
            pieceFinishTime = System.currentTimeMillis(),
            dataSizeNeedToBeTransferredBeforeSleep = dataSizeNeedToBeTransferredBeforeSleep.toLong(),
            dataSizeRealTransferredBeforeSleep = bytesTransferredBeforeSleep,
            steps = discretizationHz
        )

        val isSpeedLimited = -1 != speedBytesPerSecond

        if (isSpeedLimited) {
            val isTime2sleep = bytesTransferredBeforeSleep >= dataSizeNeedToBeTransferredBeforeSleep
            val isLastPieceOfData = readBytes < operationPortionSize

            if (isTime2sleep || isLastPieceOfData) {
                val sleepDiff = timings.first
                if (sleepDiff > 0) {
                    plnDebug("@@ --> перед засыпанием $sleepDiff")
                    Thread.sleep(sleepDiff)
                    plnDebug("@@ <-- после засыпания $sleepDiff")
                }
            }
        }

        bytesTransferredBeforeSleep = 0

        progressCallback?.invoke(totalBytesTransferred, timings.second)
    }

    plnDebug("------------------ ФИНИШ ------------------")

    val globalFinishTime = System.currentTimeMillis()
    finishCallback?.invoke(totalBytesTransferred, (globalFinishTime - globalStartTime))
}