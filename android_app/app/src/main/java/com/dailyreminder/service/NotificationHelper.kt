package com.dailyreminder.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.dailyreminder.MainActivity
import com.dailyreminder.R

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_SERVICE = "daily_reminder_service"
        const val CHANNEL_REMINDER = "daily_reminder_reminder"
        const val CHANNEL_DOWNLOAD = "daily_reminder_download"
        const val NOTIFY_SERVICE = 1
        const val NOTIFY_REMINDER = 2
        const val NOTIFY_DOWNLOAD = 3
    }

    fun createChannels() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 前台服务通知渠道（低优先级，不弹窗）
        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE, "后台服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "后台常驻服务通知"
            setShowBadge(false)
        }
        nm.createNotificationChannel(serviceChannel)

        // 提醒通知渠道（高优先级，弹窗）
        val reminderChannel = NotificationChannel(
            CHANNEL_REMINDER, "任务提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "未完成任务的定时提醒"
            enableVibration(true)
        }
        nm.createNotificationChannel(reminderChannel)

        // 下载通知渠道（中优先级，弹窗提醒）
        val downloadChannel = NotificationChannel(
            CHANNEL_DOWNLOAD, "更新下载",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "新版本下载完成通知"
            setShowBadge(false)
        }
        nm.createNotificationChannel(downloadChannel)
    }

    fun buildServiceNotification(): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setContentTitle("日常提醒运行中")
            .setContentText("点击查看任务")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    /** 下载完成通知（点击跳转安装） */
    fun showDownloadCompleteNotification(apkUri: androidx.core.net.UriCompat? = null) {
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val pi = PendingIntent.getActivity(
            context, 3, installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_DOWNLOAD)
            .setContentTitle("新版本已下载")
            .setContentText("点击安装新版本")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFY_DOWNLOAD, notification)
    }

    fun buildReminderNotification(title: String, body: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            context, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // 锁屏可见
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFY_REMINDER, notification)
    }
}
