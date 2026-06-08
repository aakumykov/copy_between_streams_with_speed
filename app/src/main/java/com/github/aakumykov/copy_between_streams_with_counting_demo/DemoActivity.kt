package com.github.aakumykov.copy_between_streams_with_counting_demo

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.github.aakumykov.copy_between_streams_with_counting_demo.databinding.ActivityDemoBinding
import com.github.aakumykov.copy_between_streams_with_counting_demo.utils.random
import com.github.aakumykov.copy_between_streams_with_speed.copyBetweenStreamsWithSpeed2
import com.github.aakumykov.copy_between_streams_with_speed.utils.humanReadable
import com.github.aakumykov.copy_between_streams_with_speed.utils.humanReadableByteCount
import kotlinx.coroutines.Dispatchers
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
        prepareButtons()
    }

    private fun prepareButtons() {
        binding.sizeSeekBar.apply {
            setProgressLabelProvider { progress ->
                val humanSize = humanReadableByteCount(progress.toLong(), decimalNotation = false)
                getString(R.string.size_selector_label, humanSize)
            }
        }
        binding.speedSeekBar.apply {
            setProgressLabelProvider { progress ->
                val humanSize = humanReadableByteCount(progress.toLong(), decimalNotation = false)
                getString(R.string.speed_selector_label, humanSize)
            }
        }
        binding.startButton.setOnClickListener { onStartButtonClicked() }
        binding.stopButton.setOnClickListener { onStopButtonClicked() }
    }

    private var currentInputStream: InputStream? = null

    private fun onStartButtonClicked() {
        val dataSize = binding.sizeSeekBar.progress
        val speed = binding.speedSeekBar.progress

        lifecycleScope.launch (Dispatchers.IO) {
            val sourceFile = File.createTempFile("source","file")
            val targetFile = File.createTempFile("target","file")

            sourceFile.writeBytes(random.nextBytes(dataSize))

            sourceFile.inputStream().use { inputStream ->
                this@DemoActivity.currentInputStream = inputStream
                targetFile.outputStream().use { outputStream ->
                    copyBetweenStreamsWithSpeed2(
                        inputStream = inputStream,
                        outputStream = outputStream,
                        speed = speed,
                        logLevel = 2,
                        progressCallback = { transferred, speed ->
                            val percent = ((transferred.toFloat()/dataSize)*100).roundToInt()
                            showProgress(percent)
                        },
                        finishCallback = { transferredBytes: Long, timeElapsedMs: Long, speedBytesPerSec:Long ->
                            showInfo("Передано ${humanReadableByteCount(transferredBytes)}\n" +
                                    "за ${(timeElapsedMs.toFloat()/1000)} с,\n" +
                                    "скорость: ${humanReadableByteCount(speedBytesPerSec)}/с")
                        }
                    )
                }
            }
        }
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
}