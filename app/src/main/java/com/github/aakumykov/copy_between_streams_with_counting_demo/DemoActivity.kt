package com.github.aakumykov.copy_between_streams_with_counting_demo

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.github.aakumykov.copy_between_streams_with_counting_demo.databinding.ActivityDemoBinding
import com.github.aakumykov.copy_between_streams_with_counting_demo.extensions.getIntFromPreferences
import com.github.aakumykov.copy_between_streams_with_counting_demo.extensions.storeIntInPreferences
import com.github.aakumykov.copy_between_streams_with_counting_demo.extensions.storeStringInPreferences
import com.github.aakumykov.copy_between_streams_with_counting_demo.utils.random
import com.github.aakumykov.copy_between_streams_with_speed.copyBetweenStreamsWithSpeed
import com.github.aakumykov.copy_between_streams_with_speed.utils.humanReadableByteCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
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
        binding.progressDebounceSeekBar.apply {
            max = MAX_DEBOUNCE
            setProgressLabelProvider { progress ->
                getString(R.string.debounce_selector_label, progress)
            }
        }
        binding.startButton.setOnClickListener { onStartButtonClicked() }
        binding.stopButton.setOnClickListener { onStopButtonClicked() }
        binding.probeButton.setOnClickListener { onProbeButtonClicked() }
    }

    private var currentInputStream: InputStream? = null

    @OptIn(FlowPreview::class)
    private fun onStartButtonClicked() {

        storeIntInPreferences(KEY_SIZE, dataSize)
        storeIntInPreferences(KEY_SPEED, speed)
        storeIntInPreferences(KEY_DEBOUNCE, debounce)

        lifecycleScope.launch (Dispatchers.IO) {
            val sourceFile = File.createTempFile("source","file")
            val targetFile = File.createTempFile("target","file")

            sourceFile.writeBytes(random.nextBytes(dataSize))

            val flow: Flow<Int> = callbackFlow {

                sourceFile.inputStream().use { inputStream ->
                    this@DemoActivity.currentInputStream = inputStream

                    targetFile.outputStream().use { outputStream ->

                        copyBetweenStreamsWithSpeed(
                            inputStream = inputStream,
                            outputStream = outputStream,
                            speedBytesPerSec = speed,
                            progressCallback = { transferred, _ ->
                                val percent = ((transferred.toFloat()/dataSize)*100).roundToInt()
                                trySend(percent)
                            },
                            finishCallback = { transferredBytes: Long, timeElapsedMs: Long, speedBytesPerSec:Long ->
                                showInfo("Передано ${humanReadableByteCount(transferredBytes)}\n" +
                                        "за ${(timeElapsedMs.toFloat()/1000)} с,\n" +
                                        "скорость: ${humanReadableByteCount(speedBytesPerSec)}/с")
                            }
                        )

                    }
                }

                awaitClose {
                    Log.d(TAG, "awaitClose{}")
                }
            }

            flow
//                .distinctUntilChanged()
                .debounce { debounce.toLong() }
                .collect {
                    Log.d(TAG, "прогресс: $it")
                    showProgress(it)
                }
        }
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

    private fun showInfo(text: String) {
        lifecycleScope.launch {
            binding.infoView.text = text
        }
    }

    private fun restoreValues() {
        binding.sizeSeekBar.progress = getIntFromPreferences(KEY_SIZE, DEFAULT_SIZE)
        binding.speedSeekBar.progress = getIntFromPreferences(KEY_SPEED, DEFAULT_SPEED)
        binding.progressDebounceSeekBar.progress = getIntFromPreferences(KEY_DEBOUNCE, DEFAULT_DEBOUNCE)
    }

    private val dataSize get() = binding.sizeSeekBar.progress
    private val speed get() = binding.speedSeekBar.progress
    private val debounce get() = binding.progressDebounceSeekBar.progress

    companion object {
        val TAG: String = DemoActivity::class.java.simpleName
        const val KEY_SIZE = "SIZE"
        const val KEY_SPEED = "SPEED"
        const val KEY_DEBOUNCE = "DEBOUNCE"

        const val MAX_SIZE = 12_000_000
        const val MAX_SPEED = 12_000_000
        const val MAX_DEBOUNCE = 100

        const val DEFAULT_SIZE = 1000000
        const val DEFAULT_SPEED = 2000000
        const val DEFAULT_DEBOUNCE = 10
    }
}