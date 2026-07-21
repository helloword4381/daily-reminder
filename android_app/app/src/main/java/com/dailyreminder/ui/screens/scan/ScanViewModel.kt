package com.dailyreminder.ui.screens.scan

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
import com.dailyreminder.data.scanner.ImageProcessor
import com.dailyreminder.data.scanner.OCRProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ScanUiState(
    val imageUri: Uri? = null,
    val bitmap: Bitmap? = null,
    val ocrText: String = "",
    val ocrLines: List<String> = emptyList(),
    val exportedFile: File? = null,
    val isBusy: Boolean = false,
    val message: String? = null
)

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val imageProcessor = ImageProcessor()
    private val ocrProcessor = OCRProcessor()
    private val fileExporter = FileExporter(application)

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    fun onImageSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    imageUri = uri,
                    isBusy = true,
                    message = "正在处理图片...",
                    exportedFile = null
                )
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    val bitmap = decodeBitmap(getApplication(), uri)
                    imageProcessor.enhanceForDocument(bitmap)
                }
            }.onSuccess { enhanced ->
                _uiState.update {
                    it.copy(
                        bitmap = enhanced,
                        ocrText = "",
                        ocrLines = emptyList(),
                        isBusy = false,
                        message = "图片已导入"
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isBusy = false, message = "图片处理失败：${error.message.orEmpty()}")
                }
            }
        }
    }

    fun recognizeText() {
        val bitmap = _uiState.value.bitmap ?: run {
            _uiState.update { it.copy(message = "请先拍照或导入图片") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, message = "正在识别文字...") }
            runCatching {
                withContext(Dispatchers.Default) {
                    ocrProcessor.recognize(bitmap)
                }
            }.onSuccess { result ->
                _uiState.update {
                    it.copy(
                        ocrText = result.fullText,
                        ocrLines = result.lines,
                        isBusy = false,
                        message = if (result.fullText.isBlank()) "未识别到文字" else "OCR 识别完成"
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isBusy = false, message = "OCR 识别失败：${error.message.orEmpty()}")
                }
            }
        }
    }

    fun exportPdf() {
        val bitmap = _uiState.value.bitmap ?: run {
            _uiState.update { it.copy(message = "请先拍照或导入图片") }
            return
        }
        export("正在导出 PDF...") {
            fileExporter.exportToPDF(bitmap)
        }
    }

    fun exportWord() {
        val text = _uiState.value.ocrText.ifBlank { "暂无 OCR 识别内容" }
        export("正在导出 Word...") {
            fileExporter.exportToWord(text)
        }
    }

    fun exportExcel() {
        val lines = _uiState.value.ocrLines.ifEmpty {
            _uiState.value.ocrText.lines().filter { it.isNotBlank() }
        }
        export("正在导出 Excel...") {
            fileExporter.exportToExcel(lines)
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

    private fun export(loadingMessage: String, block: () -> File) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, message = loadingMessage) }
            runCatching {
                withContext(Dispatchers.IO) { block() }
            }.onSuccess { file ->
                _uiState.update {
                    it.copy(
                        exportedFile = file,
                        isBusy = false,
                        message = "已生成：${file.name}"
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isBusy = false, message = "导出失败：${error.message.orEmpty()}")
                }
            }
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
                decoder.isMutableRequired = false
            }
        } else {
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
        return original.copy(Bitmap.Config.ARGB_8888, false)
    }
}
