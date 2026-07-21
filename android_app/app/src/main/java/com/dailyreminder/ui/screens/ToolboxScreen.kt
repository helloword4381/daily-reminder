package com.dailyreminder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
// roundToInt not used

// 将 DD.MMSSss 格式（如 35.25156 = 35°25'15.6"）转换为总秒数
private fun dmsToSeconds(value: Double): Double {
    val deg = value.toInt()
    val frac = abs(value - deg) * 100.0
    val min = frac.toInt()
    val sec = (frac - min) * 100.0
    return deg * 3600.0 + min * 60.0 + sec
}

@OptIn(ExperimentalMaterial3Api::class)
val STANDING_POSITIONS = listOf("小里程", "大里程", "左幅", "右幅")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ToolboxScreen(
    records: List<TowerCalcEntity>,
    onSave: (TowerCalcEntity) -> Unit,
    onDelete: (String) -> Unit,
    onShare: ((List<TowerCalcEntity>) -> Unit)? = null,
    onOpenDocumentScan: () -> Unit = {},
    onOpenIdPhoto: () -> Unit = {}
) {
    var page by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (page == 0) "工具箱" else "墩柱（扣塔）偏位计算器") },
                navigationIcon = {
                    if (page > 0) {
                        IconButton(onClick = { page = 0 }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (page == 0) {
                ToolboxHome(
                    onOpenTowerCalc = { page = 1; tab = 0 },
                    onOpenDocumentScan = onOpenDocumentScan,
                    onOpenIdPhoto = onOpenIdPhoto
                )
            } else {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 },
                        text = { Text("计算") },
                        icon = { Icon(Icons.Default.Calculate, null) })
                    Tab(selected = tab == 1, onClick = { tab = 1 },
                        text = { Text("记录 (${records.size})") },
                        icon = { Icon(Icons.Default.List, null) })
                }
                when (tab) {
                    0 -> TowerCalcTab(records = records, onSave = onSave)
                    1 -> TowerRecordsTab(records = records, onDelete = onDelete, onShare = onShare)
                }
            }
        }
    }
}

@Composable
fun ToolboxHome(
    onOpenTowerCalc: () -> Unit,
    onOpenDocumentScan: () -> Unit,
    onOpenIdPhoto: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("选择工具", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))

        // 扣塔计算工具
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenTowerCalc
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Calculate,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("墩柱（扣塔）偏位计算", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "偏位 + 方位角分析",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenDocumentScan
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("文档扫描", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "拍照 / 相册导入、OCR、导出分享",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenIdPhoto
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("证件照生成", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "1 寸 / 2 寸、背景色、自拍或拍别人",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 未来可在此添加更多工具...
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.MoreHoriz,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("更多工具即将上线", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("敬请期待",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// 根据站位计算偏位方向 + 幅度
private fun calcDirections(
    standingPosition: String,
    d1: Double, d2: Double,  // 距顶/底平距
    d3: Double, d4: Double   // 方位角
): CalcResult {
    val angleDeg = ((dmsToSeconds(d3) - dmsToSeconds(d4)) / 3600.0)
    val angleRad = angleDeg * kotlin.math.PI / 180.0

    // 两种幅度公式
    val distDiff = abs(d1 - d2) * 1000.0                                    // 前后偏幅度(里程类) / 左右偏幅度(幅类)
    val angleOffset = abs((d1 + d2) / 2.0 * tan(angleRad) * 1000.0)          // 左右偏幅度(里程类) / 前后偏幅度(幅类)

    val isMileage = standingPosition in listOf("小里程", "大里程")
    val fbMm = if (isMileage) distDiff else angleOffset
    val lrMm = if (isMileage) angleOffset else distDiff

    val (lrDir, fbDir) = when (standingPosition) {
        "小里程" -> {
            val l = if (d3 > d4) "向右幅" else "向左幅"
            val f = if (d1 > d2) "往大里程" else "往小里程"
            l to f
        }
        "大里程" -> {
            val l = if (d3 > d4) "向左幅" else "向右幅"
            val f = if (d1 > d2) "往小里程" else "往大里程"
            l to f
        }
        "左幅" -> {
            val l = if (d1 > d2) "向右幅" else "向左幅"
            val f = if (d3 > d4) "往小里程" else "往大里程"
            l to f
        }
        else -> { // 右幅
            val l = if (d1 > d2) "向左幅" else "向右幅"
            val f = if (d3 > d4) "往大里程" else "往小里程"
            l to f
        }
    }

    return CalcResult(
        resultLR = "${lrDir}偏位 ${"%.1f".format(lrMm)} mm",
        resultFB = "${fbDir}偏位 ${"%.1f".format(fbMm)} mm"
    )
}

private data class CalcResult(
    val resultLR: String,
    val resultFB: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TowerCalcTab(
    records: List<TowerCalcEntity>,
    onSave: (TowerCalcEntity) -> Unit
) {
    var standingPosition by remember { mutableStateOf(STANDING_POSITIONS[0]) }
    var number by remember { mutableStateOf("") }
    var data1 by remember { mutableStateOf("") }
    var data2 by remember { mutableStateOf("") }
    var data3 by remember { mutableStateOf("") }
    var data4 by remember { mutableStateOf("") }

    var resultLR by remember { mutableStateOf("") }
    var resultFB by remember { mutableStateOf("") }
    var calculated by remember { mutableStateOf(false) }
    var lastEntity by remember { mutableStateOf<TowerCalcEntity?>(null) }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(calculated) {
        if (calculated) listState.animateScrollToItem(10)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ===== 站位选择 =====
        item {
            Text("当前站位方向", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                STANDING_POSITIONS.forEach { pos ->
                    FilterChip(
                        selected = standingPosition == pos,
                        onClick = { standingPosition = pos },
                        label = { Text(pos, style = MaterialTheme.typography.bodyMedium) }
                    )
                }
            }
        }

        // ===== 墩柱编号 =====
        item {
            OutlinedTextField(
                value = number,
                onValueChange = { number = it },
                label = { Text("墩柱（扣塔）编号") },
                placeholder = { Text("输入编号") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Tag, null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
            )
        }

        // ===== 输入数据标题 =====
        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text("测量数据（单位：米）", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary)
        }

        item {
            OutlinedTextField(
                value = data1, onValueChange = { data1 = it },
                label = { Text("墩柱顶平距") },
                placeholder = { Text("距墩柱顶的水平距离") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Straighten, null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
            )
        }
        item {
            OutlinedTextField(
                value = data2, onValueChange = { data2 = it },
                label = { Text("墩柱底平距") },
                placeholder = { Text("距墩柱底的水平距离") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Straighten, null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
            )
        }
        item {
            OutlinedTextField(
                value = data3, onValueChange = { data3 = it },
                label = { Text("墩柱顶方位角") },
                placeholder = { Text("DD.MMSSss 格式") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Explore, null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
            )
        }
        item {
            OutlinedTextField(
                value = data4, onValueChange = { data4 = it },
                label = { Text("墩柱底方位角") },
                placeholder = { Text("DD.MMSSss 格式") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Explore, null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
            )
        }

        // ===== 计算按钮 =====
        item {
            Button(
                onClick = {
                    val d1 = data1.toDoubleOrNull() ?: return@Button
                    val d2 = data2.toDoubleOrNull() ?: return@Button
                    val d3 = data3.toDoubleOrNull() ?: return@Button
                    val d4 = data4.toDoubleOrNull() ?: return@Button

                    val res = calcDirections(standingPosition, d1, d2, d3, d4)
                    resultLR = res.resultLR
                    resultFB = res.resultFB

                    calculated = true
                    lastEntity = TowerCalcEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        standingPosition = standingPosition,
                        number = number,
                        data1 = d1, data2 = d2, data3 = d3, data4 = d4,
                        resultLeftRight = resultLR,
                        resultForwardBack = resultFB,
                        createdAt = com.dailyreminder.data.model.TowerCalc.now()
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.Calculate, null)
                Spacer(Modifier.width(8.dp))
                Text("计算偏位", style = MaterialTheme.typography.titleSmall)
            }
        }

        // ===== 结果卡片 =====
        if (calculated) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("计算结果", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ArrowForward, null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(resultFB, style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider()
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.SwapHoriz, null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(resultLR, style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            // ===== 保存按钮 =====
            item {
                OutlinedButton(
                    onClick = {
                        val entity = lastEntity ?: return@OutlinedButton
                        onSave(entity)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
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
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    // 删除确认弹窗
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除这条记录吗？删除后无法恢复。") },
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
                                Column {
                                    Text("编号: ${r.number}", fontWeight = FontWeight.Bold)
                                    Text("站位: ${r.standingPosition}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary)
                                }
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
                                TextButton(onClick = { deleteTarget = r.id }) {
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
