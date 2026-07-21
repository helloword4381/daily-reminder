package com.dailyreminder.ui.screens.idphoto

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dailyreminder.data.scanner.IdPhotoBackground
import com.dailyreminder.data.scanner.IdPhotoSpec
import com.dailyreminder.ui.screens.scan.CameraScreen
import com.dailyreminder.utils.PermissionHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdPhotoScreen(
    onBack: () -> Unit,
    viewModel: IdPhotoViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCamera by remember { mutableStateOf(false) }

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let(viewModel::onImageSelected) }

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
        context.startActivity(Intent.createChooser(viewModel.shareIntent(file), "分享证件照"))
        viewModel.markExportHandled()
    }

    if (showCamera) {
        CameraScreen(
            torchEnabled = false,
            useFrontCamera = uiState.useFrontCamera,
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
                title = { Text("证件照生成") },
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
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("拍摄方式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = uiState.useFrontCamera,
                                onClick = { viewModel.setUseFrontCamera(true) },
                                label = { Text("自拍") },
                                leadingIcon = { Icon(Icons.Default.Cameraswitch, null, modifier = Modifier.size(18.dp)) }
                            )
                            FilterChip(
                                selected = !uiState.useFrontCamera,
                                onClick = { viewModel.setUseFrontCamera(false) },
                                label = { Text("拍别人") },
                                leadingIcon = { Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(18.dp)) }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("拍照")
                            }
                            FilledTonalButton(
                                onClick = { pickImageLauncher.launch("image/*") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("导入")
                            }
                        }
                    }
                }
            }

            item {
                IdPhotoOptionsCard(uiState, viewModel)
            }

            item {
                PreviewCard(uiState)
            }

            item {
                Button(
                    onClick = viewModel::exportPhoto,
                    enabled = uiState.resultBitmap != null && !uiState.isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Save, null)
                    Spacer(Modifier.width(8.dp))
                    Text("导出并分享证件照")
                }
            }
        }
    }
}

@Composable
private fun IdPhotoOptionsCard(
    uiState: IdPhotoUiState,
    viewModel: IdPhotoViewModel
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("证件照参数", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IdPhotoSpec.values().forEach { spec ->
                    FilterChip(
                        selected = uiState.spec == spec,
                        onClick = { viewModel.setSpec(spec) },
                        label = { Text(spec.label) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IdPhotoBackground.values().forEach { bg ->
                    FilterChip(
                        selected = uiState.background == bg,
                        onClick = { viewModel.setBackground(bg) },
                        label = { Text(bg.label) }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("换成西装效果")
                Switch(checked = uiState.suitEnabled, onCheckedChange = viewModel::setSuitEnabled)
            }
        }
    }
}

@Composable
private fun PreviewCard(uiState: IdPhotoUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("预览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            val bitmap = uiState.resultBitmap ?: uiState.sourceBitmap
            if (bitmap == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("请先拍照或导入照片", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "证件照预览",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}
