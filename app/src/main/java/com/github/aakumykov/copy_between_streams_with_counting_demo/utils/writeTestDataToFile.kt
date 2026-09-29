package com.github.aakumykov.copy_between_streams_with_counting_demo.utils

import java.io.File

fun writeTestDataToFile(file: File, dataSizeBytes: Int) {

    val pieceSize = DEFAULT_BUFFER_SIZE
    val mainSteps = dataSizeBytes / pieceSize

    var alreadyWritten = 0

    file.outputStream().use { outputStream ->

        fun writeAndDisplay(data: ByteArray) {
            outputStream.write(data)
            val count = data.size
            alreadyWritten += count
        }

        repeat(mainSteps) {
            writeAndDisplay(random.nextBytes(pieceSize))
        }

        val additionalBytesCount = dataSizeBytes - alreadyWritten
        writeAndDisplay(random.nextBytes(additionalBytesCount))
    }
}