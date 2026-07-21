package com.dailyreminder

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dailyreminder.ui.theme.DailyReminderTheme
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
import com.dailyreminder.ui.screens.idphoto.IdPhotoScreen
import com.dailyreminder.ui.screens.scan.ScanMainScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Home : Screen("home", "待办任务", Icons.Default.Home)
    data object History : Screen("history", "历史记录", Icons.Default.DateRange)
    data object Diary : Screen("diary", "工作日记", Icons.Default.Description)
    data object Toolbox : Screen("toolbox", "工具箱", Icons.Default.Build)
    data object Settings : Screen("settings", "设置", Icons.Default.Settings)
    data object Scan : Screen("scan", "文档扫描", Icons.Default.CameraAlt)
    data object IdPhoto : Screen("id_photo", "证件照", Icons.Default.Person)
}

val screens = listOf(Screen.Home, Screen.History, Screen.Diary, Screen.Toolbox, Screen.Settings)

// 注意：底部的 NavigationBar 放入 Scaffold 由调用方处理
@Composable
fun AppEntry(viewModel: MainViewModel = viewModel()) {
    val navController = rememberNavController()
    val allTasks by viewModel.allTasks.collectAsState()
    val pendingTasks by viewModel.pendingTasks.collectAsState()
    val diaryEntries by viewModel.diaryEntries.collectAsState()
    val towerRecords by viewModel.towerRecords.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val isDark = when (themeMode) {
        1 -> false
        2 -> true
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val updateInfo by viewModel.updateInfo.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val toastMsg by viewModel.toastMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 启动时静默检查更新 + 检查待安装 APK
    LaunchedEffect(Unit) {
        viewModel.silentCheckForUpdate()
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

    DailyReminderTheme(darkTheme = isDark) {
        AppContent(
            navController = navController,
            viewModel = viewModel,
            themeMode = themeMode,
            allTasks = allTasks,
            pendingTasks = pendingTasks,
            diaryEntries = diaryEntries,
            towerRecords = towerRecords,
            updateInfo = updateInfo,
            updateState = updateState,
            downloadProgress = downloadProgress,
            toastMsg = toastMsg,
            snackbarHostState = snackbarHostState
        )
    }
}

@Composable
private fun AppContent(
    navController: androidx.navigation.NavHostController,
    viewModel: MainViewModel,
    themeMode: Int,
    allTasks: List<com.dailyreminder.data.db.TaskEntity>,
    pendingTasks: List<com.dailyreminder.data.db.TaskEntity>,
    diaryEntries: List<com.dailyreminder.data.db.WorkDiaryEntity>,
    towerRecords: List<com.dailyreminder.data.db.TowerCalcEntity>,
    updateInfo: com.dailyreminder.MainViewModel.UpdateInfo,
    updateState: com.dailyreminder.MainViewModel.UpdateState,
    downloadProgress: Int,
    toastMsg: String,
    snackbarHostState: SnackbarHostState
) {
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
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    tasks = pendingTasks,
                    onAdd = { content, priority -> viewModel.addTask(content, priority) },
                    onToggleDone = { id, done -> viewModel.toggleDone(id, done) },
                    onDelete = { viewModel.deleteTask(it) }
                )
            }
            composable(Screen.History.route) {
                HistoryScreen(
                    tasks = allTasks,
                    onToggleDone = { id, done -> viewModel.toggleDone(id, done) },
                    onDelete = { viewModel.deleteTask(it) }
                )
            }
            composable(Screen.Diary.route) {
                WorkDiaryScreen(
                    entries = diaryEntries,
                    onAdd = { date, title, content, imagePath -> viewModel.addDiary(date, title, content, imagePath) },
                    onUpdate = { viewModel.updateDiary(it) },
                    onDelete = { viewModel.deleteDiary(it) }
                )
            }
            composable(Screen.Toolbox.route) {
                ToolboxScreen(
                    records = towerRecords,
                    onSave = { viewModel.saveTowerRecord(it) },
                    onDelete = { viewModel.deleteTowerRecord(it) },
                    onShare = { records -> viewModel.shareTowerRecords(records) },
                    onOpenDocumentScan = { navController.navigate(Screen.Scan.route) },
                    onOpenIdPhoto = { navController.navigate(Screen.IdPhoto.route) }
                )
            }
            composable(Screen.Scan.route) {
                ScanMainScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.IdPhoto.route) {
                IdPhotoScreen(onBack = { navController.popBackStack() })
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
                    themeMode = themeMode,
                    onSetThemeMode = { viewModel.setThemeMode(it) },
                    onCheckUpdate = { viewModel.checkForUpdate() },
                    updateInfo = updateInfo,
                    updateState = updateState,
                    onDownloadUpdate = { viewModel.downloadApk() },
                    onDismissUpdate = { viewModel.cancelUpdate() },
                    onOpenBrowser = { viewModel.openDownloadInBrowser() }
                )
            }
        }
    }
}
