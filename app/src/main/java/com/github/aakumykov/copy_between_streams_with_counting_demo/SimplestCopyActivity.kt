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
import com.github.aakumykov.copy_between_streams_with_speed.ThrottledCallbackUnlimitedStreamCopier
import com.github.aakumykov.copy_between_streams_with_speed.UnlimitedStreamCopier
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

    private val unlimitedStreamCopier: UnlimitedStreamCopier by lazy {
        UnlimitedStreamCopier()
    }

    private val throttledCallbackUnlimitedStreamCopier by lazy {
        ThrottledCallbackUnlimitedStreamCopier(unlimitedStreamCopier)
    }

    private val dataSize = 1000

    fun startCopy() {

        hideInfo()

        val sourceFile = File(cacheDir, "source_file.bin").apply { createNewFile() }
        val targetFile = File(cacheDir, "target_file.bin").apply { createNewFile() }
        if (!sourceFile.exists()) throw FileNotFoundException("source file does not exists")
        if (!targetFile.exists()) throw FileNotFoundException("target file does not exists")

        val data = random.nextBytes(dataSize)

        lifecycleScope.launch (Dispatchers.IO) {

            sourceFile.writeBytes(data)

            sourceFile.inputStream().use { inputStream ->
                targetFile.outputStream().use { outputStream ->

                    throttledCallbackUnlimitedStreamCopier
                        .copyFromStreamToStreamWithCallbackRate(
                            inputStream,
                            outputStream,
                            progressCallback = { transferredBytes ->
                                val progress = (100f * transferredBytes / dataSize).roundToInt()
                                launch (Dispatchers.Main) {
                                    showProgress(progress)
                                }
                            },
                            progressCallbackRate = 5,
                            finishCallback = { transferredBytes ->
                                showInfo("Готово")
                            }
                        )

                }
            }
        }
    }

    fun showProgress(progress: Int) {
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