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
import com.github.aakumykov.copy_between_streams_with_counting_demo.databinding.ActivityDataAndSizeBinding
import com.github.aakumykov.copy_between_streams_with_counting_demo.databinding.ActivityMainBinding
import com.github.aakumykov.copy_between_streams_with_counting_demo.extensions.eraseStringFromPreferences
import com.github.aakumykov.copy_between_streams_with_counting_demo.extensions.errorMsg
import com.github.aakumykov.copy_between_streams_with_counting_demo.extensions.getStringFromPreferences
import com.github.aakumykov.copy_between_streams_with_counting_demo.extensions.showToast
import com.github.aakumykov.copy_between_streams_with_counting_demo.extensions.storeStringInPreferences
import com.github.aakumykov.copy_between_streams_with_speed.copyBetweenStreamsWithSpeed
import com.github.aakumykov.copy_between_streams_with_speed.utils.humanReadableByteCount
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

class DataAndSpeedActivity :
    AppCompatActivity(),
    FileSelector.Callbacks
{
    private lateinit var binding: ActivityDataAndSizeBinding
    private lateinit var storageAccessHelper: StorageAccessHelper


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityDataAndSizeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        prepareButtons()
    }


    private fun prepareButtons() {

        binding.startButton.setOnClickListener { onStartClicked() }
        binding.stopButton.setOnClickListener { onStopClicked() }

        binding.speedSlider.apply {
            max = MAX_SPEED
            setChangeListener(object: SeekBarWithTextInput.ChangeListener{
                override fun onSeekBarWithTextInputProgressChanged(progress: Int, fromUser: Boolean) {
                    storeSpeed()
                    if (null != selectedFSItem)
                        showEstimatedTime()
                }
            })
            setProgressLabelProvider { progress ->
                getString(R.string.speed, progress,
                    humanReadableByteCount(progress.toLong(), decimalNotation = true)
                )
            }
        }
    }

    private fun showEstimatedTime() {
        binding.estimatedTimeView.text = getString(
            R.string.estimatedTime,
            estimatedTimeSec
        )
    }

    private fun storeSpeed() {
        sharedPreferences.edit {
            putInt(KEY_SPEED, speedBytesPerSec)
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

    val speedBytesPerSec
        get() = binding.speedSlider.progress * 8

    val outputFile: File get() {
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
        binding.speedSlider.progress = sharedPreferences.getInt(KEY_SPEED, DEFAULT_SPEED)
        selectedFSItem = fsItemFomJSON(getStringFromPreferences(SELECTED_ITEM))

        displayFileSelectionState()
    }

    private fun storeFormData() {
        storeSpeed()
    }

    private fun onStartClicked() {

        if (null == selectedFSItem) {
            showToast(R.string.file_not_selected)
            return
        }

        val outputStream = outputFile.outputStream()

        val eh = CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, throwable.errorMsg, throwable)
            inputStream.close()
            lifecycleScope.launch {
                showError(throwable.errorMsg)
            }
        }

        lifecycleScope.launch (eh + Dispatchers.IO) {
            lifecycleScope.launch {
                showProgressBar()
            }

            inputStream.use { iStream ->
                outputStream.use { oStream ->
                    doCopy(iStream, oStream)
                }
            }

            lifecycleScope.launch {
                delay(300)
                hideProgressBar()
            }
        }
    }

    private val speedForCopying: Int get() {
        return if (speedLimitEnabled) speedBytesPerSec
        else -1
    }

    fun doCopy(iStream: InputStream, oStream: OutputStream) {
        copyBetweenStreamsWithSpeed(
            inputStream = iStream,
            outputStream = oStream,
            speedBytesPerSecond = speedForCopying,
            progressCallback = { processed, speed ->
                // FIXME: вот этот блок существенно тормозит процесс копирования.
                //  Кажется, уже не тормозит.
                lifecycleScope.launch {
                    showProgress(processed,selectedFileLength, speed)
                }
            },
            finishCallback = { totalBytesTransferred, elapsedTimeMs ->
                showRealTime(elapsedTimeMs)
            },
            printDebug = isDebugEnabled
        )
    }

    fun showRealTime(elapsedTimeMs: Long) {
        lifecycleScope.launch {
            val realTime = elapsedTimeMs.toFloat()/1000
            Log.d(TAG, "@@ реальное время ${realTime} с")
            binding.realTimeView.text = getString(R.string.realTime, realTime)
        }
    }

    private fun onStopClicked() {
        selectedFSItem?.also {
            inputStream.close()
        }
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

    private fun showProgress(processed: Long, total: Long, speed: Int) {

        if (isDebugEnabled) {
            Log.d(
                TAG,
                "Передано байт: $processed, " +
                        "скорость: ${humanReadableByteCount(speed.toLong())}/с"
            )
        }

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

        if (null == selectedFSItem) {
            binding.selectFileButton.setText(R.string.select_a_file)
            return
        }

        binding.selectFileButton.setText(R.string.select_another_file)

        binding.fileInfoView.text = getString(
            R.string.selected_file_path,
            selectedFile.absolutePath,
            humanReadableByteCount(selectedFileLength)
        )

        showEstimatedTime()
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
        val TAG: String = DataAndSpeedActivity::class.java.simpleName
        const val SELECTED_ITEM = "SELECTED_ITEM"
        const val KEY_DATA_SIZE = "DATA_SIZE"
        const val KEY_SPEED = "SPEED"
        const val DEFAULT_DATA_SIZE: Int = 1000
        const val DEFAULT_SPEED: Int = 100

        const val MAX_SIZE = 11 * 1024 * 1024
        const val MAX_SPEED = 100 * 1024 * 1024
    }
}