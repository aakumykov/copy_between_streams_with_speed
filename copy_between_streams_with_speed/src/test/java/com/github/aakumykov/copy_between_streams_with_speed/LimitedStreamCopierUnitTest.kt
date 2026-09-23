package com.github.aakumykov.copy_between_streams_with_speed

import android.util.Log
import com.github.aakumykov.copy_between_streams_with_speed.stream2stream_copier.LimitedStreamCopier
import com.github.aakumykov.copy_between_streams_with_speed.utils.random
import org.junit.Assert
import org.junit.Test
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.roundToLong

class LimitedStreamCopierUnitTest : StreamCopierTestBase() {

    @Test
    fun simple_copy_test() {
        val base = 1024 * 1024 * 10

        val dataSize = base
        val speed = base * 10
        val rate = 1

        prepareSourceAndTargetFiles(dataSize)

        val startTime = System.currentTimeMillis()
        LimitedStreamCopier(speed, rate)
            .copyFromStreamToStream(sourceFileStream, targetFileStream)
        val duration = System.currentTimeMillis() - startTime

        val estimatedTime = (1000f * dataSize / speed).roundToLong()
        val diffPercents = 100f * duration / estimatedTime

        Log.d(TAG, "--------------------------------------------------------------------")
        Log.d(TAG, "dataSize:$dataSize, speed:$speed, est:$estimatedTime, real:$duration (${diffPercents})%")
        Log.d(TAG, "--------------------------------------------------------------------")

        Assert.assertTrue(sourceFile.exists())
        Assert.assertTrue(targetFile.exists())

        Assert.assertEquals(dataSize.toLong(), sourceFile.length())
        Assert.assertEquals(dataSize.toLong(), targetFile.length())

        Assert.assertEquals(sourceFileData, targetFileData)
    }




}