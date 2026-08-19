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

    // ---- 自定义字体 ----

    /** 自定义字体配置的 JSON key */
    private const val FONT_KEY = "custom_font"

    /** 更新自定义字体配置 */
    fun updateFont(enabled: Boolean, path: String?) {
        val json = readConfig()
        val targetJson = json.optJSONObject(FONT_KEY) ?: JSONObject()
        targetJson.put("enabled", enabled)
        if (path != null) {
            targetJson.put("path", path)
        } else {
            targetJson.remove("path")
        }
        json.put(FONT_KEY, targetJson)
        writeConfig(json)
    }

    /** 读取自定义字体配置:返回 (enabled, path) */
    fun getFont(): Pair<Boolean, String?> {
        val json = readConfig()
        val targetJson = json.optJSONObject(FONT_KEY) ?: return false to null
        val enabled = targetJson.optBoolean("enabled", false)
        val path = targetJson.optString("path", null)
        return enabled to path
    }

    // ---- 运动:能量值 ----

    /** 运动能量配置的 JSON key */
    private const val SPORT_ENERGY_KEY = "sport_energy"

    /** 更新运动能量配置 */
    fun updateSportEnergy(enabled: Boolean, value: Int?) {
        val json = readConfig()
        val targetJson = json.optJSONObject(SPORT_ENERGY_KEY) ?: JSONObject()
        targetJson.put("enabled", enabled)
        if (value != null) {
            targetJson.put("value", value)
        } else {
            targetJson.remove("value")
        }
        json.put(SPORT_ENERGY_KEY, targetJson)
        writeConfig(json)
    }

    /** 读取运动能量配置:返回 (enabled, value) */
    fun getSportEnergy(): Pair<Boolean, Int?> {
        val json = readConfig()
        val targetJson = json.optJSONObject(SPORT_ENERGY_KEY) ?: return false to null
        val enabled = targetJson.optBoolean("enabled", false)
        val value = if (targetJson.has("value")) targetJson.optInt("value") else null
        return enabled to value
    }

    // ---- 运动:一键红环 ----

    /** 一键红环配置的 JSON key */
    private const val SPORT_RED_RING_KEY = "sport_red_ring"

    /** 更新一键红环配置 */
    fun updateSportRedRing(enabled: Boolean, count: Int?) {
        val json = readConfig()
        val targetJson = json.optJSONObject(SPORT_RED_RING_KEY) ?: JSONObject()
        targetJson.put("enabled", enabled)
        if (count != null) {
            targetJson.put("count", count)
        } else {
            targetJson.remove("count")
        }
        json.put(SPORT_RED_RING_KEY, targetJson)
        writeConfig(json)
    }

    /** 读取一键红环配置:返回 (enabled, count) */
    fun getSportRedRing(): Pair<Boolean, Int> {
        val json = readConfig()
        val targetJson = json.optJSONObject(SPORT_RED_RING_KEY) ?: return false to 1
        val enabled = targetJson.optBoolean("enabled", false)
        val count = targetJson.optInt("count", 1).coerceAtLeast(1)
        return enabled to count
    }

    // ---- 运动:自定义字体 ----

    /** 运动自定义字体配置的 JSON key */
    private const val SPORT_FONT_KEY = "sport_font"

    /** 更新运动自定义字体配置 */
    fun updateSportFont(enabled: Boolean, path: String?) {
        val json = readConfig()
        val targetJson = json.optJSONObject(SPORT_FONT_KEY) ?: JSONObject()
        targetJson.put("enabled", enabled)
        if (path != null) {
            targetJson.put("path", path)
        } else {
            targetJson.remove("path")
        }
        json.put(SPORT_FONT_KEY, targetJson)
        writeConfig(json)
    }

    /** 读取运动自定义字体配置:返回 (enabled, path) */
    fun getSportFont(): Pair<Boolean, String?> {
        val json = readConfig()
        val targetJson = json.optJSONObject(SPORT_FONT_KEY) ?: return false to null
        val enabled = targetJson.optBoolean("enabled", false)
        val path = targetJson.optString("path", null)
        return enabled to path
    }

    // ---- 运动:自定义头像 ----

    /** 运动头像配置的 JSON key */
    private const val SPORT_AVATAR_KEY = "sport_avatar"

    /** 更新运动头像配置 */
    fun updateSportAvatar(enabled: Boolean, path: String?) {
        val json = readConfig()
        val targetJson = json.optJSONObject(SPORT_AVATAR_KEY) ?: JSONObject()
        targetJson.put("enabled", enabled)
        if (path != null) {
            targetJson.put("path", path)
        } else {
            targetJson.remove("path")
        }
        json.put(SPORT_AVATAR_KEY, targetJson)
        writeConfig(json)
    }

    /** 读取运动头像配置:返回 (enabled, path) */
    fun getSportAvatar(): Pair<Boolean, String?> {
        val json = readConfig()
        val targetJson = json.optJSONObject(SPORT_AVATAR_KEY) ?: return false to null
        val enabled = targetJson.optBoolean("enabled", false)
        val path = targetJson.optString("path", null)
        return enabled to path
    }

    // ---- 运动:排行榜(自定义排名) ----

    /** 自定义排名配置的 JSON key */
    private const val SPORT_RANK_KEY = "sport_rank"

    /** 更新自定义排名配置 */
    fun updateSportRank(enabled: Boolean, value: Int?) {
        val json = readConfig()
        val targetJson = json.optJSONObject(SPORT_RANK_KEY) ?: JSONObject()
        targetJson.put("enabled", enabled)
        if (value != null) {
            targetJson.put("value", value)
        } else {
            targetJson.remove("value")
        }
        json.put(SPORT_RANK_KEY, targetJson)
        writeConfig(json)
    }

    /** 读取自定义排名配置:返回 (enabled, value) */
    fun getSportRank(): Pair<Boolean, Int?> {
        val json = readConfig()
        val targetJson = json.optJSONObject(SPORT_RANK_KEY) ?: return false to null
        val enabled = targetJson.optBoolean("enabled", false)
        val value = if (targetJson.has("value")) targetJson.optInt("value") else null
        return enabled to value
    }

    // ---- 运动:排行榜(自定义能量) ----

    /** 自定义能量配置的 JSON key */
    private const val SPORT_RANK_ENERGY_KEY = "sport_rank_energy"

    /** 更新自定义能量配置 */
    fun updateSportRankEnergy(enabled: Boolean, value: Int?) {
        val json = readConfig()
        val targetJson = json.optJSONObject(SPORT_RANK_ENERGY_KEY) ?: JSONObject()
        targetJson.put("enabled", enabled)
        if (value != null) {
            targetJson.put("value", value)
        } else {
            targetJson.remove("value")
        }
        json.put(SPORT_RANK_ENERGY_KEY, targetJson)
        writeConfig(json)
    }

    /** 读取自定义能量配置:返回 (enabled, value) */
    fun getSportRankEnergy(): Pair<Boolean, Int?> {
        val json = readConfig()
        val targetJson = json.optJSONObject(SPORT_RANK_ENERGY_KEY) ?: return false to null
        val enabled = targetJson.optBoolean("enabled", false)
        val value = if (targetJson.has("value")) targetJson.optInt("value") else null
        return enabled to value
    }

    // ---- 个人中心 ----

    /** 个人中心字段 JSON key(昵称/积分/实名/账号id) */
    private const val PC_NAME_KEY = "pc_name"
    private const val PC_SCORE_KEY = "pc_score"
    private const val PC_REALNAME_KEY = "pc_realname"
    private const val PC_FONT_KEY = "pc_font"
    private const val PC_NAME_COLOR_KEY = "pc_name_color"
    private const val PC_BG_BOY_KEY = "pc_bg_boy"
    private const val PC_BG_GIRL_KEY = "pc_bg_girl"

    /** 更新个人中心字段(enabled + value,value 统一存字符串) */
    fun updatePersonalField(key: String, enabled: Boolean, value: String?) {
        val json = readConfig()
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

    /** 读取个人中心字段:返回 (enabled, value) */
    fun getPersonalField(key: String): Pair<Boolean, String?> {
        val json = readConfig()
        val targetJson = json.optJSONObject(key) ?: return false to null
        val enabled = targetJson.optBoolean("enabled", false)
        val value = if (targetJson.has("value")) targetJson.optString("value") else null
        return enabled to value
    }

    /** 昵称 */
    fun updatePersonalName(enabled: Boolean, value: String?) =
        updatePersonalField(PC_NAME_KEY, enabled, value)
    fun getPersonalName(): Pair<Boolean, String?> = getPersonalField(PC_NAME_KEY)

    /** 积分(整数) */
    fun updatePersonalScore(enabled: Boolean, value: Int?) =
        updatePersonalField(PC_SCORE_KEY, enabled, value?.toString())
    fun getPersonalScore(): Pair<Boolean, Int?> {
        val (enabled, v) = getPersonalField(PC_SCORE_KEY)
        return enabled to (v?.toIntOrNull())
    }

    /** 实名 */
    fun updatePersonalRealName(enabled: Boolean, value: String?) =
        updatePersonalField(PC_REALNAME_KEY, enabled, value)
    fun getPersonalRealName(): Pair<Boolean, String?> = getPersonalField(PC_REALNAME_KEY)

    /** 个人中心自定义字体 */
    fun updatePersonalFont(enabled: Boolean, path: String?) {
        val json = readConfig()
        val targetJson = json.optJSONObject(PC_FONT_KEY) ?: JSONObject()
        targetJson.put("enabled", enabled)
        if (path != null) {
            targetJson.put("path", path)
        } else {
            targetJson.remove("path")
        }
        json.put(PC_FONT_KEY, targetJson)
        writeConfig(json)
    }

    fun getPersonalFont(): Pair<Boolean, String?> {
        val json = readConfig()
        val targetJson = json.optJSONObject(PC_FONT_KEY) ?: return false to null
        val enabled = targetJson.optBoolean("enabled", false)
        val path = targetJson.optString("path", null)
        return enabled to path
    }

    /** 个人中心昵称颜色 */
    fun updatePersonalNameColor(enabled: Boolean, value: String?) =
        updatePersonalField(PC_NAME_COLOR_KEY, enabled, value)
    fun getPersonalNameColor(): Pair<Boolean, String?> = getPersonalField(PC_NAME_COLOR_KEY)

    /** 个人中心背景图片(男/女) */
    fun updatePersonalBg(key: String, enabled: Boolean, path: String?) {
        val json = readConfig()
        val targetJson = json.optJSONObject(key) ?: JSONObject()
        targetJson.put("enabled", enabled)
        if (path != null) {
            targetJson.put("path", path)
        } else {
            targetJson.remove("path")
        }
        json.put(key, targetJson)
        writeConfig(json)
    }

    fun getPersonalBg(key: String): Pair<Boolean, String?> {
        val json = readConfig()
        val targetJson = json.optJSONObject(key) ?: return false to null
        val enabled = targetJson.optBoolean("enabled", false)
        val path = targetJson.optString("path", null)
        return enabled to path
    }

    fun updatePersonalBgBoy(enabled: Boolean, path: String?) =
        updatePersonalBg(PC_BG_BOY_KEY, enabled, path)
    fun getPersonalBgBoy(): Pair<Boolean, String?> = getPersonalBg(PC_BG_BOY_KEY)
    fun updatePersonalBgGirl(enabled: Boolean, path: String?) =
        updatePersonalBg(PC_BG_GIRL_KEY, enabled, path)
    fun getPersonalBgGirl(): Pair<Boolean, String?> = getPersonalBg(PC_BG_GIRL_KEY)
}
