package com.github.aakumykov.copy_between_streams_with_counting_demo

import android.R.attr.text
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.github.aakumykov.copy_between_streams_with_counting_demo.databinding.ActivityDemoBinding
import com.github.aakumykov.copy_between_streams_with_counting_demo.extensions.errorMsg
import com.github.aakumykov.copy_between_streams_with_counting_demo.extensions.errorMsgExtended
import com.github.aakumykov.copy_between_streams_with_counting_demo.extensions.getIntFromPreferences
import com.github.aakumykov.copy_between_streams_with_counting_demo.extensions.storeIntInPreferences
import com.github.aakumykov.copy_between_streams_with_counting_demo.utils.random
import com.github.aakumykov.copy_between_streams_with_speed.LimitedStreamCopier
import com.github.aakumykov.copy_between_streams_with_speed.ScopedLimitedStreamCopier
import com.github.aakumykov.copy_between_streams_with_speed.utils.humanReadableByteCount
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.roundToInt

class DemoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDemoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityDemoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        "".apply {  }
        prepareButtons()
        restoreValues()
    }

    private fun prepareButtons() {
        binding.sizeSeekBar.apply {
            max = MAX_SIZE
            setProgressLabelProvider { progress ->
                val humanSize = humanReadableByteCount(progress.toLong(), decimalNotation = false)
                getString(R.string.size_selector_label, humanSize)
            }
        }
        binding.speedSeekBar.apply {
            max = MAX_SPEED
            setProgressLabelProvider { progress ->
                val humanSize = humanReadableByteCount(progress.toLong(), decimalNotation = false)
                getString(R.string.speed_selector_label, humanSize)
            }
        }
        binding.startButton.setOnClickListener { onStartButtonClicked() }
        binding.startButton2.setOnClickListener { startCopyingFile2() }
        binding.stopButton.setOnClickListener { onStopButtonClicked() }
        binding.probeButton.setOnClickListener { onProbeButtonClicked() }
    }

    private var currentInputStream: InputStream? = null

    private val limitedStreamCopier by lazy { LimitedStreamCopier() }

    private val dataSize get() = binding.sizeSeekBar.progress
    private val speed get() = binding.speedSeekBar.progress

    private val sourceFile: File by lazy { File.createTempFile("source", "file") }
    private val targetFile: File by lazy { File.createTempFile("target", "file") }

    private val sourceFileStream: InputStream get() = sourceFile.inputStream()
    private val targetFileStream: OutputStream get() = targetFile.outputStream()


    private var fileCopyingJob2: Job? = null
    private var progressCollectingJob2: Job? = null

    fun startCopyingFile2() {



        val sourceStream = sourceFileStream
        val targetStream = targetFileStream

        sourceStream.use { inputStream ->
            targetStream.use { outputStream ->

                lifecycleScope.launch (Dispatchers.IO) {

                    sourceFile.writeBytes(random.nextBytes(dataSize))

                    launch (Dispatchers.Main) {
                        showInfo("Копирование-2 начато")
                    }

                    limitedStreamCopier.copyFromStreamToStream(
                        inputStream,
                        outputStream,
                        1000_1000,
                    )

                    launch (Dispatchers.Main) {
                        showInfo("Копирование-2 завершено")
                    }

                }
            }
        }
    }

    fun stopCopyingFile2() {
        fileCopyingJob2?.cancel()
        fileCopyingJob2 = null
    }

    fun closeStream2() {
        sourceFileStream.close()
    }

    private fun onStartButtonClicked() {

        storeIntInPreferences(KEY_SIZE, dataSize)
        storeIntInPreferences(KEY_SPEED, speed)

        val eh = CoroutineExceptionHandler { context, throwable ->
            showError(throwable.errorMsgExtended)
            Log.e(TAG, throwable.errorMsg, throwable)
        }

        lifecycleScope.launch (eh + Dispatchers.IO) {


            sourceFile.writeBytes(random.nextBytes(dataSize))

            sourceFile.inputStream().use { inputStream ->
                this@DemoActivity.currentInputStream = inputStream
                targetFile.outputStream().use { outputStream ->
                    doCopy(
                        scope = this,
                        inputStream = inputStream,
                        outputStream = outputStream
                    )
                }
            }
        }
    }

    /*copyBetweenStreamsWithSpeed(
                        inputStream = inputStream,
                        outputStream = outputStream,
                        speedBytesPerSec = speed,
                        progressCallback = { transferred, _ ->
                            val percent = ((transferred.toFloat()/dataSize)*100).roundToInt()
                            showProgress(percent)
                        },
                        finishCallback = { transferredBytes: Long, timeElapsedMs: Long, speedBytesPerSec:Long ->
                            showInfo("Передано ${humanReadableByteCount(transferredBytes)}\n" +
                                    "за ${(timeElapsedMs.toFloat()/1000)} с,\n" +
                                    "скорость: ${humanReadableByteCount(speedBytesPerSec)}/с")
                        }
                    )*/

    private val scopedLimitedStreamCopier: ScopedLimitedStreamCopier by lazy {
        ScopedLimitedStreamCopier(
            scope = lifecycleScope,
            streamCopier = limitedStreamCopier
        )
    }

    private suspend fun doCopy(
        scope: CoroutineScope,
        inputStream: FileInputStream,
        outputStream: FileOutputStream
    ) {
        scope.launch {
            scopedLimitedStreamCopier.progressFlow.collect { transferred ->
                val percent = ((transferred.toFloat()/dataSize)*100).roundToInt()
                showProgress(percent)
            }
        }.invokeOnCompletion {
            println()
        }

        scopedLimitedStreamCopier.copyFromStreamToStream(
            inputStream,
            outputStream,
            speed
        )
    }

    private val probeClass by lazy {
        ProbeClass(this@DemoActivity, lifecycleScope)
    }

    private fun onProbeButtonClicked() {
        probeClass.probe()
    }

    private fun onStopButtonClicked() {
        currentInputStream?.close()
    }

    private fun showProgress(value: Int) {
        lifecycleScope.launch {
            binding.progressBar.progress = value
        }
    }

    private fun showInfo(message: String) {
        lifecycleScope.launch {
            binding.infoView.apply {
                text = message
                setTextColor(getColor(R.color.black_white_day_night))
            }
        }
    }

    private fun showError(message: String) {
        lifecycleScope.launch {
            binding.infoView.apply {
                text = message
                setTextColor(getColor(R.color.error))
            }
        }
    }

    private fun restoreValues() {
        binding.sizeSeekBar.progress = getIntFromPreferences(KEY_SIZE, DEFAULT_SIZE)
        binding.speedSeekBar.progress = getIntFromPreferences(KEY_SPEED, DEFAULT_SPEED)
    }

    companion object {
        val TAG: String = DemoActivity::class.java.simpleName
        const val KEY_SIZE = "SIZE"
        const val KEY_SPEED = "SPEED"
        const val MAX_SIZE = 12_000_000
        const val MAX_SPEED = 12_000_000
        const val DEFAULT_SIZE = 1000000
        const val DEFAULT_SPEED = 2000000
    }
}