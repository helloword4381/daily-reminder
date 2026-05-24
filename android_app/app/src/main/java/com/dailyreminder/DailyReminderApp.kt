package com.dailyreminder

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dailyreminder.ui.screens.*

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Home : Screen("home", "今日任务", Icons.Default.Home)
    data object History : Screen("history", "历史记录", Icons.Default.DateRange)
    data object Diary : Screen("diary", "工作日记", Icons.Default.Description)
    data object Toolbox : Screen("toolbox", "工具箱", Icons.Default.Build)
    data object Settings : Screen("settings", "设置", Icons.Default.Settings)
}

val screens = listOf(Screen.Home, Screen.History, Screen.Diary, Screen.Toolbox, Screen.Settings)

@OptIn(ExperimentalMaterial3Api::class)
// DailyReminderApp 已替换为 AppEntry（使用 Scaffold + BottomBar）
@Composable
fun DailyReminderApp(viewModel: MainViewModel = viewModel()) {
    val navController = rememberNavController()
    val allTasks by viewModel.allTasks.collectAsState()
    val todayPending by viewModel.todayPending.collectAsState()
    val diaryEntries by viewModel.diaryEntries.collectAsState()
    val towerRecords by viewModel.towerRecords.collectAsState()

    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        screens.forEach { screen ->
            NavigationBarItem(
                icon = { Icon(screen.icon, contentDescription = screen.title) },
                label = { Text(screen.title) },
                selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                tasks = todayPending,
                onAdd = { viewModel.addTask(it) },
                onToggleDone = { id, done -> viewModel.toggleDone(id, done) },
                onDelete = { viewModel.deleteTask(it) }
            )
        }
        composable(Screen.History.route) {
            HistoryScreen(
                tasks = allTasks,
                onToggleDone = { id, done -> viewModel.toggleDone(id, done) }
            )
        }
        composable(Screen.Diary.route) {
            WorkDiaryScreen(
                entries = diaryEntries,
                onAdd = { date, title, content -> viewModel.addDiary(date, title, content) },
                onUpdate = { viewModel.updateDiary(it) },
                onDelete = { viewModel.deleteDiary(it) }
            )
        }
        composable(Screen.Toolbox.route) {
            ToolboxScreen(
                records = towerRecords,
                onSave = { viewModel.saveTowerRecord(it) },
                onDelete = { viewModel.deleteTowerRecord(it) },
                onShare = { records -> viewModel.shareTowerRecords(records) }
            )
        }
        composable(Screen.Settings.route) {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val version = remember {
                try {
                    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
                } catch (_: Exception) { "" }
            }
            SettingsScreen(
                settings = viewModel.settings,
                currentVersion = version,
                onCheckUpdate = { viewModel.checkForUpdate() },
                updateInfo = updateInfo,
                updateState = updateState,
                onDownloadUpdate = { viewModel.downloadApk() },
                onDismissUpdate = { viewModel.cancelUpdate() }
            )
        }
    }
}

// 注意：底部的 NavigationBar 放入 Scaffold 由调用方处理，
// 这里改成由 DailyReminderApp 整体包裹
@Composable
fun AppEntry(viewModel: MainViewModel = viewModel()) {
    val navController = rememberNavController()
    val allTasks by viewModel.allTasks.collectAsState()
    val todayPending by viewModel.todayPending.collectAsState()
    val diaryEntries by viewModel.diaryEntries.collectAsState()
    val towerRecords by viewModel.towerRecords.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val toastMsg by viewModel.toastMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 启动时检查更新 + 检查待安装 APK
    LaunchedEffect(Unit) {
        viewModel.checkForUpdate()
        viewModel.checkPendingInstall()
    }

    // Toast 消息
    LaunchedEffect(toastMsg) {
        if (toastMsg.isNotBlank()) {
            snackbarHostState.showSnackbar(toastMsg, duration = SnackbarDuration.Short)
        }
    }

    // 更新弹窗 - 检查中
    if (updateState == com.dailyreminder.MainViewModel.UpdateState.CHECKING) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("检查更新") },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("正在检查最新版本...")
                }
            },
            confirmButton = { },
            dismissButton = { }
        )
    }

    // 更新弹窗 - 下载完成
    if (updateState == com.dailyreminder.MainViewModel.UpdateState.DOWNLOADED) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelUpdate() },
            title = { Text("下载完成") },
            text = {
                Column {
                    Text("新版本 v${updateInfo.versionName} 已准备好")
                    Spacer(Modifier.height(4.dp))
                    Divider()
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TextButton(onClick = { viewModel.cancelUpdate() }) { Text("取消") }
                        TextButton(onClick = { viewModel.postponeUpdate() }) { Text("稍后安装") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.installApk() }) { Text("立即安装") }
            },
            dismissButton = { }
        )
    }

    // 更新弹窗 - 下载进度
    if (updateState == com.dailyreminder.MainViewModel.UpdateState.DOWNLOADING) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("正在下载...") },
            text = {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    LinearProgressIndicator(
                        progress = { downloadProgress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("$downloadProgress%")
                }
            },
            confirmButton = { },
            dismissButton = { }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    tasks = todayPending,
                    onAdd = { viewModel.addTask(it) },
                    onToggleDone = { id, done -> viewModel.toggleDone(id, done) },
                    onDelete = { viewModel.deleteTask(it) }
                )
            }
            composable(Screen.History.route) {
                HistoryScreen(
                    tasks = allTasks,
                    onToggleDone = { id, done -> viewModel.toggleDone(id, done) }
                )
            }
            composable(Screen.Diary.route) {
                WorkDiaryScreen(
                    entries = diaryEntries,
                    onAdd = { date, title, content -> viewModel.addDiary(date, title, content) },
                    onUpdate = { viewModel.updateDiary(it) },
                    onDelete = { viewModel.deleteDiary(it) }
                )
            }
            composable(Screen.Toolbox.route) {
                ToolboxScreen(
                    records = towerRecords,
                    onSave = { viewModel.saveTowerRecord(it) },
                    onDelete = { viewModel.deleteTowerRecord(it) },
                    onShare = { records -> viewModel.shareTowerRecords(records) }
                )
            }
            composable(Screen.Settings.route) {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val version = remember {
                    try {
                        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
                    } catch (_: Exception) { "" }
                }
                SettingsScreen(
                    settings = viewModel.settings,
                    currentVersion = version,
                    onCheckUpdate = { viewModel.checkForUpdate() }
                )
            }
        }
    }
}
