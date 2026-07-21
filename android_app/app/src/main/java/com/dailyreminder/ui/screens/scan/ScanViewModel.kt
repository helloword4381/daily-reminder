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
import com.dailyreminder.data.scanner.ColorMode
import com.dailyreminder.data.scanner.FileExporter
import com.dailyreminder.data.scanner.IdPhotoSpec
import com.dailyreminder.data.scanner.ImageProcessor
import com.dailyreminder.data.scanner.ImageProcessOptions
import com.dailyreminder.data.scanner.OCRProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class ScanUiState(
    val scanMode: ScanMode = ScanMode.SINGLE,
    val pages: List<ScanPage> = emptyList(),
    val currentPageIndex: Int = 0,
    val processOptions: ImageProcessOptions = ImageProcessOptions(),
    val autoTextBox: Boolean = true,
    val torchEnabled: Boolean = false,
    val ocrText: String = "",
    val ocrLines: List<String> = emptyList(),
    val exportedFile: File? = null,
    val idPhotoFile: File? = null,
    val idPhotoSpec: IdPhotoSpec = IdPhotoSpec.ONE_INCH,
    val isBusy: Boolean = false,
    val message: String? = null
) {
    val currentPage: ScanPage?
        get() = pages.getOrNull(currentPageIndex)

    val currentBitmap: Bitmap?
        get() = currentPage?.processedBitmap
}

data class ScanPage(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val originalBitmap: Bitmap,
    val processedBitmap: Bitmap,
    val ocrText: String = "",
    val ocrLines: List<String> = emptyList()
)

enum class ScanMode {
    SINGLE,
    MULTI
}

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val imageProcessor = ImageProcessor()
    private val ocrProcessor = OCRProcessor()
    private val fileExporter = FileExporter(application)

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    fun setScanMode(mode: ScanMode) {
        _uiState.update { it.copy(scanMode = mode) }
    }

    fun setTorchEnabled(enabled: Boolean) {
        _uiState.update { it.copy(torchEnabled = enabled) }
    }

    fun setAutoTextBox(enabled: Boolean) {
        _uiState.update { it.copy(autoTextBox = enabled) }
    }

    fun updateProcessOptions(options: ImageProcessOptions) {
        _uiState.update { it.copy(processOptions = options) }
        reprocessPages()
    }

    fun setIdPhotoSpec(spec: IdPhotoSpec) {
        _uiState.update { it.copy(idPhotoSpec = spec) }
    }

    fun onImageSelected(uri: Uri) {
        onImagesSelected(listOf(uri), replace = _uiState.value.scanMode == ScanMode.SINGLE)
    }

    fun onImagesSelected(uris: List<Uri>, replace: Boolean = false) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    message = "正在导入 ${uris.size} 张图片...",
                    exportedFile = null
                )
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    uris.map { uri ->
                        val bitmap = decodeBitmap(getApplication(), uri)
                        ScanPage(
                            uri = uri,
                            originalBitmap = bitmap,
                            processedBitmap = imageProcessor.processDocument(bitmap, _uiState.value.processOptions)
                        )
                    }
                }
            }.onSuccess { newPages ->
                _uiState.update {
                    val pages = if (replace) newPages else it.pages + newPages
                    it.copy(
                        pages = pages,
                        currentPageIndex = if (replace) 0 else pages.lastIndex,
                        ocrText = pages.joinToString("\n\n") { page -> page.ocrText }.trim(),
                        ocrLines = pages.flatMap { page -> page.ocrLines },
                        isBusy = false,
                        message = "已导入 ${newPages.size} 张图片"
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
        val page = _uiState.value.currentPage ?: run {
            _uiState.update { it.copy(message = "请先拍照或导入图片") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, message = "正在识别文字...") }
            runCatching {
                withContext(Dispatchers.Default) {
                    ocrProcessor.recognize(page.processedBitmap)
                }
            }.onSuccess { result ->
                _uiState.update {
                    val pages = it.pages.map { item ->
                        if (item.id == page.id) {
                            item.copy(ocrText = result.fullText, ocrLines = result.lines)
                        } else {
                            item
                        }
                    }
                    it.copy(
                        pages = pages,
                        ocrText = pages.joinToString("\n\n") { item -> item.ocrText }.trim(),
                        ocrLines = pages.flatMap { item -> item.ocrLines },
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

    fun recognizeAllText() {
        val pages = _uiState.value.pages
        if (pages.isEmpty()) {
            _uiState.update { it.copy(message = "请先拍照或导入图片") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, message = "正在识别全部页面...") }
            runCatching {
                withContext(Dispatchers.Default) {
                    pages.map { page ->
                        val result = ocrProcessor.recognize(page.processedBitmap)
                        page.copy(ocrText = result.fullText, ocrLines = result.lines)
                    }
                }
            }.onSuccess { updated ->
                _uiState.update {
                    it.copy(
                        pages = updated,
                        ocrText = updated.joinToString("\n\n") { page -> page.ocrText }.trim(),
                        ocrLines = updated.flatMap { page -> page.ocrLines },
                        isBusy = false,
                        message = "全部页面 OCR 识别完成"
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
        val bitmaps = _uiState.value.pages.map { it.processedBitmap }
        if (bitmaps.isEmpty()) {
            _uiState.update { it.copy(message = "请先拍照或导入图片") }
            return
        }
        export("正在导出 PDF...") {
            fileExporter.exportToPDF(bitmaps)
        }
    }

    fun exportWord() {
        val bitmaps = _uiState.value.pages.map { it.processedBitmap }
        val text = _uiState.value.ocrText.ifBlank { "暂无 OCR 识别内容" }
        if (bitmaps.isEmpty()) {
            _uiState.update { it.copy(message = "请先拍照或导入图片") }
            return
        }
        export("正在导出 Word...") {
            fileExporter.exportToWord(bitmaps, text)
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

    fun selectPage(index: Int) {
        _uiState.update {
            it.copy(currentPageIndex = index.coerceIn(0, maxOf(0, it.pages.lastIndex)))
        }
    }

    fun removeCurrentPage() {
        _uiState.update {
            val page = it.currentPage ?: return@update it
            val pages = it.pages.filterNot { item -> item.id == page.id }
            it.copy(
                pages = pages,
                currentPageIndex = it.currentPageIndex.coerceAtMost(maxOf(0, pages.lastIndex)),
                ocrText = pages.joinToString("\n\n") { item -> item.ocrText }.trim(),
                ocrLines = pages.flatMap { item -> item.ocrLines }
            )
        }
    }

    fun createIdPhoto() {
        val bitmap = _uiState.value.currentBitmap ?: run {
            _uiState.update { it.copy(message = "请先自拍或导入一张照片") }
            return
        }
        val spec = _uiState.value.idPhotoSpec
        export("正在生成${spec.label}证件照...") {
            val idPhoto = imageProcessor.createIdPhoto(bitmap, spec)
            fileExporter.exportToPDF(listOf(idPhoto), "证件照_${spec.label}_${System.currentTimeMillis()}.pdf")
        }
    }

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

    private fun reprocessPages() {
        viewModelScope.launch {
            val options = _uiState.value.processOptions
            val pages = _uiState.value.pages
            if (pages.isEmpty()) return@launch
            _uiState.update { it.copy(isBusy = true, message = "正在应用图片处理...") }
            runCatching {
                withContext(Dispatchers.Default) {
                    pages.map { page ->
                        page.copy(processedBitmap = imageProcessor.processDocument(page.originalBitmap, options))
                    }
                }
            }.onSuccess { updated ->
                _uiState.update {
                    it.copy(pages = updated, isBusy = false, message = "图片处理已应用")
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isBusy = false, message = "图片处理失败：${error.message.orEmpty()}")
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
