package com.github.aakumykov.copy_between_streams_with_speed

import com.github.aakumykov.copy_between_streams_with_speed.utils.random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class WorkClass {

    private val exceptionRequired = AtomicBoolean(false)

    fun requestException() {
        exceptionRequired.set(true)
    }

    fun work(workTimeMs: Int) {
        repeat(workTimeMs) {
            throwExceptionIfRequired()
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
        if (exceptionRequired.get())
            throw RuntimeException(newExceptionMessage)
    }

    private val newExceptionMessage: String
        get() = "Исключение-${random.nextInt(100)}"
}


class WorkClassDelayedExceptionUnitTest() {

    private val newWorkClass get() = WorkClass()

    @Test
    fun simpleWorkWithException(): Unit = runBlocking {
        val wc = newWorkClass
        // Без Dispatchers.IO тест не срабатывает
        launch (Dispatchers.IO) {
            delay(1000)
            wc.requestException()
        }
        Assert.assertThrows(Exception::class.java) {
            wc.work(10_000)
        }
    }

    @Test
    fun simpleWorkSuspendWithException(): Unit = runBlocking {
        val wc = newWorkClass

        // Тест работает даже без Dispatchers.IO
        launch {
            delay(1000)
            wc.requestException()
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