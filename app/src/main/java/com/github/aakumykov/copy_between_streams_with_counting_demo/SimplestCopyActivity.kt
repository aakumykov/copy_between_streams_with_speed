package com.github.aakumykov.copy_between_streams_with_counting_demo

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.github.aakumykov.copy_between_streams_with_counting_demo.databinding.ActivitySimplestCopyBinding
import com.github.aakumykov.copy_between_streams_with_counting_demo.utils.random
import com.github.aakumykov.copy_between_streams_with_speed.stream2stream_copier.LimitedStreamCopier
import com.github.aakumykov.copy_between_streams_with_speed.stream2stream_copier.Stream2StreamCopier
import com.github.aakumykov.copy_between_streams_with_speed.stream2stream_copier.UnlimitedStreamCopier
import com.github.aakumykov.file_lister_navigator_selector.extensions.errorMsg
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.roundToInt

class SimplestCopyActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySimplestCopyBinding

    val sourceFile by lazy { File(cacheDir, "source_file.bin").apply { createNewFile() } }
    val targetFile by lazy { File(cacheDir, "target_file.bin").apply { createNewFile() } }

    private val sourceFileStream: InputStream by lazy { sourceFile.inputStream() }
    private val targetFileStream: OutputStream by lazy { targetFile.outputStream() }

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
        binding.cancelButton.setOnClickListener { cancelCopy() }
    }

    private val unlimitedStreamCopier: Stream2StreamCopier by lazy {
        UnlimitedStreamCopier()
    }

    private val limitedStreamCopier: Stream2StreamCopier by lazy {
        LimitedStreamCopier(
            speedBytesPerSecond = 3000,
            stepsPerSecond = 100
        )
    }

    private val stream2streamCopier: Stream2StreamCopier by lazy {
        limitedStreamCopier
    }

    private val dataSize = 100_000

    fun startCopy() {

        hideInfo()

        if (!sourceFile.exists()) throw FileNotFoundException("source file does not exists")
        if (!targetFile.exists()) throw FileNotFoundException("target file does not exists")

        val data = random.nextBytes(dataSize)

        val eh = CoroutineExceptionHandler { _, throwable ->
            lifecycleScope.launch (Dispatchers.Main) {
                showError(throwable)
            }
        }

        lifecycleScope.launch (eh + Dispatchers.IO) {

            sourceFile.writeBytes(data)

            sourceFileStream.use { inputStream ->
                targetFileStream.use { outputStream ->

                    stream2streamCopier
                        .copyFromStreamToStream(
                            inputStream = inputStream,
                            outputStream = outputStream,
                            progressCallback = { transferredBytes ->
                                Log.d(TAG, "transferredBytes: $transferredBytes")
                                val progress = (100f * transferredBytes / dataSize).roundToInt()
                                launch (Dispatchers.Main) {
                                    showProgress(progress)
                                }
                            },
                            progressCallbackRate = 1,
                            finishCallback = {
                                showInfo("Готово")
                            }
                        )

                }
            }
        }
    }

    fun cancelCopy() {
        when(random.nextBoolean()) {
            true -> sourceFileStream.close()
            false -> targetFileStream.close()
        }
    }


    fun showProgress(progress: Int) {
        binding.progressBar.progress = progress
    }

    fun showInfo(message: String) {
        binding.infoView.apply {
            text = message
            setTextColor(getColor(R.color.black_white_day_night))
        }
    }

    fun showError(throwable: Throwable) {
        throwable.errorMsg.also {
            binding.infoView.apply {
                text = it
                setTextColor(getColor(R.color.error))
            }
            Log.e(TAG, it, throwable)
        }
    }

    fun hideInfo() {
        binding.infoView.apply {
            text = ""
        }
    }

    companion object {
        val TAG: String = SimplestCopyActivity::class.java.simpleName
    }
}