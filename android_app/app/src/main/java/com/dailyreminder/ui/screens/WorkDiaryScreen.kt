package com.dailyreminder.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dailyreminder.data.db.WorkDiaryEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkDiaryScreen(
    entries: List<WorkDiaryEntity>,
    onAdd: (date: String, title: String, content: String) -> Unit,
    onUpdate: (WorkDiaryEntity) -> Unit,
    onDelete: (WorkDiaryEntity) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var editEntry by remember { mutableStateOf<WorkDiaryEntity?>(null) }
    var editDate by remember { mutableStateOf("") }
    var editTitle by remember { mutableStateOf("") }
    var editContent by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("工作日记") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editEntry = null
                editDate = com.dailyreminder.data.model.WorkDiary.today()
                editTitle = ""
                editContent = ""
                showDialog = true
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
                            editDate = entry.date
                            editTitle = entry.title
                            editContent = entry.content
                            showDialog = true
                        },
                        onDelete = { onDelete(entry) }
                    )
                }
            }
        }
    }

    // 新增/编辑 对话框
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (editEntry == null) "写日记" else "编辑日记") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editDate,
                        onValueChange = { editDate = it },
                        label = { Text("日期") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("标题") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editContent,
                        onValueChange = { editContent = it },
                        label = { Text("内容") },
                        minLines = 3,
                        maxLines = 8
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editTitle.isNotBlank()) {
                        if (editEntry != null) {
                            onUpdate(editEntry!!.copy(
                                date = editDate, title = editTitle, content = editContent,
                                updatedAt = com.dailyreminder.data.model.WorkDiary.now()
                            ))
                        } else {
                            onAdd(editDate, editTitle, editContent)
                        }
                        showDialog = false
                    }
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
fun DiaryCard(
    entry: WorkDiaryEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
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
        }
    }
}
