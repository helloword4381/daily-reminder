package com.dailyreminder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dailyreminder.SettingsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: SettingsManager) {
    var morningRem by remember { mutableStateOf(settings.morningReminder) }
    var morningH by remember { mutableStateOf(settings.morningHour) }
    var morningM by remember { mutableStateOf(settings.morningMinute) }
    var afternoonRem by remember { mutableStateOf(settings.afternoonReminder) }
    var afternoonH by remember { mutableStateOf(settings.afternoonHour) }
    var afternoonM by remember { mutableStateOf(settings.afternoonMinute) }
    var screenRem by remember { mutableStateOf(settings.screenOnReminder) }

    // 时间选择器弹出
    var showMorningPicker by remember { mutableStateOf(false) }
    var showAfternoonPicker by remember { mutableStateOf(false) }
    var pickerHour by remember { mutableIntStateOf(8) }
    var pickerMinute by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 提醒设置标题
            Text("提醒设置", style = MaterialTheme.typography.titleLarge)
            Text(
                "工作时间：8:00-12:00，14:00-18:00",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            // 早上提醒
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("早上提醒", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${String.format("%02d", morningH)}:${String.format("%02d", morningM)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row {
                        TextButton(onClick = {
                            pickerHour = morningH
                            pickerMinute = morningM
                            showMorningPicker = true
                        }) {
                            Icon(Icons.Default.Schedule, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("修改时间")
                        }
                        Switch(
                            checked = morningRem,
                            onCheckedChange = {
                                morningRem = it
                                settings.morningReminder = it
                            }
                        )
                    }
                }
            }

            // 下午提醒
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("下午提醒", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${String.format("%02d", afternoonH)}:${String.format("%02d", afternoonM)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row {
                        TextButton(onClick = {
                            pickerHour = afternoonH
                            pickerMinute = afternoonM
                            showAfternoonPicker = true
                        }) {
                            Icon(Icons.Default.Schedule, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("修改时间")
                        }
                        Switch(
                            checked = afternoonRem,
                            onCheckedChange = {
                                afternoonRem = it
                                settings.afternoonReminder = it
                            }
                        )
                    }
                }
            }

            // 屏幕亮起提醒
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("屏幕亮起提醒", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "工作时间打开屏幕时提醒未完成任务",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = screenRem,
                        onCheckedChange = {
                            screenRem = it
                            settings.screenOnReminder = it
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 应用信息
            Text("关于", style = MaterialTheme.typography.titleLarge)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("日常提醒 v1.0", style = MaterialTheme.typography.bodyMedium)
                    Text("数据全部保存在本地", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    // 早上时间选择器
    if (showMorningPicker) {
        AlertDialog(
            onDismissRequest = { showMorningPicker = false },
            title = { Text("设置早上提醒时间") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    timePicker@
                    Column {
                        Text("时")
                        for (h in (0..23).toList().chunked(12)) {
                            Row {
                                h.forEach { hr ->
                                    FilterChip(
                                        selected = pickerHour == hr,
                                        onClick = { pickerHour = hr },
                                        label = { Text("${String.format("%02d", hr)}") },
                                        modifier = Modifier.padding(2.dp)
                                    )
                                }
                            }
                        }
                    }
                    Column {
                        Text("分")
                        for (m in listOf(0, 15, 30, 45)) {
                            FilterChip(
                                selected = pickerMinute == m,
                                onClick = { pickerMinute = m },
                                label = { Text("${String.format("%02d", m)}") },
                                modifier = Modifier.padding(2.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    settings.morningHour = pickerHour
                    settings.morningMinute = pickerMinute
                    morningH = pickerHour
                    morningM = pickerMinute
                    showMorningPicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showMorningPicker = false }) { Text("取消") }
            }
        )
    }

    // 下午时间选择器（复用逻辑）
    if (showAfternoonPicker) {
        AlertDialog(
            onDismissRequest = { showAfternoonPicker = false },
            title = { Text("设置下午提醒时间") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("时")
                        for (h in (0..23).toList().chunked(12)) {
                            Row {
                                h.forEach { hr ->
                                    FilterChip(
                                        selected = pickerHour == hr,
                                        onClick = { pickerHour = hr },
                                        label = { Text("${String.format("%02d", hr)}") }
                                    )
                                }
                            }
                        }
                    }
                    Column {
                        Text("分")
                        for (m in listOf(0, 15, 30, 45)) {
                            FilterChip(
                                selected = pickerMinute == m,
                                onClick = { pickerMinute = m },
                                label = { Text("${String.format("%02d", m)}") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    settings.afternoonHour = pickerHour
                    settings.afternoonMinute = pickerMinute
                    afternoonH = pickerHour
                    afternoonM = pickerMinute
                    showAfternoonPicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showAfternoonPicker = false }) { Text("取消") }
            }
        )
    }
}
