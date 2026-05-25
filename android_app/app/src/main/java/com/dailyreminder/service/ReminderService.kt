package com.dailyreminder.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.dailyreminder.SettingsManager
import com.dailyreminder.data.db.AppDatabase
import kotlinx.coroutines.*
import java.util.*

class ReminderService : Service() {

    private lateinit var notificationHelper: NotificationHelper
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var intervalHandler: Handler? = null
    private var lastNotifyTime = 0L

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
        notificationHelper.createChannels()

        // 工作时间每 30 分钟推送一次锁屏通知
        intervalHandler = Handler(Looper.getMainLooper())
        startWorkHourCheck()

        scheduleAlarms()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = notificationHelper.buildServiceNotification()
        startForeground(NotificationHelper.NOTIFY_SERVICE, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        intervalHandler?.removeCallbacksAndMessages(null)
        scope.cancel()
        super.onDestroy()
    }

    /** 工作时间每 30 分钟检查一次 */
    private fun startWorkHourCheck() {
        val handler = intervalHandler ?: return
        val settings = SettingsManager(this)
        if (settings.screenOnReminder) {
            val now = Calendar.getInstance()
            val h = now.get(Calendar.HOUR_OF_DAY)
            val inWorkHours = (h >= 8 && h < 12) || (h >= 14 && h < 18)
            if (inWorkHours) {
                val nowMs = System.currentTimeMillis()
                // 首次启动立即检查一次，之后每 30 分钟
                if (lastNotifyTime == 0L || nowMs - lastNotifyTime >= 1_800_000) {
                    lastNotifyTime = nowMs
                    checkAndNotifyPending(this)
                }
            } else {
                lastNotifyTime = 0L // 非工作时间重置计时
            }
        }
        handler.postDelayed({ startWorkHourCheck() }, 1_800_000) // 30 分钟
    }

    private fun scheduleAlarms() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        scheduleDailyAlarm(alarmManager, 8, 0, "morning")
        scheduleDailyAlarm(alarmManager, 14, 0, "afternoon")
    }

    private fun scheduleDailyAlarm(am: AlarmManager, hour: Int, minute: Int, tag: String) {
        try {
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
        } catch (_: Exception) { }
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