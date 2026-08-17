package com.laoli.hooktools.util

import android.os.Environment
import org.json.JSONObject
import java.io.File

/**
 * 已编辑动态的跨进程存储。
 *
 * 模块 UI(EditMomentActivity)进程写入,好友圈 Hook 进程读取。
 * 文件: /sdcard/laoli_hooktools/edited_moments.json
 * 结构: { "momentId": { "text": "内容", "color": 16711680, "underline": true } }
 */
object EditedMomentStore {

    data class EditedMoment(
        /** 编辑后的文字内容(null 表示未改文字) */
        val text: String?,
        /** 文字颜色 ARGB int(null 表示不改颜色,沿用原色) */
        val color: Int?,
        /** 是否下划线 */
        val underline: Boolean,
        /** 编辑后的点赞数量(null 表示不修改,沿用真实点赞数) */
        val likeCount: Int?,
        /** 编辑后的发布者名字(null 表示不修改,沿用原名) */
        val name: String?
    )

    private fun file(): File =
        File(Environment.getExternalStorageDirectory(), "laoli_hooktools/edited_moments.json")

    // 简单缓存:按文件 lastModified 失效,避免每次渲染都读文件
    @Volatile private var cachedJson: JSONObject? = null
    @Volatile private var cachedTime: Long = 0

    private fun readJson(): JSONObject {
        val f = file()
        val lastModified = f.lastModified()
        val cached = cachedJson
        if (cached != null && cachedTime == lastModified) return cached
        val json = try {
            if (f.exists()) JSONObject(f.readText()) else JSONObject()
        } catch (_: Throwable) {
            JSONObject()
        }
        cachedJson = json
        cachedTime = lastModified
        return json
    }

    private fun writeJson(json: JSONObject) {
        try {
            val f = file()
            f.parentFile?.mkdirs()
            f.writeText(json.toString())
            try {
                f.setReadable(true, false)
            } catch (_: Throwable) {
            }
        } catch (_: Throwable) {
        }
    }

    /** 读取单个动态的编辑配置 */
    fun get(momentId: String): EditedMoment? {
        val obj = readJson().optJSONObject(momentId) ?: return null
        val text = obj.optString("text", null)
        val color = if (obj.has("color")) obj.optInt("color") else null
        val underline = obj.optBoolean("underline", false)
        val likeCount = if (obj.has("likeCount")) obj.optInt("likeCount") else null
        val name = if (obj.has("name")) obj.optString("name") else null
        return EditedMoment(text, color, underline, likeCount, name)
    }

    /** 保存动态编辑配置 */
    fun save(momentId: String, text: String?, color: Int?, underline: Boolean, likeCount: Int?, name: String?) {
        val json = readJson()
        val obj = JSONObject()
        if (text != null) obj.put("text", text)
        if (color != null) obj.put("color", color)
        obj.put("underline", underline)
        if (likeCount != null) obj.put("likeCount", likeCount)
        if (name != null) obj.put("name", name)
        json.put(momentId, obj)
        writeJson(json)
    }

    /** 删除动态编辑配置(恢复原样) */
    fun remove(momentId: String) {
        val json = readJson()
        json.remove(momentId)
        writeJson(json)
    }

    /** 清除所有编辑配置(一键还原所有动态) */
    fun clear() {
        try {
            file().delete()
        } catch (_: Throwable) {
        }
        cachedJson = null
        cachedTime = 0L
    }
}
