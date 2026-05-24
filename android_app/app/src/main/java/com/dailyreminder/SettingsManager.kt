package com.dailyreminder

import android.content.Context
import android.content.SharedPreferences

/**
 * 管理用户设置（提醒时间开关等）
 */
class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // 早上 8 点提醒
    var morningReminder: Boolean
        get() = prefs.getBoolean("morning_reminder", true)
        set(v) = prefs.edit().putBoolean("morning_reminder", v).apply()

    var morningHour: Int
        get() = prefs.getInt("morning_hour", 8)
        set(v) = prefs.edit().putInt("morning_hour", v).apply()

    var morningMinute: Int
        get() = prefs.getInt("morning_minute", 0)
        set(v) = prefs.edit().putInt("morning_minute", v).apply()

    // 下午 2 点提醒
    var afternoonReminder: Boolean
        get() = prefs.getBoolean("afternoon_reminder", true)
        set(v) = prefs.edit().putBoolean("afternoon_reminder", v).apply()

    var afternoonHour: Int
        get() = prefs.getInt("afternoon_hour", 14)
        set(v) = prefs.edit().putInt("afternoon_hour", v).apply()

    var afternoonMinute: Int
        get() = prefs.getInt("afternoon_minute", 0)
        set(v) = prefs.edit().putInt("afternoon_minute", v).apply()

    // 屏幕亮起提醒
    var screenOnReminder: Boolean
        get() = prefs.getBoolean("screen_on_reminder", true)
        set(v) = prefs.edit().putBoolean("screen_on_reminder", v).apply()

    // 主题模式: 0=跟随系统, 1=浅色, 2=深色
    var themeMode: Int
        get() = prefs.getInt("theme_mode", 0)
        set(v) = prefs.edit().putInt("theme_mode", v).apply()

    // 稍后安装的 APK 路径
    var pendingApkPath: String
        get() = prefs.getString("pending_apk_path", "") ?: ""
        set(v) = prefs.edit().putString("pending_apk_path", v).apply()
}
