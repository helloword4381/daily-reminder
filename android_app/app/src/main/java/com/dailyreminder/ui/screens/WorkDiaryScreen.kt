package com.dailyreminder.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dailyreminder.data.db.WorkDiaryEntity
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkDiaryScreen(
    entries: List<WorkDiaryEntity>,
    onAdd: (date: String, title: String, content: String, imagePath: String) -> Unit,
    onUpdate: (WorkDiaryEntity) -> Unit,
    onDelete: (WorkDiaryEntity) -> Unit
) {
    var showInputPage by remember { mutableStateOf(false) }
    var editEntry by remember { mutableStateOf<WorkDiaryEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<WorkDiaryEntity?>(null) }
    var previewImagePath by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("工作日记") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editEntry = null
                showInputPage = true
            }) { Icon(Icons.Default.Add, contentDescription = "写日记") }
        }
    ) { padding ->
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("点击 + 写第一篇日记", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    DiaryCard(
                        entry = entry,
                        onEdit = {
                            editEntry = entry
                            showInputPage = true
                        },
                        onDelete = { deleteTarget = entry },
                        onImageClick = { path -> previewImagePath = path }
                    )
                }
            }
        }
    }

    // 删除确认对话框
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除「${deleteTarget!!.title}」吗？删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(deleteTarget!!)
                    deleteTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }

    // 图片预览 → 打开系统相册查看
    if (previewImagePath != null) {
        val ctx = LocalContext.current
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse(previewImagePath)
                flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra("android.intent.extra.REFERRER", android.net.Uri.parse("android-app://${ctx.packageName}"))
            }
            ctx.startActivity(intent)
        } catch (_: Exception) { }
        previewImagePath = null
    }

    // 完整的输入页面
    if (showInputPage) {
        DiaryInputPage(
            editEntry = editEntry,
            onSave = { date, title, content, imagePath ->
                if (editEntry != null) {
                    onUpdate(editEntry!!.copy(
                        date = date, title = title, content = content,
                        imagePath = imagePath,
                        updatedAt = com.dailyreminder.data.model.WorkDiary.now()
                    ))
                } else {
                    onAdd(date, title, content, imagePath)
                }
                showInputPage = false
            },
            onCancel = { showInputPage = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryInputPage(
    editEntry: WorkDiaryEntity?,
    onSave: (date: String, title: String, content: String, imagePath: String) -> Unit,
    onCancel: () -> Unit
) {
    val ctx = LocalContext.current
    var date by remember { mutableStateOf(editEntry?.date ?: com.dailyreminder.data.model.WorkDiary.today()) }
    var title by remember { mutableStateOf(editEntry?.title ?: "") }
    var content by remember { mutableStateOf(editEntry?.content ?: "") }
    var imagePath by remember { mutableStateOf(editEntry?.imagePath ?: "") }

    // 图片选择器（用 ACTION_PICK 兼容性最好）
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (uri != null) {
            imagePath = uri.toString()
        }
    }

    // 图片读取权限
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val intent = android.content.Intent(android.content.Intent.ACTION_PICK).apply {
                type = "image/*"
            }
            imagePickerLauncher.launch(intent)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editEntry == null) "写日记" else "编辑日记") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        if (title.isNotBlank()) {
                            onSave(date, title, content, imagePath)
                        }
                    }) { Text("保存") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = date,
                onValueChange = { date = it },
                label = { Text("日期") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("标题") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("内容") },
                minLines = 6,
                maxLines = 20,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            // 图片选择区域
            HorizontalDivider()
            Text("附件图片", style = MaterialTheme.typography.titleSmall)

            if (imagePath.isNotBlank()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Image, null,
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("已选择一张图片", modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { imagePath = "" }) { Text("移除") }
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    val permission = if (android.os.Build.VERSION.SDK_INT >= 33)
                        android.Manifest.permission.READ_MEDIA_IMAGES
                    else
                        android.Manifest.permission.READ_EXTERNAL_STORAGE

                    if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, permission)
                        == android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        val intent = android.content.Intent(android.content.Intent.ACTION_PICK).apply {
                            type = "image/*"
                        }
                        imagePickerLauncher.launch(intent)
                    } else {
                        permissionLauncher.launch(permission)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Image, null)
                Spacer(Modifier.width(8.dp))
                Text("从相册选择图片")
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun DiaryCard(
    entry: WorkDiaryEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onImageClick: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    entry.createdAt.take(16).replace("T", " "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑",
                            modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "删除",
                            modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (entry.title.isNotBlank()) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
            }
            Text(entry.content, style = MaterialTheme.typography.bodyMedium)

            // 图片显示
            if (entry.imagePath.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 150.dp)
                        .clickable { onImageClick(entry.imagePath) },
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("点击预览图片",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}