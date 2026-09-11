package com.github.aakumykov.copy_between_streams_with_counting_demo

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.github.aakumykov.copy_between_streams_with_counting_demo.databinding.ActivitySimplestCopyBinding
import com.github.aakumykov.copy_between_streams_with_counting_demo.extensions.showToast
import com.github.aakumykov.copy_between_streams_with_counting_demo.utils.random
import com.github.aakumykov.copy_between_streams_with_speed.LimitedStreamCopier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileNotFoundException
import kotlin.math.roundToInt

class SimplestCopyActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySimplestCopyBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySimplestCopyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        binding.startButton.setOnClickListener { startCopy() }
    }

    private val streamCopier by lazy { LimitedStreamCopier(lifecycleScope) }

    private val dataSize = 1000

    fun startCopy() {

        hideInfo()

        val sourceFile = File(cacheDir, "source_file.bin").apply { createNewFile() }
        val targetFile = File(cacheDir, "target_file.bin").apply { createNewFile() }
        if (!sourceFile.exists()) throw FileNotFoundException("source file does not exists")
        if (!targetFile.exists()) throw FileNotFoundException("target file does not exists")

        val data = random.nextBytes(dataSize)

        var progressCollectingJob: Job? = null

        lifecycleScope.launch {

            launch (Dispatchers.IO) {
                sourceFile.writeBytes(data)
            }.join()

            progressCollectingJob = launch {
                streamCopier
                    .progressFlow
                    .onCompletion {
                        showInfo("Скопировано")
                    }
                    .collect {
                        showProgress(it)
                    }
            }

            sourceFile.inputStream().use { inputStream ->
                targetFile.outputStream().use { outputStream ->
                    streamCopier.copyFromStreamToStream(
                        inputStream,
                        outputStream,
                        speedBytesPerSecond = 500,
                        stepsPerSecond = 1
                    )
                }
            }

            progressCollectingJob?.cancel()
            progressCollectingJob = null
        }
    }

    fun showProgress(value: Long) {
        val progress = ((1f * value / dataSize) * 100).roundToInt()
        binding.progressBar.progress = progress
    }

    fun showInfo(message: String) {
        binding.infoView.apply {
            text = message
        }
    }

    fun hideInfo() {
        binding.infoView.apply {
            text = ""
        }
    }
}