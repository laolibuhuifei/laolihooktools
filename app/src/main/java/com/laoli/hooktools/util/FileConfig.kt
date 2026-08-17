package com.laoli.hooktools.util

import android.content.Context
import android.os.Environment
import org.json.JSONObject
import java.io.File

/**
 * 文件配置管理。
 *
 * 将模块配置写入 /sdcard/laoli_hooktools/config.json,
 * Hook 进程直接读文件,不依赖 XSharedPreferences(在 LSPosed 上不可靠)。
 *
 * JSON 格式:
 * {
 *   "bg_head_view": { "enabled": true, "path": "/sdcard/laoli_hooktools/bg_head_view.png" },
 *   "bg_new_message": { "enabled": false, "path": null }
 * }
 */
object FileConfig {

    /** 配置目录 */
    private fun configDir(): File {
        val dir = File(Environment.getExternalStorageDirectory(), "laoli_hooktools")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** 配置文件路径 */
    fun configFilePath(): String = File(configDir(), "config.json").absolutePath

    /** 读取配置 JSON */
    fun readConfig(): JSONObject {
        return try {
            val file = File(configFilePath())
            if (file.exists()) {
                JSONObject(file.readText())
            } else {
                JSONObject()
            }
        } catch (t: Throwable) {
            JSONObject()
        }
    }

    /** 写入配置 JSON */
    fun writeConfig(json: JSONObject) {
        try {
            val file = File(configFilePath())
            file.writeText(json.toString())
            // 设置世界可读
            try {
                file.setReadable(true, false)
            } catch (_: Throwable) {
            }
            // 用 root 确保权限
            try {
                RootUtil.execRoot("chmod 644 ${file.absolutePath}")
            } catch (_: Throwable) {
            }
        } catch (t: Throwable) {
            // 写入失败,尝试用 root 写
        }
    }

    /**
     * 更新单个资源配置。
     */
    fun updateTarget(target: Constants.TargetResource, enabled: Boolean, path: String?) {
        val json = readConfig()
        val targetJson = json.optJSONObject(target.resName) ?: JSONObject()
        targetJson.put("enabled", enabled)
        if (path != null) {
            targetJson.put("path", path)
        } else {
            targetJson.remove("path")
        }
        json.put(target.resName, targetJson)
        writeConfig(json)
    }

    /**
     * 读取单个资源配置。
     */
    fun getTarget(target: Constants.TargetResource): Pair<Boolean, String?> {
        val json = readConfig()
        val targetJson = json.optJSONObject(target.resName) ?: return false to null
        val enabled = targetJson.optBoolean("enabled", false)
        val path = targetJson.optString("path", null)
        return enabled to path
    }

    // ---- string 替换 ----

    /** string 配置的 JSON key 前缀 */
    private const val STRING_PREFIX = "string_"

    /** 更新单个 string 配置 */
    fun updateString(target: Constants.TargetString, enabled: Boolean, value: String?) {
        val json = readConfig()
        val key = STRING_PREFIX + target.resName
        val targetJson = json.optJSONObject(key) ?: JSONObject()
        targetJson.put("enabled", enabled)
        if (value != null) {
            targetJson.put("value", value)
        } else {
            targetJson.remove("value")
        }
        json.put(key, targetJson)
        writeConfig(json)
    }

    /** 读取单个 string 配置:返回 (enabled, value) */
    fun getString(target: Constants.TargetString): Pair<Boolean, String?> {
        val json = readConfig()
        val key = STRING_PREFIX + target.resName
        val targetJson = json.optJSONObject(key) ?: return false to null
        val enabled = targetJson.optBoolean("enabled", false)
        val value = targetJson.optString("value", null)
        return enabled to value
    }

    // ---- color 替换 ----

    /** color 配置的 JSON key 前缀 */
    private const val COLOR_PREFIX = "color_"

    /** 更新单个 color 配置 */
    fun updateColor(target: Constants.TargetColor, enabled: Boolean, value: String?) {
        val json = readConfig()
        val key = COLOR_PREFIX + target.resName
        val targetJson = json.optJSONObject(key) ?: JSONObject()
        targetJson.put("enabled", enabled)
        if (value != null) {
            targetJson.put("value", value)
        } else {
            targetJson.remove("value")
        }
        json.put(key, targetJson)
        writeConfig(json)
    }

    /** 读取单个 color 配置:返回 (enabled, value) */
    fun getColor(target: Constants.TargetColor): Pair<Boolean, String?> {
        val json = readConfig()
        val key = COLOR_PREFIX + target.resName
        val targetJson = json.optJSONObject(key) ?: return false to null
        val enabled = targetJson.optBoolean("enabled", false)
        val value = targetJson.optString("value", null)
        return enabled to value
    }

    // ---- 时间详细显示 ----

    /** 时间配置的 JSON key */
    private const val TIME_DETAIL_KEY = "time_detail"

    /** 更新时间详细显示开关 */
    fun updateTimeDetail(enabled: Boolean) {
        val json = readConfig()
        val targetJson = json.optJSONObject(TIME_DETAIL_KEY) ?: JSONObject()
        targetJson.put("enabled", enabled)
        json.put(TIME_DETAIL_KEY, targetJson)
        writeConfig(json)
    }

    /** 读取时间详细显示开关 */
    fun isTimeDetailEnabled(): Boolean {
        val json = readConfig()
        val targetJson = json.optJSONObject(TIME_DETAIL_KEY) ?: return false
        return targetJson.optBoolean("enabled", false)
    }

    // ---- 防删除(只防同步删除) ----

    /** 防删除配置的 JSON key */
    private const val ANTI_DELETE_KEY = "anti_delete"

    /** 更新防删除开关 */
    fun updateAntiDelete(enabled: Boolean) {
        val json = readConfig()
        val targetJson = json.optJSONObject(ANTI_DELETE_KEY) ?: JSONObject()
        targetJson.put("enabled", enabled)
        json.put(ANTI_DELETE_KEY, targetJson)
        writeConfig(json)
    }

    /** 读取防删除开关 */
    fun isAntiDeleteEnabled(): Boolean {
        val json = readConfig()
        val targetJson = json.optJSONObject(ANTI_DELETE_KEY) ?: return false
        return targetJson.optBoolean("enabled", false)
    }

    // ---- 链接自动跳转 ----

    /** 链接自动跳转配置的 JSON key */
    private const val LINK_JUMP_KEY = "link_jump"

    /** 更新链接自动跳转开关 */
    fun updateLinkJump(enabled: Boolean) {
        val json = readConfig()
        val targetJson = json.optJSONObject(LINK_JUMP_KEY) ?: JSONObject()
        targetJson.put("enabled", enabled)
        json.put(LINK_JUMP_KEY, targetJson)
        writeConfig(json)
    }

    /** 读取链接自动跳转开关 */
    fun isLinkJumpEnabled(): Boolean {
        val json = readConfig()
        val targetJson = json.optJSONObject(LINK_JUMP_KEY) ?: return false
        return targetJson.optBoolean("enabled", false)
    }
}
