package com.dailyreminder

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
            SettingsScreen(settings = viewModel.settings)
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
    val checkingUpdate by viewModel.checkingUpdate.collectAsState()

    // 启动时检查更新
    LaunchedEffect(Unit) { viewModel.checkForUpdate() }

    // 更新弹窗
    if (!checkingUpdate && updateInfo.hasUpdate) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("发现新版本") },
            text = {
                Column {
                    Text("${updateInfo.title} (${updateInfo.version})",
                        style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(updateInfo.notes.ifBlank { "请前往 GitHub 下载更新" },
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    // 打开浏览器下载
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(updateInfo.downloadUrl.ifBlank {
                            "https://github.com/helloword4381/daily-reminder/releases"
                        })
                    ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                    viewModel.getApplication<android.app.Application>()
                        .startActivity(intent)
                }) { Text("立即更新") }
            },
            dismissButton = {
                TextButton(onClick = { /* 跳过此版本 */ }) { Text("稍后") }
            }
        )
    }

    Scaffold(
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
                SettingsScreen(settings = viewModel.settings)
            }
        }
    }
}
