package com.dailyreminder.ui.screens.scan

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dailyreminder.data.scanner.ColorMode
import com.dailyreminder.data.scanner.ImageProcessOptions
import com.dailyreminder.utils.PermissionHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanMainScreen(
    onBack: () -> Unit,
    viewModel: ScanViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCamera by remember { mutableStateOf(false) }

    val pickImagesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onImagesSelected(
                uris = uris,
                replace = uiState.scanMode == ScanMode.SINGLE
            )
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showCamera = true
    }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
        viewModel.clearMessage()
    }

    LaunchedEffect(uiState.exportedFile) {
        val file = uiState.exportedFile ?: return@LaunchedEffect
        val shareIntent = viewModel.shareIntent(file)
        context.startActivity(Intent.createChooser(shareIntent, "分享扫描文件"))
        viewModel.markExportHandled()
    }

    if (showCamera) {
        CameraScreen(
            torchEnabled = uiState.torchEnabled,
            onImageCaptured = { uri ->
                showCamera = false
                viewModel.onImageSelected(uri)
            },
            onBack = { showCamera = false },
            onError = { error ->
                showCamera = false
                viewModel.showMessage(error)
            }
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("文档扫描") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ScanOptionsCard(
                    uiState = uiState,
                    onModeChange = viewModel::setScanMode,
                    onTorchChange = viewModel::setTorchEnabled,
                    onAutoTextBoxChange = viewModel::setAutoTextBox
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            if (PermissionHelper.hasCameraPermission(context)) {
                                showCamera = true
                            } else {
                                cameraPermissionLauncher.launch(PermissionHelper.cameraPermission)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("拍照扫描")
                    }

                    FilledTonalButton(
                        onClick = { pickImagesLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("相册导入")
                    }
                }
            }

            item {
                PageSelectorCard(
                    uiState = uiState,
                    onSelectPage = viewModel::selectPage,
                    onRemoveCurrent = viewModel::removeCurrentPage
                )
            }

            item {
                ScanPreviewCard(uiState = uiState)
            }

            item {
                ImageProcessCard(
                    options = uiState.processOptions,
                    onOptionsChange = viewModel::updateProcessOptions
                )
            }

            item {
                OCRCard(
                    text = uiState.ocrText,
                    isBusy = uiState.isBusy,
                    onRecognize = viewModel::recognizeText,
                    onRecognizeAll = viewModel::recognizeAllText
                )
            }

            item {
                ExportCard(
                    hasImage = uiState.pages.isNotEmpty(),
                    hasText = uiState.ocrText.isNotBlank(),
                    isBusy = uiState.isBusy,
                    onExportPdf = viewModel::exportPdf,
                )
            }
        }
    }
}

@Composable
private fun ScanOptionsCard(
    uiState: ScanUiState,
    onModeChange: (ScanMode) -> Unit,
    onTorchChange: (Boolean) -> Unit,
    onAutoTextBoxChange: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("扫描选项", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = uiState.scanMode == ScanMode.SINGLE,
                    onClick = { onModeChange(ScanMode.SINGLE) },
                    label = { Text("单页扫描") }
                )
                FilterChip(
                    selected = uiState.scanMode == ScanMode.MULTI,
                    onClick = { onModeChange(ScanMode.MULTI) },
                    label = { Text("多页扫描") }
                )
            }
            OptionSwitch("打开照明", uiState.torchEnabled, onTorchChange)
            OptionSwitch("自动框选文本", uiState.autoTextBox, onAutoTextBoxChange)
        }
    }
}

@Composable
private fun OptionSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PageSelectorCard(
    uiState: ScanUiState,
    onSelectPage: (Int) -> Unit,
    onRemoveCurrent: () -> Unit
) {
    if (uiState.pages.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("页面管理：${uiState.currentPageIndex + 1}/${uiState.pages.size}", fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = onRemoveCurrent) {
                    Text("删除当前页")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                uiState.pages.forEachIndexed { index, _ ->
                    FilterChip(
                        selected = index == uiState.currentPageIndex,
                        onClick = { onSelectPage(index) },
                        label = { Text("${index + 1}") }
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanPreviewCard(uiState: ScanUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("扫描预览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            val bitmap = uiState.currentBitmap
            if (bitmap == null) {
                EmptyState("请拍照或从相册导入一张文档图片")
            } else {
                ComposeImage(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "扫描预览",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

@Composable
private fun OCRCard(
    text: String,
    isBusy: Boolean,
    onRecognize: () -> Unit,
    onRecognizeAll: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.TextFields, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("OCR 文字识别", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onRecognize,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("识别当前页")
                }
                OutlinedButton(
                    onClick = onRecognizeAll,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("识别全部")
                }
            }
            Spacer(Modifier.height(12.dp))
            if (text.isBlank()) {
                EmptyState("识别完成后会在这里显示文字内容")
            } else {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column {
                        Text(
                            text = text,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedButton(
                            onClick = { clipboardManager.setText(AnnotatedString(text)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text("复制 OCR 文字")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageProcessCard(
    options: ImageProcessOptions,
    onOptionsChange: (ImageProcessOptions) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("图片修改", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = options.colorMode == ColorMode.COLOR,
                    onClick = { onOptionsChange(options.copy(colorMode = ColorMode.COLOR)) },
                    label = { Text("彩色") }
                )
                FilterChip(
                    selected = options.colorMode == ColorMode.BLACK_WHITE,
                    onClick = { onOptionsChange(options.copy(colorMode = ColorMode.BLACK_WHITE)) },
                    label = { Text("黑白") }
                )
            }
            StepButtons("清晰度", options.clarity) { onOptionsChange(options.copy(clarity = it)) }
            StepButtons("画质增强", options.quality) { onOptionsChange(options.copy(quality = it)) }
            StepButtons("锐化", options.sharpen) { onOptionsChange(options.copy(sharpen = it)) }
            OptionSwitch("自动识别裁切 / 拉正", options.autoCorrect) { onOptionsChange(options.copy(autoCorrect = it)) }
            OptionSwitch("去除阴影", options.removeShadow) { onOptionsChange(options.copy(removeShadow = it)) }
            OptionSwitch("遮挡修复", options.removeOcclusion) { onOptionsChange(options.copy(removeOcclusion = it)) }
        }
    }
}

@Composable
private fun StepButtons(
    title: String,
    value: Int,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$title：$value")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { onChange((value - 1).coerceAtLeast(0)) }) { Text("-") }
            OutlinedButton(onClick = { onChange((value + 1).coerceAtMost(3)) }) { Text("+") }
        }
    }
}

@Composable
private fun ExportCard(
    hasImage: Boolean,
    hasText: Boolean,
    isBusy: Boolean,
    onExportPdf: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("导出与分享", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "导出后会自动打开系统分享面板，可发送到微信、文件管理器或其他应用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Divider()
            Spacer(Modifier.height(12.dp))
            ExportButton(
                icon = Icons.Default.PictureAsPdf,
                title = "导出 PDF",
                subtitle = "将单页/多页扫描图像合成为 PDF",
                enabled = hasImage && !isBusy,
                onClick = onExportPdf
            )
        }
    }
}


@Composable
private fun ExportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.Start) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
