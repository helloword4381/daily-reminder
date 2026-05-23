package com.dailyreminder.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.dailyreminder.SettingsManager
import com.dailyreminder.data.db.AppDatabase
import com.dailyreminder.data.db.TaskEntity
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class ReminderService : Service() {

    private lateinit var notificationHelper: NotificationHelper
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var screenOnReceiver: ScreenOnReceiver? = null

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
        notificationHelper.createChannels()

        // 注册屏幕亮起广播
        screenOnReceiver = ScreenOnReceiver()
        registerReceiver(screenOnReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))

        // 启动定时提醒
        scheduleAlarms()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = notificationHelper.buildServiceNotification()
        startForeground(NotificationHelper.NOTIFY_SERVICE, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        screenOnReceiver?.let { unregisterReceiver(it) }
        scope.cancel()
        super.onDestroy()
    }

    private fun scheduleAlarms() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // 早上提醒
        scheduleDailyAlarm(alarmManager, 8, 0, "morning")
        // 下午提醒
        scheduleDailyAlarm(alarmManager, 14, 0, "afternoon")
    }

    private fun scheduleDailyAlarm(am: AlarmManager, hour: Int, minute: Int, tag: String) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        val intent = Intent(this, ReminderAlarmReceiver::class.java).apply {
            putExtra("tag", tag)
            putExtra("hour", hour)
            putExtra("minute", minute)
        }
        val pi = PendingIntent.getBroadcast(
            this, if (tag == "morning") 100 else 200, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        }
    }

    inner class ScreenOnReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_ON) {
                val settings = SettingsManager(context)
                if (!settings.screenOnReminder) return

                // 判断是否在工作时间
                val now = Calendar.getInstance()
                val hour = now.get(Calendar.HOUR_OF_DAY)
                val minute = now.get(Calendar.MINUTE)
                val inWorkHours = (hour in 8..11) || (hour == 12 && minute == 0) ||
                        (hour in 14..17) || (hour == 18 && minute == 0)

                if (inWorkHours) {
                    checkAndNotifyPending(context)
                }
            }
        }
    }

    companion object {
        fun checkAndNotifyPending(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getInstance(context)
                    val allPending = db.taskDao().getPendingList()
                    val tasks = allPending.take(5)
                    val count = tasks.size
                    if (count > 0) {
                        val titles = tasks.take(3).joinToString("、") { it.content }
                        val helper = NotificationHelper(context)
                        helper.buildReminderNotification(
                            "还有 $count 个任务未完成",
                            if (count <= 3) titles else "$titles 等"
                        )
                    }
                } catch (_: Exception) {}
            }
        }
    }
}

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val tag = intent.getStringExtra("tag") ?: return
        val settings = SettingsManager(context)

        val enabled = when (tag) {
            "morning" -> settings.morningReminder
            "afternoon" -> settings.afternoonReminder
            else -> false
        }
        if (!enabled) return

        // 重新注册下次闹钟
        val hour = intent.getIntExtra("hour", 8)
        val minute = intent.getIntExtra("minute", 0)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, 1)
        }
        val pi = PendingIntent.getBroadcast(
            context, if (tag == "morning") 100 else 200, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        }

        ReminderService.checkAndNotifyPending(context)
    }
}
