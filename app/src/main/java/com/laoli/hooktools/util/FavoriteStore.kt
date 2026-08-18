package com.laoli.hooktools.util

import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 收藏动态的跨进程存储。
 *
 * 好友圈 Hook 进程写入(长按弹窗里的"取消"按钮收藏),模块 App 进程读取(收藏夹展示)。
 * 元数据文件: /sdcard/laoli_hooktools/favorites.json
 * 每条收藏的本地文件目录: /sdcard/laoli_hooktools/favorites/<momentId>/
 *   - text.txt     文字内容
 *   - image_0.jpg / video_0.mp4 等 媒体文件
 */
object FavoriteStore {

    data class FavoriteMoment(
        val momentId: String,
        val name: String,
        val content: String,
        val type: Int,
        val createTime: Long,
        /** 点赞数 */
        val likeCount: Int,
        /** 评论数 */
        val commentCount: Int,
        /** 发布者头像 URL(可为空) */
        val iconUrl: String?,
        /** 媒体文件相对文件名(不含 text.txt),如 ["image_0.jpg", "video_0.mp4"] */
        val mediaFiles: List<String>
    )

    private fun file(): File =
        File(Environment.getExternalStorageDirectory(), "laoli_hooktools/favorites.json")

    /** 收藏文件根目录 */
    fun rootDir(): File =
        File(Environment.getExternalStorageDirectory(), "laoli_hooktools/favorites")

    /** 单条收藏的目录 */
    fun dirOf(momentId: String): File = File(rootDir(), momentId)

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

    private fun toJson(fav: FavoriteMoment): JSONObject {
        val obj = JSONObject()
        obj.put("momentId", fav.momentId)
        obj.put("name", fav.name)
        obj.put("content", fav.content)
        obj.put("type", fav.type)
        obj.put("createTime", fav.createTime)
        obj.put("likeCount", fav.likeCount)
        obj.put("commentCount", fav.commentCount)
        if (fav.iconUrl != null) obj.put("iconUrl", fav.iconUrl)
        val arr = JSONArray()
        fav.mediaFiles.forEach { arr.put(it) }
        obj.put("mediaFiles", arr)
        return obj
    }

    private fun fromJson(obj: JSONObject): FavoriteMoment {
        val mediaFiles = mutableListOf<String>()
        val arr = obj.optJSONArray("mediaFiles")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                arr.optString(i)?.let { mediaFiles.add(it) }
            }
        }
        return FavoriteMoment(
            momentId = obj.optString("momentId"),
            name = obj.optString("name"),
            content = obj.optString("content"),
            type = obj.optInt("type"),
            createTime = obj.optLong("createTime"),
            likeCount = obj.optInt("likeCount"),
            commentCount = obj.optInt("commentCount"),
            iconUrl = if (obj.has("iconUrl")) obj.optString("iconUrl") else null,
            mediaFiles = mediaFiles
        )
    }

    /** 列出所有收藏(按收藏时间倒序) */
    fun list(): List<FavoriteMoment> {
        val json = readJson()
        val result = mutableListOf<FavoriteMoment>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val obj = json.optJSONObject(k) ?: continue
            result.add(fromJson(obj))
        }
        return result.sortedByDescending { it.createTime }
    }

    fun get(momentId: String): FavoriteMoment? {
        val obj = readJson().optJSONObject(momentId) ?: return null
        return fromJson(obj)
    }

    fun contains(momentId: String): Boolean =
        readJson().has(momentId)

    fun add(fav: FavoriteMoment) {
        val json = readJson()
        json.put(fav.momentId, toJson(fav))
        writeJson(json)
    }

    /** 删除收藏(仅删元数据,不删本地文件);返回被删条目 */
    fun remove(momentId: String): FavoriteMoment? {
        val json = readJson()
        val obj = json.optJSONObject(momentId)
        val removed = obj?.let { fromJson(it) }
        json.remove(momentId)
        writeJson(json)
        return removed
    }
}
