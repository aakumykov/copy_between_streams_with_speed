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

    private val streamCopier by lazy { LimitedStreamCopier() }

    private val dataSize = 1000

    private var dataCopyingJob: Job? = null

    fun startCopy() {
        val sourceFile = File(cacheDir, "source_file.bin").apply { createNewFile() }
        val targetFile = File(cacheDir, "target_file.bin").apply { createNewFile() }
        if (!sourceFile.exists()) throw FileNotFoundException("source file does not exists")
        if (!targetFile.exists()) throw FileNotFoundException("target file does not exists")

        val data = random.nextBytes(dataSize)

        dataCopyingJob = lifecycleScope.launch (Dispatchers.IO) {

            sourceFile.writeBytes(data)

            sourceFile.inputStream().use { inputStream ->
                targetFile.outputStream().use { outputStream ->

                    launch (Dispatchers.Main) {
                        streamCopier
                            .progressFlow
                            .onCompletion {
                                showToast("Скопировано")
                            }
                            .collect {
                                showProgress(it)
                            }
                    }

                    streamCopier.copyFromStreamToStream(
                        inputStream,
                        outputStream,
                        10
                    )

                    dataCopyingJob?.cancel()
                    dataCopyingJob = null
                }
            }

        }
    }

    fun showProgress(value: Long) {
        val progress = ((1f * value / dataSize) * 100).roundToInt()
        binding.progressBar.progress = progress
    }
}