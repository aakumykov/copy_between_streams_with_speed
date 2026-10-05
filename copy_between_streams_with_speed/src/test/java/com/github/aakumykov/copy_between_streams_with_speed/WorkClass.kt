package com.github.aakumykov.copy_between_streams_with_speed

import com.github.aakumykov.copy_between_streams_with_speed.utils.random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert
import org.junit.Test
import java.io.File
import java.util.Properties
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class WorkClass : TestBase() {

    private val sourceExceptionRequired = AtomicBoolean(false)
    private val targetExceptionRequired = AtomicBoolean(false)

    private val localPropertiesFilePath = "../local.properties"

    private val yandexAuthId: String by lazy {
        val localPropertiesFile = File(localPropertiesFilePath)
        val keyYandexAuthId = "YANDEX_AUTH_ID"
        Properties().apply {
            load(localPropertiesFile.inputStream())
        }.getProperty(keyYandexAuthId)
    }

    @Test
    fun yandex_auth_id_exists() {
        Assert.assertTrue(yandexAuthId.isNotEmpty())
    }

    fun requestSourceStreamClose() {
        sourceExceptionRequired.set(true)
    }

    fun requestTargetStreamClose() {
        targetExceptionRequired.set(true)
    }

    fun work(dataSizeBytes: Int) {
        prepareSourceAndTargetFiles(dataSizeBytes.toLong())

        val source = newSourceFileStream
        val target = newTargetFileStream
        val dataBuffer = ByteArray(1)

        repeat(dataSizeBytes) {
            source.use { sourceStream ->
                target.use { targetStream ->
                    sourceStream.read(dataBuffer)
                    targetStream.write(dataBuffer)
                }
            }
            if (sourceExceptionRequired.get())
                source.close()
            if (targetExceptionRequired.get())
                target.close()
            TimeUnit.MILLISECONDS.sleep(1)
        }
    }

    suspend fun workSuspend(workTimeMs: Int) {
        repeat(workTimeMs) {
            throwExceptionIfRequired()
            delay(1)
        }
    }

    suspend fun workSuspendInContext(workTimeMs: Int): Unit = withContext(Dispatchers.IO) {
        repeat(workTimeMs) {
            throwExceptionIfRequired()
            delay(1)
        }
    }

    private fun throwExceptionIfRequired() {
        if (sourceExceptionRequired.get())
            throw RuntimeException(newExceptionMessage)
    }

    private val newExceptionMessage: String
        get() = "Исключение-${random.nextInt(100)}"
}


class WorkClassDelayedExceptionUnitTest() {

    private val newWorkClass get() = WorkClass()

    @Test
    fun sourceStreamClose(): Unit = runBlocking {
        val wc = newWorkClass
        // Без Dispatchers.IO тест не срабатывает
        launch (Dispatchers.IO) {
            delay(1000)
            wc.requestSourceStreamClose()
        }
        Assert.assertThrows(Exception::class.java) {
            wc.work(1000)
        }
    }


    @Test
    fun targetStreamClose(): Unit = runBlocking {
        val wc = newWorkClass
        // Без Dispatchers.IO тест не срабатывает
        launch (Dispatchers.IO) {
            delay(1000)
            wc.requestTargetStreamClose()
        }
        Assert.assertThrows(Exception::class.java) {
            wc.work(1000)
        }
    }


    @Test
    fun simpleWorkSuspendWithException(): Unit = runBlocking {
        val wc = newWorkClass

        // Тест работает даже без Dispatchers.IO
        launch {
            delay(1000)
            wc.requestSourceStreamClose()
        }

        try {
            wc.workSuspend(10_000)
        } catch (t: Throwable) {
            Assert.assertThrows(Exception::class.java) {
                throw t
            }
        }
    }
}