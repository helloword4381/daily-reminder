package com.dailyreminder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.dailyreminder.service.ReminderService
import com.dailyreminder.ui.theme.DailyReminderTheme

class MainActivity : ComponentActivity() {

    private var serviceStarted = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && !serviceStarted) {
            startReminderService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            DailyReminderTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppEntry()
                }
            }
        }

        // 检查并请求通知权限
        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        // 权限已授予但服务未启动 → 启动
        if (!serviceStarted && hasNotificationPermission()) {
            startReminderService()
        }
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED
        }
        return true // Android 12 及以下默认有通知权限
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasNotificationPermission()) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        // 已经有权限，直接启动服务
        if (!serviceStarted) {
            startReminderService()
        }
    }

    private fun startReminderService() {
        serviceStarted = true
        try {
            val intent = Intent(this, ReminderService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "启动前台服务失败", e)
        }
    }
}
