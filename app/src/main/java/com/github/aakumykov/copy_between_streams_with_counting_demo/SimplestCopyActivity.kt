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
import com.github.aakumykov.copy_between_streams_with_speed.utils.humanSizeBinary
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

    var currentInputStream: InputStream? = null

    private val sourceFileStream: InputStream get() = sourceFile.inputStream()
    private val targetFileStream: OutputStream get() = targetFile.outputStream()

    private val speedBytesPerSec: Int get() = binding.speedSeekBar.progress
    private val stepsPerSecond: Int get() = binding.stepsSeekBar.progress
    private val dataSize: Int get() = binding.dataSizeSeekBar.progress
    private val progressRate: Int get() = binding.progressRateSeekBar.progress

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

        binding.speedSeekBar.setProgressLabelProvider { progress ->
                "Скорость $progress байт/с"
        }


        binding.dataSizeSeekBar.setProgressLabelProvider {
            "Размер $it байт"
        }

        binding.stepsSeekBar.setProgressLabelProvider {
            "$it шагов/секунду"
        }

        binding.progressRateSeekBar.setProgressLabelProvider {
            "Прогресс $it раз в секунду"
        }

    }

    private val unlimitedStreamCopier: Stream2StreamCopier by lazy {
        UnlimitedStreamCopier(progressRate)
    }

    private val limitedStreamCopier: Stream2StreamCopier get() {
        return LimitedStreamCopier(
            speedBytesPerSecond = speedBytesPerSec,
            progressRatePerSecond = progressRate,
            dataCopyStepsPerSecond = stepsPerSecond
        )
    }

    private val stream2streamCopier: Stream2StreamCopier by lazy {
        limitedStreamCopier
    }

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

            val sourceStream = sourceFileStream
            val targetStream =  targetFileStream

            currentInputStream = sourceStream

            sourceStream.use { inputStream ->
                targetStream.use { outputStream ->

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
                            finishCallback = {
                                showInfo("Готово (${it.humanSizeBinary()})")
                                currentInputStream = null
                            }
                        )

                }
            }
        }
    }

    fun cancelCopy() {
        currentInputStream?.close()
    }


    fun showProgress(progress: Int) {
        Log.d(TAG, "прогресс: $progress")
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