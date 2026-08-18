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

    // ---- 自定义字体 ----

    fun setFontEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(Constants.KEY_FONT_ENABLED, enabled).commit()
        val (_, oldPath) = FileConfig.getFont()
        FileConfig.updateFont(enabled, oldPath)
    }

    fun isFontEnabled(): Boolean =
        prefs.getBoolean(Constants.KEY_FONT_ENABLED, false)

    fun setFontPath(path: String?) {
        prefs.edit().apply {
            if (path.isNullOrEmpty()) {
                remove(Constants.KEY_FONT_PATH)
            } else {
                putString(Constants.KEY_FONT_PATH, path)
            }
        }.commit()
        val (oldEnabled, _) = FileConfig.getFont()
        FileConfig.updateFont(oldEnabled, path)
    }

    fun getFontPath(): String? =
        prefs.getString(Constants.KEY_FONT_PATH, null)

    // ---- 模块自身字体 ----

    fun setModuleFontEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(Constants.KEY_MODULE_FONT_ENABLED, enabled).commit()
    }

    fun isModuleFontEnabled(): Boolean =
        prefs.getBoolean(Constants.KEY_MODULE_FONT_ENABLED, false)

    fun setModuleFontPath(path: String?) {
        prefs.edit().apply {
            if (path.isNullOrEmpty()) {
                remove(Constants.KEY_MODULE_FONT_PATH)
            } else {
                putString(Constants.KEY_MODULE_FONT_PATH, path)
            }
        }.commit()
    }

    fun getModuleFontPath(): String? =
        prefs.getString(Constants.KEY_MODULE_FONT_PATH, null)

    // ---- 运动:能量值 ----

    fun setSportEnergyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(Constants.KEY_SPORT_ENERGY_ENABLED, enabled).commit()
        val (_, oldValue) = FileConfig.getSportEnergy()
        FileConfig.updateSportEnergy(enabled, oldValue)
    }

    fun isSportEnergyEnabled(): Boolean =
        prefs.getBoolean(Constants.KEY_SPORT_ENERGY_ENABLED, false)

    fun setSportEnergyValue(value: Int?) {
        prefs.edit().apply {
            if (value == null) {
                remove(Constants.KEY_SPORT_ENERGY_VALUE)
            } else {
                putInt(Constants.KEY_SPORT_ENERGY_VALUE, value)
            }
        }.commit()
        val (oldEnabled, _) = FileConfig.getSportEnergy()
        FileConfig.updateSportEnergy(oldEnabled, value)
    }

    fun getSportEnergyValue(): Int? =
        if (prefs.contains(Constants.KEY_SPORT_ENERGY_VALUE)) {
            prefs.getInt(Constants.KEY_SPORT_ENERGY_VALUE, 0)
        } else null

    // ---- 运动:一键红环 ----

    fun setSportRedRingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(Constants.KEY_SPORT_RED_RING_ENABLED, enabled).commit()
        val (_, oldCount) = FileConfig.getSportRedRing()
        FileConfig.updateSportRedRing(enabled, oldCount)
    }

    fun isSportRedRingEnabled(): Boolean =
        prefs.getBoolean(Constants.KEY_SPORT_RED_RING_ENABLED, false)

    fun setSportRedRingCount(count: Int) {
        prefs.edit().putInt(Constants.KEY_SPORT_RED_RING_COUNT, count).commit()
        val (oldEnabled, _) = FileConfig.getSportRedRing()
        FileConfig.updateSportRedRing(oldEnabled, count)
    }

    fun getSportRedRingCount(): Int =
        prefs.getInt(Constants.KEY_SPORT_RED_RING_COUNT, 1).coerceIn(1, 20)

    // ---- 运动:自定义字体 ----

    fun setSportFontEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(Constants.KEY_SPORT_FONT_ENABLED, enabled).commit()
        val (_, oldPath) = FileConfig.getSportFont()
        FileConfig.updateSportFont(enabled, oldPath)
    }

    fun isSportFontEnabled(): Boolean =
        prefs.getBoolean(Constants.KEY_SPORT_FONT_ENABLED, false)

    fun setSportFontPath(path: String?) {
        prefs.edit().apply {
            if (path.isNullOrEmpty()) {
                remove(Constants.KEY_SPORT_FONT_PATH)
            } else {
                putString(Constants.KEY_SPORT_FONT_PATH, path)
            }
        }.commit()
        val (oldEnabled, _) = FileConfig.getSportFont()
        FileConfig.updateSportFont(oldEnabled, path)
    }

    fun getSportFontPath(): String? =
        prefs.getString(Constants.KEY_SPORT_FONT_PATH, null)

    // ---- 运动:自定义头像 ----

    fun setSportAvatarEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(Constants.KEY_SPORT_AVATAR_ENABLED, enabled).commit()
        val (_, oldPath) = FileConfig.getSportAvatar()
        FileConfig.updateSportAvatar(enabled, oldPath)
    }

    fun isSportAvatarEnabled(): Boolean =
        prefs.getBoolean(Constants.KEY_SPORT_AVATAR_ENABLED, false)

    fun setSportAvatarPath(path: String?) {
        prefs.edit().apply {
            if (path.isNullOrEmpty()) {
                remove(Constants.KEY_SPORT_AVATAR_PATH)
            } else {
                putString(Constants.KEY_SPORT_AVATAR_PATH, path)
            }
        }.commit()
        val (oldEnabled, _) = FileConfig.getSportAvatar()
        FileConfig.updateSportAvatar(oldEnabled, path)
    }

    fun getSportAvatarPath(): String? =
        prefs.getString(Constants.KEY_SPORT_AVATAR_PATH, null)

    companion object {
        @Volatile private var instance: PrefsManager? = null
        fun get(context: Context): PrefsManager =
            instance ?: synchronized(this) {
                instance ?: PrefsManager(context.applicationContext).also { instance = it }
            }
    }
}
