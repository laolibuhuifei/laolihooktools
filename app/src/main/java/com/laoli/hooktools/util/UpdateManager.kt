package com.laoli.hooktools.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.laoli.hooktools.BuildConfig
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 公告与自更新网络管理。
 *
 * 通过后端 API 检查更新、拉取公告、下载 APK 并安装。
 */
object UpdateManager {

    private const val CONNECT_TIMEOUT = 15_000
    private const val READ_TIMEOUT = 30_000

    /** 更新信息 */
    data class UpdateInfo(
        val hasUpdate: Boolean,
        val versionCode: Int,
        val versionName: String,
        val forceUpdate: Boolean,
        val updateLog: String,
        val apkUrl: String?,
        val apkSize: Long,
        val apkMd5: String
    )

    /** 公告 */
    data class Announcement(
        val id: Long,
        val title: String,
        val content: String,
        val publishTime: Long
    )

    /** 适配应用 */
    data class AdaptApp(
        val id: Long,
        val name: String,
        val iconUrl: String?,
        val apkUrl: String?,
        val apkSize: Long,
        val publishTime: Long
    )

    /** 字体 */
    data class FontItem(
        val id: Long,
        val name: String,
        val fontUrl: String?,
        val publishTime: Long
    )

    /**
     * 检查更新。
     * @return 有更新返回 UpdateInfo,无更新返回 hasUpdate=false 的 UpdateInfo,网络失败返回 null
     */
    fun checkUpdate(): UpdateInfo? {
        val url = "${Constants.API_BASE_URL}/api/update/check?versionCode=${BuildConfig.VERSION_CODE}"
        val body = httpGet(url) ?: return null
        return try {
            val obj = JSONObject(body)
            UpdateInfo(
                hasUpdate = obj.optBoolean("hasUpdate", false),
                versionCode = obj.optInt("versionCode", 0),
                versionName = obj.optString("versionName", ""),
                forceUpdate = obj.optBoolean("forceUpdate", false),
                updateLog = obj.optString("updateLog", ""),
                apkUrl = if (obj.isNull("apkUrl")) null else obj.getString("apkUrl"),
                apkSize = obj.optLong("apkSize", 0),
                apkMd5 = obj.optString("apkMd5", "")
            )
        } catch (t: Throwable) {
            Logger.e("Update", "解析更新信息失败", t)
            null
        }
    }

    /**
     * 获取公告列表(按时间倒序)。
     * @return 公告列表,网络失败返回空列表
     */
    fun fetchAnnouncements(): List<Announcement> {
        val url = "${Constants.API_BASE_URL}/api/announcement/list"
        val body = httpGet(url) ?: return emptyList()
        return try {
            val obj = JSONObject(body)
            val arr = obj.optJSONArray("announcements") ?: return emptyList()
            val list = mutableListOf<Announcement>()
            for (i in 0 until arr.length()) {
                val a = arr.optJSONObject(i) ?: continue
                list.add(
                    Announcement(
                        id = a.optLong("id", 0),
                        title = a.optString("title", ""),
                        content = a.optString("content", ""),
                        publishTime = a.optLong("publishTime", 0)
                    )
                )
            }
            list
        } catch (t: Throwable) {
            Logger.e("Update", "解析公告失败", t)
            emptyList()
        }
    }

    /**
     * 获取适配应用列表。
     * @return 适配应用列表,网络失败返回空列表
     */
    fun fetchApps(): List<AdaptApp> {
        val url = "${Constants.API_BASE_URL}/api/app/list"
        val body = httpGet(url) ?: return emptyList()
        return try {
            val obj = JSONObject(body)
            val arr = obj.optJSONArray("apps") ?: return emptyList()
            val list = mutableListOf<AdaptApp>()
            for (i in 0 until arr.length()) {
                val a = arr.optJSONObject(i) ?: continue
                list.add(
                    AdaptApp(
                        id = a.optLong("id", 0),
                        name = a.optString("name", ""),
                        iconUrl = if (a.isNull("iconUrl")) null else a.optString("iconUrl", null),
                        apkUrl = if (a.isNull("apkUrl")) null else a.optString("apkUrl", null),
                        apkSize = a.optLong("apkSize", 0),
                        publishTime = a.optLong("publishTime", 0)
                    )
                )
            }
            list
        } catch (t: Throwable) {
            Logger.e("Update", "解析适配应用列表失败", t)
            emptyList()
        }
    }

    /**
     * 获取字体列表。
     * @return 字体列表,网络失败返回空列表
     */
    fun fetchFonts(): List<FontItem> {
        val url = "${Constants.API_BASE_URL}/api/font/list"
        val body = httpGet(url) ?: return emptyList()
        return try {
            val obj = JSONObject(body)
            val arr = obj.optJSONArray("fonts") ?: return emptyList()
            val list = mutableListOf<FontItem>()
            for (i in 0 until arr.length()) {
                val f = arr.optJSONObject(i) ?: continue
                list.add(
                    FontItem(
                        id = f.optLong("id", 0),
                        name = f.optString("name", ""),
                        fontUrl = if (f.isNull("fontUrl")) null else f.optString("fontUrl", null),
                        publishTime = f.optLong("publishTime", 0)
                    )
                )
            }
            list
        } catch (t: Throwable) {
            Logger.e("Update", "解析字体列表失败", t)
            emptyList()
        }
    }

    /**
     * 下载文件到目标文件,实时回调下载进度(通用下载)。
     * @return 是否下载成功
     */
    fun downloadFile(
        url: String,
        dest: File,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): Boolean {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = CONNECT_TIMEOUT
            conn.readTimeout = READ_TIMEOUT
            conn.requestMethod = "GET"
            conn.instanceFollowRedirects = true
            conn.connect()

            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                FileOutputStream(dest).use { output ->
                    val buf = ByteArray(8192)
                    var downloaded = 0L
                    var n = input.read(buf)
                    while (n != -1) {
                        output.write(buf, 0, n)
                        downloaded += n
                        onProgress(downloaded, total)
                        n = input.read(buf)
                    }
                    output.flush()
                }
            }
            true
        } catch (t: Throwable) {
            Logger.e("Update", "下载文件失败", t)
            false
        }
    }

    /**
     * 下载 APK 到目标文件,实时回调下载进度。
     * @return 是否下载成功
     */
    fun downloadApk(
        url: String,
        dest: File,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): Boolean = downloadFile(url, dest, onProgress)

    /**
     * 调起系统安装器安装 APK。
     * @return true 表示成功调起系统安装界面
     */
    fun installApk(context: Context, apkPath: String): Boolean {
        val apkFile = File(apkPath)
        if (!apkFile.exists()) return false

        return try {
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            true
        } catch (t: Throwable) {
            Logger.e("Update", "调起系统安装器失败", t)
            false
        }
    }

    /** APK 下载目标文件 */
    fun apkFile(context: Context): File {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        return File(dir, "update.apk")
    }

    // ---- 基础 GET ----
    private fun httpGet(url: String): String? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = CONNECT_TIMEOUT
            conn.readTimeout = READ_TIMEOUT
            conn.requestMethod = "GET"
            conn.instanceFollowRedirects = true
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_OK) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (t: Throwable) {
            Logger.e("Update", "请求失败: $url", t)
            null
        }
    }
}
