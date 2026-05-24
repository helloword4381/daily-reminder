package com.dailyreminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(
            android.content.pm.PackageInstaller.EXTRA_STATUS,
            android.content.pm.PackageInstaller.STATUS_FAILURE
        )
        val message = intent.getStringExtra(
            android.content.pm.PackageInstaller.EXTRA_STATUS_MESSAGE
        ) ?: ""

        // 无论成功失败，删除缓存 APK 文件
        val filePath = intent.getStringExtra("file_path")
        if (filePath != null) {
            val file = java.io.File(filePath)
            if (file.exists()) file.delete()
        }
    }
}
