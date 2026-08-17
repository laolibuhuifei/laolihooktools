package com.laoli.hooktools.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日志工具(仅输出到 Logcat,不再写文件)。
 *
 * 原因:写文件(/sdcard/laoli_hooktools/)在系统启动早期会访问尚未挂载的外部存储,
 * 在部分设备(如 Android 8.1 + 老 Xposed)上会导致开机卡第一屏,故已移除文件写入。
 * 排查日志请使用: adb logcat -s LaoliHook
 */
object Logger {

    private const val TAG = "LaoliHook"

    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())

    fun log(tag: String, msg: String, t: Throwable? = null) {
        val time = dateFormat.format(Date())
        val fullMsg = "[$time][$tag] $msg" + if (t != null) "\n  ${t.javaClass.name}: ${t.message}\n${Log.getStackTraceString(t)}" else ""

        try {
            if (t != null) Log.e(TAG, fullMsg, t) else Log.d(TAG, fullMsg)
        } catch (_: Throwable) {
        }
    }

    fun d(tag: String, msg: String) = log(tag, msg)

    fun e(tag: String, msg: String, t: Throwable? = null) = log(tag, msg, t)

    /** 兼容旧界面:日志已不再写文件 */
    fun readLog(): String = "日志已改为仅输出到 Logcat,请使用 adb logcat -s LaoliHook 查看"

    /** 兼容旧界面:无文件可清空 */
    fun clearLog() {
    }
}
