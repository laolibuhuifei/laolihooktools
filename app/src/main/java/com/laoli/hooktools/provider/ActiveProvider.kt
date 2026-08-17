package com.laoli.hooktools.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.laoli.hooktools.util.Constants

/**
 * 激活状态回传 ContentProvider。
 *
 * 工作流程:
 * - 好友圈进程被 hook 后,通过 contentResolver.insert() 写入激活状态
 * - 模块 UI 进程通过 query() 读取激活状态
 *
 * 这比 XSharedPreferences 跨包读取更可靠,不依赖文件权限。
 * 数据存在模块自身 SharedPreferences 里(authority 是模块包名,好友圈进程可跨包调用)。
 */
class ActiveProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.laoli.hooktools.provider"
        const val PATH_ACTIVE = "active"
        const val CODE_ACTIVE = 1

        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_ACTIVE")
    }

    private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(AUTHORITY, PATH_ACTIVE, CODE_ACTIVE)
    }

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(Constants.PREFS_ACTIVE, Context.MODE_PRIVATE)

    override fun onCreate(): Boolean = true

    /**
     * 好友圈进程调用 insert 写入激活状态。
     * 存到模块自身 prefs(因为 context 是模块的)。
     */
    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        if (uriMatcher.match(uri) != CODE_ACTIVE) return null
        val ctx = context ?: return null
        values?.let { cv ->
            prefs(ctx).edit().apply {
                if (cv.containsKey("active")) putBoolean(Constants.KEY_ACTIVE_FLAG, cv.getAsBoolean("active"))
                if (cv.containsKey("time")) putLong(Constants.KEY_ACTIVE_TIME, cv.getAsLong("time"))
                if (cv.containsKey("version")) putString(Constants.KEY_ACTIVE_VERSION, cv.getAsString("version"))
            }.commit()
        }
        ctx.contentResolver.notifyChange(uri, null)
        return uri
    }

    /**
     * 模块 UI 调用 query 读取激活状态。
     */
    override fun query(
        uri: Uri, projection: Array<String>?,
        selection: String?, selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        if (uriMatcher.match(uri) != CODE_ACTIVE) return null
        val ctx = context ?: return null
        val sp = prefs(ctx)
        val cursor = MatrixCursor(arrayOf("active", "time", "version"))
        cursor.addRow(arrayOf(
            if (sp.getBoolean(Constants.KEY_ACTIVE_FLAG, false)) 1 else 0,
            sp.getLong(Constants.KEY_ACTIVE_TIME, 0L),
            sp.getString(Constants.KEY_ACTIVE_VERSION, null)
        ))
        return cursor
    }

    override fun getType(uri: Uri): String? = when (uriMatcher.match(uri)) {
        CODE_ACTIVE -> "vnd.android.cursor.dir/$PATH_ACTIVE"
        else -> null
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int {
        insert(uri, values)
        return 1
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
}
