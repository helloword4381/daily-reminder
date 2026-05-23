package com.dailyreminder

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
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
import com.dailyreminder.ui.screens.HistoryScreen
import com.dailyreminder.ui.screens.HomeScreen
import com.dailyreminder.ui.screens.SettingsScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Home : Screen("home", "今日任务", Icons.Default.Home)
    data object History : Screen("history", "历史记录", Icons.Default.DateRange)
    data object Settings : Screen("settings", "同步设置", Icons.Default.Settings)
}

val screens = listOf(Screen.Home, Screen.History, Screen.Settings)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyReminderApp(viewModel: MainViewModel = viewModel()) {
    val navController = rememberNavController()
    val allTasks by viewModel.allTasks.collectAsState()
    val todayPending by viewModel.todayPending.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val discoveredHost by viewModel.discoveredHost.collectAsState()
    var date by remember { mutableStateOf(java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())) }
    val dateTasks by viewModel.getTasksByDate(date).collectAsState(initial = emptyList())

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
                    tasks = dateTasks,
                    onToggleDone = { id, done -> viewModel.toggleDone(id, done) }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    syncStatus = syncStatus,
                    discoveredHost = discoveredHost,
                    onStartDiscovery = { viewModel.startDiscovery() },
                    onSyncNow = { viewModel.syncNow() }
                )
            }
        }
    }
}
