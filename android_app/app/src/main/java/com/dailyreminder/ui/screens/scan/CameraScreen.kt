package com.dailyreminder.ui.screens.scan

import android.os.Environment
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CameraScreen(
    torchEnabled: Boolean,
    useFrontCamera: Boolean = false,
    onImageCaptured: (android.net.Uri) -> Unit,
    onBack: () -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val imageCaptureState = remember { mutableStateOf<ImageCapture?>(null) }
    val cameraState = remember { mutableStateOf<Camera?>(null) }

    LaunchedEffect(previewView, useFrontCamera) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                val selector = if (useFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                runCatching {
                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        preview,
                        imageCapture
                    )
                    imageCaptureState.value = imageCapture
                    cameraState.value = camera
                    camera.cameraControl.enableTorch(torchEnabled)
                }.onFailure { error ->
                    onError("相机启动失败：${error.message.orEmpty()}")
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    LaunchedEffect(torchEnabled) {
        cameraState.value?.cameraControl?.enableTorch(torchEnabled)
        imageCaptureState.value?.flashMode = if (torchEnabled) {
            ImageCapture.FLASH_MODE_ON
        } else {
            ImageCapture.FLASH_MODE_OFF
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
            shape = MaterialTheme.shapes.large
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "将文档置于画面中央",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                FilledTonalButton(
                    onClick = {
                        val imageCapture = imageCaptureState.value
                        if (imageCapture == null) {
                            onError("相机尚未准备好")
                            return@FilledTonalButton
                        }
                        val file = createScanPhotoFile(context)
                        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
                        imageCapture.takePicture(
                            outputOptions,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    onImageCaptured(file.toUri())
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    onError("拍照失败：${exception.message.orEmpty()}")
                                }
                            }
                        )
                    }
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("拍照")
                }
            }
        }
    }
}

private fun createScanPhotoFile(context: android.content.Context): File {
    val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "scans")
        .apply { mkdirs() }
    val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
    return File(dir, "scan_$name.jpg")
}
