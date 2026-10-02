package com.github.aakumykov.copy_between_streams_with_counting_demo

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.github.aakumykov.copy_between_streams_with_counting_demo.databinding.ActivitySimplestCopyBinding
import com.github.aakumykov.copy_between_streams_with_counting_demo.extensions.showToast
import com.github.aakumykov.copy_between_streams_with_counting_demo.utils.writeTestDataToFile
import com.github.aakumykov.copy_between_streams_with_speed.SimpleStreamToStreamCopier
import com.github.aakumykov.copy_between_streams_with_speed.utils.humanSizeBinary
import com.github.aakumykov.file_lister_navigator_selector.extensions.errorMsg
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

class SimplestCopyActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySimplestCopyBinding

    val sourceFile by lazy { File(cacheDir, "source_file.bin").apply { createNewFile() } }
    val targetFile by lazy { File(cacheDir, "target_file.bin").apply { createNewFile() } }

    var currentInputStream: InputStream? = null

    private val sourceFileStream: InputStream get() = sourceFile.inputStream()
    private val targetFileStream: OutputStream get() = targetFile.outputStream()

    private val speedBytesPerSec: Int get() = binding.speedSeekBar.progress
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
        binding.startButton.setOnClickListener { onStartButtonClicked() }
        binding.closeStreamButton.setOnClickListener { onCloseStreamClicked() }
        binding.cancelJobButton.setOnClickListener { onCancelJobClicked() }


        binding.dataSizeSeekBar.apply {
            setProgressLabelProvider {
                "Размер {${it.humanSizeBinary()}}"
            }
        }

        binding.speedSeekBar.apply {
            setProgressLabelProvider { progress ->
                "Скорость ${progress.humanSizeBinary()}/с"
            }
            /*setChangeListener(object: SeekBarWithTextInput.ChangeListener{
                override fun onSeekBarWithTextInputProgressChanged(
                    progress: Int,
                    fromUser: Boolean
                ) {
                    limitedStreamCopier.setSpeedBytesPerSec(progress)
                }
            })*/
        }

        binding.progressRateSeekBar.apply {
            setProgressLabelProvider {
                "Прогресс $it раз в секунду"
            }
            /*setChangeListener(object: SeekBarWithTextInput.ChangeListener{
                override fun onSeekBarWithTextInputProgressChanged(
                    progress: Int,
                    fromUser: Boolean
                ) {
                    limitedStreamCopier.setProgressRate(progress)
                }
            })*/
        }
    }

    private val streamCopier: SimpleStreamToStreamCopier get() {
        return SimpleStreamToStreamCopier()
    }

    private var currentJob: Job? = null


    private fun onStartButtonClicked() {
        if (null == currentJob) {
            startCopy()
        } else {
            showToast("Копирование уже идёт")
        }
    }

    private fun startCopy() {

        hideInfo()
        binding.progressBar.progress = 0

        if (!sourceFile.exists()) throw FileNotFoundException("source file does not exists")
        if (!targetFile.exists()) throw FileNotFoundException("target file does not exists")

        val eh = CoroutineExceptionHandler { _, throwable ->
            lifecycleScope.launch (Dispatchers.Main) {
                showError(throwable)
            }
        }

        currentJob = lifecycleScope.launch (eh + Dispatchers.IO) {

            writeTestDataToFile(sourceFile, dataSize)

            val sourceStream = sourceFileStream
            val targetStream =  targetFileStream

            currentInputStream = sourceStream

            sourceStream.use { inputStream ->
                targetStream.use { outputStream ->

                    try {
                        streamCopier
                            .copyFromStreamToStream(
                                inputStream = inputStream,
                                outputStream = outputStream,
                                progressRatePerSecond = progressRate,
                                speedBytesPerSecond = speedBytesPerSec,
                                progressCallback = { transferredBytes, speedBytesPerSecond ->
                                    Log.d(TAG, "transferredBytes: $transferredBytes")
                                    val progress = (100f * transferredBytes / dataSize).roundToInt()
                                    launch (Dispatchers.Main) {
                                        showProgress(progress)
                                        showSpeed(speedBytesPerSecond)
                                    }
                                },
                                finishCallback = {
                                    showInfo("Готово, скопировано ${it.humanSizeBinary()}")
                                }
                            )
                    }
                    finally {
                        currentJob = null
                        currentInputStream = null
                    }
                }
            }
        }
    }

    private fun onCloseStreamClicked() {
        currentInputStream?.close() ?: run {
            showToast("Копирование не запущено")
        }
    }

    private fun onCancelJobClicked() {
        currentJob?.cancel(CancellationException("Отменено пользователем"))
            ?: run { showToast("Задача не найдена") }
    }

    private fun showProgress(progress: Int) {
        Log.d(TAG, "прогресс: $progress")
        binding.progressBar.progress = progress
    }

    private fun showSpeed(speed: Long) {
//        val textShort = "$speed байт / с"
        val textShort = "${speed.humanSizeBinary()}/с"
        Log.d(TAG, "скорость: $textShort")
        binding.speedView.text = textShort
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