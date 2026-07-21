package com.dailyreminder.ui.screens.idphoto

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailyreminder.data.scanner.FileExporter
import com.dailyreminder.data.scanner.IdPhotoBackground
import com.dailyreminder.data.scanner.IdPhotoSpec
import com.dailyreminder.data.scanner.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class IdPhotoUiState(
    val sourceBitmap: Bitmap? = null,
    val resultBitmap: Bitmap? = null,
    val spec: IdPhotoSpec = IdPhotoSpec.ONE_INCH,
    val background: IdPhotoBackground = IdPhotoBackground.WHITE,
    val suitEnabled: Boolean = false,
    val useFrontCamera: Boolean = true,
    val exportedFile: File? = null,
    val isBusy: Boolean = false,
    val message: String? = null
)

class IdPhotoViewModel(application: Application) : AndroidViewModel(application) {

    private val imageProcessor = ImageProcessor()
    private val fileExporter = FileExporter(application)

    private val _uiState = MutableStateFlow(IdPhotoUiState())
    val uiState: StateFlow<IdPhotoUiState> = _uiState.asStateFlow()

    fun setSpec(spec: IdPhotoSpec) {
        _uiState.update { it.copy(spec = spec) }
        regenerate()
    }

    fun setBackground(background: IdPhotoBackground) {
        _uiState.update { it.copy(background = background) }
        regenerate()
    }

    fun setSuitEnabled(enabled: Boolean) {
        _uiState.update { it.copy(suitEnabled = enabled) }
        regenerate()
    }

    fun setUseFrontCamera(enabled: Boolean) {
        _uiState.update { it.copy(useFrontCamera = enabled) }
    }

    fun onImageSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, message = "正在处理照片...") }
            runCatching {
                withContext(Dispatchers.IO) { decodeBitmap(getApplication(), uri) }
            }.onSuccess { bitmap ->
                _uiState.update { it.copy(sourceBitmap = bitmap, isBusy = false, message = "照片已导入") }
                regenerate()
            }.onFailure { error ->
                _uiState.update { it.copy(isBusy = false, message = "照片处理失败：${error.message.orEmpty()}") }
            }
        }
    }

    fun exportPhoto() {
        val bitmap = _uiState.value.resultBitmap ?: run {
            _uiState.update { it.copy(message = "请先拍照或导入照片") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, message = "正在导出证件照...") }
            runCatching {
                withContext(Dispatchers.IO) {
                    fileExporter.exportImage(bitmap, "证件照_${_uiState.value.spec.label}_${System.currentTimeMillis()}.jpg")
                }
            }.onSuccess { file ->
                _uiState.update { it.copy(exportedFile = file, isBusy = false, message = "证件照已生成") }
            }.onFailure { error ->
                _uiState.update { it.copy(isBusy = false, message = "导出失败：${error.message.orEmpty()}") }
            }
        }
    }

    fun shareIntent(file: File) = fileExporter.shareFile(file)

    fun markExportHandled() {
        _uiState.update { it.copy(exportedFile = null) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun showMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    private fun regenerate() {
        val source = _uiState.value.sourceBitmap ?: return
        val state = _uiState.value
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                imageProcessor.createIdPhoto(source, state.spec, state.background, state.suitEnabled)
            }
            _uiState.update { it.copy(resultBitmap = result) }
        }
    }

    @Suppress("DEPRECATION")
    private fun decodeBitmap(context: Context, uri: Uri): Bitmap {
        val original = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val width = info.size.width
                val height = info.size.height
                val maxSide = maxOf(width, height)
                if (maxSide > 2200) {
                    val ratio = 2200f / maxSide
                    decoder.setTargetSize((width * ratio).toInt(), (height * ratio).toInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
        return original.copy(Bitmap.Config.ARGB_8888, false)
    }
}
