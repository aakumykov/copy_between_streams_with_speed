package com.github.aakumykov.copy_between_streams_with_counting_demo

import android.content.Context
import android.os.Environment
import android.util.Log
import com.github.aakumykov.copy_between_streams_with_speed.copyBetweenStreamsWithSpeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.Date

class ProbeClass(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {

    val sourceFileName = "debian.iso"
    val targetFileName = "debian2.iso"
    val sourceDir = context.cacheDir
    val targetDir = context.cacheDir

    val sourceFile = File(sourceDir, sourceFileName)
    val targetFile = File(targetDir, targetFileName)

    fun probe() {
        coroutineScope.launch (Dispatchers.IO) {
            repeat(1) { i ->
                val n = i+1
                val bufferSize = n * DEFAULT_BUFFER_SIZE
                Log.d(TAG, "===== probe(${n}x$DEFAULT_BUFFER_SIZE)")
                val startTime = currentTime
                sourceFile.inputStream().use { inputStream ->
                    targetFile.outputStream().use { outputStream ->
                        copyBetweenStreamsWithSpeed(
                            inputStream = inputStream,
                            outputStream = outputStream,
                            // Жёстко заданное количество
                            // шагов в секунду (100)
                            // являет бутылочное горлышко,
                            // если скорость не задана,
                            // так как размер буфера фиксирован.
                            stepsPerSecond = 10_000
                        )
                    }
                }
                val duration = currentTime - startTime
                Log.d(TAG, "длительность: $duration")
            }
        }
    }

    companion object {
        val TAG: String = ProbeClass::class.java.simpleName
    }
}

val currentTime: Long get() = Date().time