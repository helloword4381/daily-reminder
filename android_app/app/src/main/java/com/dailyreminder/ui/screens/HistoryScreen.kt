package com.dailyreminder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.dailyreminder.data.db.TaskEntity
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    tasks: List<TaskEntity>,
    onToggleDone: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit
) {
    var currentDate by remember {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
    }

    val dateDisplay = remember(currentDate) {
        val parts = currentDate.split("-")
        "${parts[0]}年${parts[1].toInt()}月${parts[2].toInt()}日"
    }

    var deleteTarget by remember { mutableStateOf<TaskEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("历史记录") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    val cal = Calendar.getInstance()
                    val parts = currentDate.split("-")
                    cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                    cal.add(Calendar.DAY_OF_MONTH, -1)
                    currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "前一天")
                }
                Text(dateDisplay, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = {
                    val cal = Calendar.getInstance()
                    val parts = currentDate.split("-")
                    cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                    cal.add(Calendar.DAY_OF_MONTH, 1)
                    currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                }) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "后一天")
                }
            }

            Divider()

            if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("这一天没有任务", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tasks, key = { it.id }) { task ->
                        HistoryTaskCard(
                            task = task,
                            onToggleDone = { onToggleDone(task.id, task.done) },
                            onDelete = { deleteTarget = task }
                        )
                    }
                }
            }
        }
    }

    // 删除确认对话框
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除这个任务吗？删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(deleteTarget!!.id)
                    deleteTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
fun HistoryTaskCard(
    task: TaskEntity,
    onToggleDone: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleDone) {
                Icon(
                    if (task.done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (task.done) "已完成" else "未完成",
                    tint = if (task.done) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = task.content,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
                color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = task.createdAt.takeLast(8).take(5),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "删除",
                    modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}