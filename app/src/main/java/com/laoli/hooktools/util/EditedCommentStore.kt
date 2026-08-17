package com.laoli.hooktools.util

import android.os.Environment
import org.json.JSONObject
import java.io.File

/**
 * 已编辑评论的跨进程存储。
 *
 * 模块 UI(EditCommentActivity)进程写入,好友圈 Hook 进程读取。
 * 文件: /sdcard/laoli_hooktools/edited_comments.json
 * 结构: { "commentId": { "color": 16711680, "underline": true, "name": "名字" } }
 */
object EditedCommentStore {

    data class EditedComment(
        /** 编辑后的评论内容(null 表示不改内容,沿用原文) */
        val text: String?,
        /** 评论内容颜色 ARGB int(null 表示不改颜色,沿用原色) */
        val color: Int?,
        /** 是否给评论内容加下划线 */
        val underline: Boolean,
        /** 编辑后的评论者名字(null 表示不修改,沿用原名) */
        val name: String?
    )

    private fun file(): File =
        File(Environment.getExternalStorageDirectory(), "laoli_hooktools/edited_comments.json")

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

    /** 读取单条评论的编辑配置 */
    fun get(commentId: String): EditedComment? {
        val obj = readJson().optJSONObject(commentId) ?: return null
        val text = if (obj.has("text")) obj.optString("text") else null
        val color = if (obj.has("color")) obj.optInt("color") else null
        val underline = obj.optBoolean("underline", false)
        val name = if (obj.has("name")) obj.optString("name") else null
        return EditedComment(text, color, underline, name)
    }

    /** 保存评论编辑配置 */
    fun save(commentId: String, text: String?, color: Int?, underline: Boolean, name: String?) {
        val json = readJson()
        val obj = JSONObject()
        if (text != null) obj.put("text", text)
        if (color != null) obj.put("color", color)
        obj.put("underline", underline)
        if (name != null) obj.put("name", name)
        json.put(commentId, obj)
        writeJson(json)
    }

    /** 删除评论编辑配置(恢复原样) */
    fun remove(commentId: String) {
        val json = readJson()
        json.remove(commentId)
        writeJson(json)
    }

    /** 清除所有评论编辑配置 */
    fun clear() {
        try {
            file().delete()
        } catch (_: Throwable) {
        }
        cachedJson = null
        cachedTime = 0L
    }
}
