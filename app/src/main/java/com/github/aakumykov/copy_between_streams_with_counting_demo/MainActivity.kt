package com.github.aakumykov.copy_between_streams_with_counting_demo

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.github.aakumykov.copy_between_streams_with_counting_demo.databinding.ActivityMainBinding
import com.github.aakumykov.copy_between_streams_with_counting_demo.extensions.eraseStringFromPreferences
import com.github.aakumykov.copy_between_streams_with_counting_demo.extensions.errorMsg
import com.github.aakumykov.copy_between_streams_with_counting_demo.extensions.getStringFromPreferences
import com.github.aakumykov.copy_between_streams_with_counting_demo.extensions.storeStringInPreferences
import com.github.aakumykov.copy_between_streams_with_counting_demo.utils.humanReadableByteCount
import com.github.aakumykov.copy_between_streams_with_counting_demo.utils.random
import com.github.aakumykov.copy_between_streams_with_speed.copyBetweenStreamsWithSpeed
import com.github.aakumykov.file_lister_navigator_selector.file_lister.SimpleSortingMode
import com.github.aakumykov.file_lister_navigator_selector.file_selector.FileSelector
import com.github.aakumykov.file_lister_navigator_selector.fs_item.FSItem
import com.github.aakumykov.file_lister_navigator_selector.fs_item.SimpleFSItem
import com.github.aakumykov.local_file_lister_navigator_selector.local_file_selector.LocalFileSelector
import com.github.aakumykov.seek_bar_with_text_input.SeekBarWithTextInput
import com.github.aakumykov.storage_access_helper.StorageAccessHelper
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.roundToInt

class MainActivity :
    AppCompatActivity(),
    FileSelector.Callbacks
{

    private lateinit var binding: ActivityMainBinding
    private lateinit var storageAccessHelper: StorageAccessHelper

    private val fileSelector: FileSelector<SimpleSortingMode>
        get() = LocalFileSelector().prepare()

    private var selectedFSItem: FSItem? = null
    private var inputStream: InputStream? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        prepareButtons()
        prepareComponents()

//        action1()
    }

    private fun prepareComponents() {
        storageAccessHelper = StorageAccessHelper.create(this).apply {
            prepareForReadAccess()
        }
    }

    private fun prepareButtons() {
        binding.selectFileButton.setOnClickListener { onSelectFileClicked() }
        binding.clearSelectionButton.setOnClickListener { onClearFileSelectionClicked() }
        binding.startButton.setOnClickListener { onStartClicked() }
        binding.stopButton.setOnClickListener { onStopClicked() }
//        binding.action3Button.setOnClickListener { action3() }

        binding.dataSizeSlider.apply {
            max = MAX_SIZE
            setChangeListener(object: SeekBarWithTextInput.ChangeListener{
                override fun onSeekBarWithTextInputProgressChanged(progress: Int, fromUser: Boolean) {
//                    Log.d(TAG, "progress: $progress")
                    storeDataSize()
                }
            })
            setProgressLabelProvider { progress ->
                getString(R.string.dataSize, progress, humanReadableByteCount(progress.toLong()))
            }
        }

        binding.speedSlider.apply {
            max = MAX_SPEED
            setChangeListener(object: SeekBarWithTextInput.ChangeListener{
                override fun onSeekBarWithTextInputProgressChanged(progress: Int, fromUser: Boolean) {
//                    Log.d(TAG, "speed: $progress")
                    storeSpeed()
                }
            })
            setProgressLabelProvider { progress ->
                getString(R.string.speed, progress,
                    humanReadableByteCount(progress.toLong(), decimalNotation = true)
                )
            }
        }
    }

    private fun storeSpeed() {
        sharedPreferences.edit {
            putInt(KEY_SPEED, speedBytesPerSec)
        }
    }

    private fun storeDataSize() {
        sharedPreferences.edit {
            putInt(KEY_DATA_SIZE, dataSizeBytes)
        }
    }

    private fun onSelectFileClicked() {
        resetView()
        storageAccessHelper.requestReadAccess { selectAFile() }
    }

    private fun onClearFileSelectionClicked() {
        selectedFSItem = null
        eraseStringFromPreferences(SELECTED_ITEM)
        displayFileSelectionState()
    }

    val dataSizeBytes get() = binding.dataSizeSlider.progress
    val speedBytesPerSec get() = binding.speedSlider.progress//.let { if (0 == it) 1 else it }

    val file1: File get() {
        val f1 = File(cacheDir, "file1.bin")

        if (!f1.exists()) {
            Log.d(TAG, "@@ Создаю файл ${f1.name}")
            f1.createNewFile()
        }

        if (f1.length() != dataSizeBytes.toLong())
            f1.apply {
                createNewFile()
                this.writeBytes(random.nextBytes(dataSizeBytes))
            }

        return f1
    }

    val file2: File get() {
        val f2 = File(cacheDir, "file2.bin")
        if (!f2.exists()) f2.createNewFile()
        return f2
    }

    private val sharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    override fun onPause() {
        super.onPause()
        storeFormData()
    }

    override fun onResume() {
        super.onResume()
        restoreFormData()
    }

    private fun restoreFormData() {
        binding.dataSizeSlider.progress = sharedPreferences.getInt(KEY_DATA_SIZE, DEFAULT_DATA_SIZE)
        binding.speedSlider.progress = sharedPreferences.getInt(KEY_SPEED, DEFAULT_SPEED)
        selectedFSItem = fsItemFomJSON(getStringFromPreferences(SELECTED_ITEM))

        displayFileSelectionState()
    }

    private fun storeFormData() {
        storeSpeed()
        storeDataSize()
    }

    private fun onStartClicked() {

        val estimatedTimeMs: Int = if (speedLimitEnabled) ((dataSizeBytes.toDouble() / speedBytesPerSec) * 1000).roundToInt()
                                    else -1

        Log.d(TAG, "@@ размер данных: ${humanReadableByteCount(dataSizeBytes.toLong())}, " +
                "скорость: ${
                    humanReadableByteCount(
                        speedBytesPerSec.toLong(),
                        decimalNotation = true
                    )
                }/с, " +
                "ожидаемое время: ${estimatedTimeMs.toFloat()/100000} с")

        inputStream = file1.inputStream()
        val outputStream = file2.outputStream()

        val eh = CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, throwable.errorMsg, throwable)
            inputStream?.close()
            lifecycleScope.launch {
                showError(throwable.errorMsg)
            }
        }

        lifecycleScope.launch (eh + Dispatchers.IO) {
            lifecycleScope.launch {
                showProgressBar()
            }

            inputStream!!.use { iStream ->
                outputStream.use { oStream ->
                    doCopy(iStream, oStream, estimatedTimeMs)
                }
            }

            lifecycleScope.launch {
                delay(300)
                hideProgressBar()
            }
        }
    }

    fun doCopy(iStream: InputStream, oStream: OutputStream, estimatedTimeMs: Int) {
        copyBetweenStreamsWithSpeed(
            inputStream = iStream,
            outputStream = oStream,
            speedBytesPerSecond = if (speedLimitEnabled) speedBytesPerSec else -1,
            progressCallback = { totalBytesTransferred, speed ->
                if (isDebugEnabled)
                    Log.d(TAG, "totalBytesTransferred: $totalBytesTransferred, speed: ${
                        humanReadableByteCount(
                            speed.toLong()
                        )
                    }/s")
                // FIXME: вот этот блок существенно тормозит процесс копирования.
                //  Кажется, уже не тормозит.
                lifecycleScope.launch {
                    showProgress(totalBytesTransferred,dataSizeBytes.toLong())
                }
            },
            finishCallback = { totalBytesTransferred, elapsedTimeMs ->
                Log.d(TAG, "@@ реальное время ${elapsedTimeMs.toFloat()/1000} с")
                showInfo("ожидаемое время: ${estimatedTimeMs.toFloat()/1000}, реальное время ${elapsedTimeMs.toFloat()/1000} с")
            },
            printDebug = isDebugEnabled
        )
    }

    private fun onStopClicked() {
        inputStream?.close()
    }

    private fun action3() {

    }

    private fun resetView() {
        hideError()
        hideInfo()
    }

    private fun showProgressBar(indeterminate: Boolean = false) {
        binding.progressBar.apply {
            progress = 0
            visibility = View.VISIBLE
            isIndeterminate = indeterminate
        }
    }
    private fun hideProgressBar() {
        binding.progressBar.apply {
            visibility = View.INVISIBLE
        }
    }

    private fun showProgress(processed: Long, total: Long) {
        binding.progressBar.apply {
            max = 100
            progress = calculateProgress(processed, total)
        }
    }

    private fun calculateProgress(part: Long, total: Long): Int {
        return ((part.toFloat() / total) * 100).toInt()
    }

    private fun selectAFile() {
        fileSelector.display(this, this)
    }

    override fun onFileSelected(list: List<FSItem>) {
        selectedFSItem = list.first()
        storeStringInPreferences(SELECTED_ITEM, fsItem2JSON(selectedFSItem))
        displayFileSelectionState()
    }

    private fun displayFileSelectionState() {

        binding.selectFileButton.setText(
            if (null != selectedFSItem) R.string.select_another_file
            else R.string.select_a_file
        )

        if (null != selectedFSItem) {
            val filePath = selectedFSItem!!.absolutePath
            val fileLength = File(filePath).length()
            getString(
                R.string.selected_file_path,
                filePath,
                humanReadableByteCount(fileLength)
            ).also {
                binding.fileInfoView.text = it
            }
        } else {
            binding.fileInfoView.text = getString(R.string.file_not_selected)
        }
    }

    private fun showError(throwable: Throwable) {
        binding.errorView.text = throwable.errorMsg
        Log.d(TAG, throwable.errorMsg, throwable)
    }

    private fun showError(message: String) {
        binding.errorView.text = message
        Log.d(TAG, message)
    }

    private fun hideError() {
        binding.errorView.text = ""
    }

    private fun showInfo(text: String) {
        binding.infoView.text = text
    }

    private fun hideInfo() {
        binding.infoView.text = ""
    }

    private fun fsItem2JSON(fsItem: FSItem?): String? {
        return gson.toJson(fsItem)
    }

    private fun fsItemFomJSON(json: String?): FSItem? {
        return gson.fromJson(json, SimpleFSItem::class.java)
    }

    private val gson: Gson by lazy { Gson() }

    private val isDebugEnabled: Boolean get() = binding.debugToggle.isChecked
    private val speedLimitEnabled: Boolean get() = !binding.noSpeedLimitToggle.isChecked

    companion object {
        val TAG: String = MainActivity::class.java.simpleName
        const val SELECTED_ITEM = "SELECTED_ITEM"
        const val KEY_DATA_SIZE = "DATA_SIZE"
        const val KEY_SPEED = "SPEED"
        const val DEFAULT_DATA_SIZE: Int = 1000
        const val DEFAULT_SPEED: Int = 100

        const val MAX_SIZE = 11 * 1024 * 1024
        const val MAX_SPEED = 100 * 1024 * 1024
    }
}