package com.laoli.hooktools.prefs

import android.content.Context
import android.content.SharedPreferences
import com.laoli.hooktools.util.Constants
import com.laoli.hooktools.util.FileConfig

/**
 * 模块 UI 侧配置管理。
 *
 * 双写:
 * 1. SharedPreferences(UI 进程自身读取)
 * 2. /sdcard/laoli_hooktools/config.json(Hook 进程读取,不依赖 XSharedPreferences)
 */
class PrefsManager private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(Constants.PREFS_CONFIG, Context.MODE_PRIVATE)

    fun setEnabled(target: Constants.TargetResource, enabled: Boolean) {
        prefs.edit().putBoolean(target.configKeyEnabled, enabled).commit()
        // 同步写入 JSON 文件供 Hook 读取
        val (oldEnabled, oldPath) = FileConfig.getTarget(target)
        FileConfig.updateTarget(target, enabled, oldPath)
    }

    fun isEnabled(target: Constants.TargetResource): Boolean =
        prefs.getBoolean(target.configKeyEnabled, false)

    fun setPath(target: Constants.TargetResource, path: String?) {
        prefs.edit().apply {
            if (path.isNullOrEmpty()) {
                remove(target.configKeyPath)
            } else {
                putString(target.configKeyPath, path)
            }
        }.commit()
        // 同步写入 JSON 文件供 Hook 读取
        val (oldEnabled, _) = FileConfig.getTarget(target)
        FileConfig.updateTarget(target, oldEnabled, path)
    }

    fun getPath(target: Constants.TargetResource): String? =
        prefs.getString(target.configKeyPath, null)

    fun hasImage(target: Constants.TargetResource): Boolean =
        !getPath(target).isNullOrEmpty()

    fun isAnyEnabled(): Boolean =
        isEnabled(Constants.TargetResource.BG_HEAD_VIEW) ||
                isEnabled(Constants.TargetResource.BG_NEW_MESSAGE)

    // ---- string 替换 ----

    fun setStringEnabled(target: Constants.TargetString, enabled: Boolean) {
        prefs.edit().putBoolean(target.configKeyEnabled, enabled).commit()
        val (_, oldValue) = FileConfig.getString(target)
        FileConfig.updateString(target, enabled, oldValue)
    }

    fun isStringEnabled(target: Constants.TargetString): Boolean =
        prefs.getBoolean(target.configKeyEnabled, false)

    fun setStringValue(target: Constants.TargetString, value: String?) {
        prefs.edit().apply {
            if (value.isNullOrEmpty()) {
                remove(target.configKeyValue)
            } else {
                putString(target.configKeyValue, value)
            }
        }.commit()
        val (oldEnabled, _) = FileConfig.getString(target)
        FileConfig.updateString(target, oldEnabled, value)
    }

    fun getStringValue(target: Constants.TargetString): String? =
        prefs.getString(target.configKeyValue, null)

    // ---- color 替换 ----

    fun setColorEnabled(target: Constants.TargetColor, enabled: Boolean) {
        prefs.edit().putBoolean(target.configKeyEnabled, enabled).commit()
        val (_, oldValue) = FileConfig.getColor(target)
        FileConfig.updateColor(target, enabled, oldValue)
    }

    fun isColorEnabled(target: Constants.TargetColor): Boolean =
        prefs.getBoolean(target.configKeyEnabled, false)

    fun setColorValue(target: Constants.TargetColor, value: String?) {
        prefs.edit().apply {
            if (value.isNullOrEmpty()) {
                remove(target.configKeyValue)
            } else {
                putString(target.configKeyValue, value)
            }
        }.commit()
        val (oldEnabled, _) = FileConfig.getColor(target)
        FileConfig.updateColor(target, oldEnabled, value)
    }

    fun getColorValue(target: Constants.TargetColor): String? =
        prefs.getString(target.configKeyValue, null)

    // ---- 时间详细显示 ----

    fun setTimeDetailEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(Constants.KEY_TIME_DETAIL_ENABLED, enabled).commit()
        FileConfig.updateTimeDetail(enabled)
    }

    fun isTimeDetailEnabled(): Boolean =
        prefs.getBoolean(Constants.KEY_TIME_DETAIL_ENABLED, false)

    // ---- 防删除(只防同步删除) ----

    fun setAntiDeleteEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(Constants.KEY_ANTI_DELETE_ENABLED, enabled).commit()
        FileConfig.updateAntiDelete(enabled)
    }

    fun isAntiDeleteEnabled(): Boolean =
        prefs.getBoolean(Constants.KEY_ANTI_DELETE_ENABLED, false)

    // ---- 链接自动跳转 ----

    fun setLinkJumpEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(Constants.KEY_LINK_JUMP_ENABLED, enabled).commit()
        FileConfig.updateLinkJump(enabled)
    }

    fun isLinkJumpEnabled(): Boolean =
        prefs.getBoolean(Constants.KEY_LINK_JUMP_ENABLED, false)

    companion object {
        @Volatile private var instance: PrefsManager? = null
        fun get(context: Context): PrefsManager =
            instance ?: synchronized(this) {
                instance ?: PrefsManager(context.applicationContext).also { instance = it }
            }
    }
}
