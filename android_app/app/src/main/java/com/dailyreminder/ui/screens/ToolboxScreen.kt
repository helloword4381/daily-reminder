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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dailyreminder.data.db.TowerCalcEntity
import kotlin.math.abs
import kotlin.math.tan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolboxScreen(
    records: List<TowerCalcEntity>,
    onSave: (TowerCalcEntity) -> Unit,
    onDelete: (String) -> Unit,
    onShare: ((List<TowerCalcEntity>) -> Unit)? = null
) {
    var tab by remember { mutableIntStateOf(0) } // 0=计算 1=记录

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("工具箱") })
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            // Tab row
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 },
                    text = { Text("扣塔计算") },
                    icon = { Icon(Icons.Default.Calculate, null) })
                Tab(selected = tab == 1, onClick = { tab = 1 },
                    text = { Text("计算记录 (${records.size})") },
                    icon = { Icon(Icons.Default.List, null) })
            }

            when (tab) {
                0 -> TowerCalcTab(records = records, onSave = onSave)
                1 -> TowerRecordsTab(records = records, onDelete = onDelete, onShare = onShare)
            }
        }
    }
}

@Composable
fun TowerCalcTab(
    records: List<TowerCalcEntity>,
    onSave: (TowerCalcEntity) -> Unit
) {
    var number by remember { mutableStateOf("") }
    var data1 by remember { mutableStateOf("") }
    var data2 by remember { mutableStateOf("") }
    var data3 by remember { mutableStateOf("") }
    var data4 by remember { mutableStateOf("") }

    var resultLR by remember { mutableStateOf("") }
    var resultFB by remember { mutableStateOf("") }
    var calculated by remember { mutableStateOf(false) }
    var lastEntity by remember { mutableStateOf<TowerCalcEntity?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Input fields
        item {
            Text("扣塔编号", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = number,
                onValueChange = { number = it },
                label = { Text("输入编号") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Text("输入数据（单位：米）", style = MaterialTheme.typography.titleMedium)
        }

        item {
            OutlinedTextField(
                value = data1, onValueChange = { data1 = it },
                label = { Text("1. 距扣塔顶平距") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = data2, onValueChange = { data2 = it },
                label = { Text("2. 距扣塔底平距") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = data3, onValueChange = { data3 = it },
                label = { Text("3. 塔顶实测方位角") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = data4, onValueChange = { data4 = it },
                label = { Text("4. 扣塔底实测方位角") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Calculate button
        item {
            Button(
                onClick = {
                    val d1 = data1.toDoubleOrNull() ?: return@Button
                    val d2 = data2.toDoubleOrNull() ?: return@Button
                    val d3 = data3.toDoubleOrNull() ?: return@Button
                    val d4 = data4.toDoubleOrNull() ?: return@Button

                    // 左右偏位
                    val angleDeg = (d3 - d4) / 3600.0
                    val lrRaw = (d1 + d2) / 2.0 * tan(angleDeg * kotlin.math.PI / 180.0)
                    val lrMm = abs(lrRaw * 1000.0)
                    val lrDir = if (d3 > d4) "向右" else "向左"
                    resultLR = "${lrDir}偏位 ${"%.1f".format(lrMm)} mm"

                    // 前后偏位
                    val fbMm = abs(d1 - d2) * 1000.0
                    val fbDir = if (d1 > d2) "向前" else "向后"
                    resultFB = "${fbDir}偏位 ${"%.1f".format(fbMm)} mm"

                    calculated = true
                    lastEntity = TowerCalcEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        number = number,
                        data1 = d1, data2 = d2, data3 = d3, data4 = d4,
                        resultLeftRight = resultLR,
                        resultForwardBack = resultFB,
                        createdAt = com.dailyreminder.data.model.TowerCalc.now()
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Calculate, null)
                Spacer(Modifier.width(8.dp))
                Text("计算")
            }
        }

        // Results
        if (calculated) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("计算结果", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("左右偏位：$resultLR")
                        Text("前后偏位：$resultFB")
                    }
                }
            }

            // Save button
            item {
                Button(
                    onClick = {
                        val entity = lastEntity ?: return@Button
                        onSave(entity)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(Icons.Default.Save, null)
                    Spacer(Modifier.width(8.dp))
                    Text("保存记录")
                }
            }
        }
    }
}

@Composable
fun TowerRecordsTab(
    records: List<TowerCalcEntity>,
    onDelete: (String) -> Unit,
    onShare: ((List<TowerCalcEntity>) -> Unit)? = null
) {
    var selectMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    if (records.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("暂无记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        Column {
            // 操作栏
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("共 ${records.size} 条", style = MaterialTheme.typography.bodySmall)
                Row {
                    if (selectMode) {
                        TextButton(onClick = {
                            onShare?.invoke(records.filter { it.id in selectedIds })
                            selectMode = false
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("分享 (${selectedIds.size})")
                        }
                        TextButton(onClick = {
                            selectMode = false
                            selectedIds = emptySet()
                        }) {
                            Text("取消")
                        }
                    } else {
                        TextButton(onClick = {
                            selectMode = true
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("分享")
                        }
                    }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(records, key = { it.id }) { r ->
                    val isSelected = r.id in selectedIds
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (isSelected) CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ) else CardDefaults.cardColors()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("编号: ${r.number}", fontWeight = FontWeight.Bold)
                                Row {
                                    if (selectMode) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = {
                                                selectedIds = if (it) selectedIds + r.id
                                                else selectedIds - r.id
                                            }
                                        )
                                    }
                                    Text(r.createdAt.take(10),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("顶平距: ${r.data1} m | 底平距: ${r.data2} m")
                            Text("顶方位角: ${r.data3} | 底方位角: ${r.data4}")
                            Divider(modifier = Modifier.padding(vertical = 4.dp))
                            Text(r.resultLeftRight, fontWeight = FontWeight.SemiBold)
                            Text(r.resultForwardBack, fontWeight = FontWeight.SemiBold)
                            if (!selectMode) {
                                TextButton(onClick = { onDelete(r.id) }) {
                                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                                    Spacer(Modifier.width(4.dp))
                                    Text("删除", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
