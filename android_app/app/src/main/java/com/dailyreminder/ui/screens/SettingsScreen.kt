package com.dailyreminder.ui.screens

import androidx.compose.foundation.*
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
fun SettingsScreen(
    settings: SettingsManager,
    currentVersion: String = "",
    onCheckUpdate: () -> Unit = {}
) {
    var morningRem by remember { mutableStateOf(settings.morningReminder) }
    var morningH by remember { mutableStateOf(settings.morningHour) }
    var morningM by remember { mutableStateOf(settings.morningMinute) }
    var afternoonRem by remember { mutableStateOf(settings.afternoonReminder) }
    var afternoonH by remember { mutableStateOf(settings.afternoonHour) }
    var afternoonM by remember { mutableStateOf(settings.afternoonMinute) }
    var screenRem by remember { mutableStateOf(settings.screenOnReminder) }

    // 获取上下文
    val ctx = androidx.compose.ui.platform.LocalContext.current

    // 时间选择器弹出
    var showMorningPicker by remember { mutableStateOf(false) }
    var showAfternoonPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
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

            // 后台优化（适配所有系统）
            Text("后台优化", style = MaterialTheme.typography.titleLarge)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "部分系统会自动杀死后台服务导致提醒失效。请按以下步骤设置：",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))

                    // 按钮1：关闭电池优化
                    OutlinedButton(
                        onClick = {
                            val intent = android.content.Intent(
                                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                android.net.Uri.parse("package:${ctx.packageName}")
                            ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                            try { ctx.startActivity(intent) } catch (_: Exception) { }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(androidx.compose.material.icons.Icons.Default.BatteryStd, null)
                        Spacer(Modifier.width(8.dp))
                        Text("① 关闭电池优化")
                    }
                    Spacer(Modifier.height(8.dp))

                    // 按钮2：打开应用系统设置页（自启动/后台权限）
                    OutlinedButton(
                        onClick = {
                            val intent = android.content.Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.parse("package:${ctx.packageName}")
                            ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                            try { ctx.startActivity(intent) } catch (_: Exception) { }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(androidx.compose.material.icons.Icons.Default.Settings, null)
                        Spacer(Modifier.width(8.dp))
                        Text("② 打开应用系统设置")
                    }
                    Spacer(Modifier.height(12.dp))

                    // 各品牌设置路径
                    Text("各品牌详细路径：", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        buildString {
                            appendLine("📱 Realme / OPPO：设置 → 应用 → 日常助手 → 耗电管理")
                            appendLine("    → 允许自启动 / 允许后台运行 / 关闭电池优化")
                            appendLine("📱 小米 / Redmi：设置 → 应用 → 应用管理 → 日常助手")
                            appendLine("    → 省电策略 → 无限制 / 自启动 → 允许")
                            appendLine("📱 华为 / 荣耀：设置 → 应用 → 应用启动管理 → 日常助手")
                            appendLine("    → 关闭「自动管理」→ 允许自启/关联启动/后台活动")
                            appendLine("📱 vivo / iQOO：设置 → 应用与权限 → 应用管理 → 日常助手")
                            appendLine("    → 耗电管理 → 后台高耗电允许 / 自启动 → 允许")
                            appendLine("📱 三星：设置 → 电池 → 后台使用限制 → 未使用的应用 → 关闭")
                            appendLine("    设置 → 应用 → 日常助手 → 电池 → 不受限制")
                        }.trimEnd(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 应用信息
            Text("关于", style = MaterialTheme.typography.titleLarge)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("日常助手", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (currentVersion.isNotBlank()) "v$currentVersion" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("数据全部保存在本地",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))

                    // 按钮行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCheckUpdate,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("检查更新")
                        }
                        OutlinedButton(
                            onClick = {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://github.com/helloword4381/daily-reminder/releases")
                                ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                                try { ctx.startActivity(intent) } catch (_: Exception) { }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Info, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("更新日志")
                        }
                    }
                }
            }
        }
    }

    // 早上时间选择器（Material3 滑动式）
    if (showMorningPicker) {
        val state = rememberTimePickerState(
            initialHour = morningH,
            initialMinute = morningM,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showMorningPicker = false },
            title = { Text("设置早上提醒时间") },
            text = {
                TimePicker(state = state)
            },
            confirmButton = {
                TextButton(onClick = {
                    settings.morningHour = state.hour
                    settings.morningMinute = state.minute
                    morningH = state.hour
                    morningM = state.minute
                    showMorningPicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showMorningPicker = false }) { Text("取消") }
            }
        )
    }

    // 下午时间选择器（Material3 滑动式）
    if (showAfternoonPicker) {
        val state = rememberTimePickerState(
            initialHour = afternoonH,
            initialMinute = afternoonM,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showAfternoonPicker = false },
            title = { Text("设置下午提醒时间") },
            text = {
                TimePicker(state = state)
            },
            confirmButton = {
                TextButton(onClick = {
                    settings.afternoonHour = state.hour
                    settings.afternoonMinute = state.minute
                    afternoonH = state.hour
                    afternoonM = state.minute
                    showAfternoonPicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showAfternoonPicker = false }) { Text("取消") }
            }
        )
    }
}
