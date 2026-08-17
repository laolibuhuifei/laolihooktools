package com.laoli.hooktools.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import java.io.File
import java.io.FileOutputStream

/**
 * 图片管理:把用户从相册选择的图片拷贝到外部存储目录,
 * 供好友圈进程读取(模块私有目录 /data/data/... 其他进程无权访问)。
 *
 * 使用 /sdcard/Android/data/com.laoli.hooktools/files/,这个目录:
 * - 不需要存储权限(应用专属外部目录)
 * - 其他应用可读(Android 10 之前默认;Android 10+ 需要 legacy storage)
 */
object ImageManager {

    /**
     * 模块图片存放目录。
     * 使用 /sdcard/laoli_hooktools/(公共目录,所有应用可读)。
     * 需要 WRITE_EXTERNAL_STORAGE 权限(Android 10 之前),
     * Android 10+ 用 MediaStore 或 legacy storage。
     */
    private fun imagesDir(context: Context): File {
        val dir = File(Environment.getExternalStorageDirectory(), "laoli_hooktools")
        if (!dir.exists()) dir.mkdirs()
        // 设置目录世界可读可执行
        try {
            dir.setReadable(true, false)
            dir.setExecutable(true, false)
            dir.setWritable(true, false)
        } catch (_: Throwable) {
        }
        return dir
    }

    /**
     * 将 [uri] 指向的图片拷贝到外部存储目录,返回目标文件绝对路径。
     * @param maxBytes 最大字节数,超过返回 null
     */
    fun importUri(context: Context, uri: Uri, targetName: String, maxBytes: Long = 2 * 1024 * 1024): String? {
        return try {
            // 大小校验
            val size = getFileSize(context, uri)
            if (size > maxBytes) return null

            val target = File(imagesDir(context), "$targetName.png")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            // 设置文件世界可读(确保好友圈进程能读)
            try {
                target.setReadable(true, false)
            } catch (_: Throwable) {
            }

            // 用 root 强制设置 644 权限(确保好友圈进程可读)
            // 好友圈通常有外部存储读权限,但保险起见
            try {
                com.laoli.hooktools.util.RootUtil.execRoot("chmod 644 ${target.absolutePath}")
            } catch (_: Throwable) {
            }

            target.absolutePath
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * 将小天才相册返回的文件路径 [sourcePath] 拷贝到外部存储目录,返回目标文件绝对路径。
     * @param maxBytes 最大字节数,超过返回 null
     */
    fun importPath(context: Context, sourcePath: String, targetName: String, maxBytes: Long = 2 * 1024 * 1024): String? {
        return try {
            val src = File(sourcePath)
            if (!src.exists()) return null
            if (src.length() > maxBytes) return null

            val target = File(imagesDir(context), "$targetName.png")
            src.copyTo(target, overwrite = true)

            try {
                target.setReadable(true, false)
            } catch (_: Throwable) {
            }

            try {
                RootUtil.execRoot("chmod 644 ${target.absolutePath}")
            } catch (_: Throwable) {
            }

            target.absolutePath
        } catch (t: Throwable) {
            null
        }
    }

    /** 解码图片尺寸(用于校验) */
    fun decodeSize(path: String): Pair<Int, Int>? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, opts)
            opts.outWidth to opts.outHeight
        } catch (t: Throwable) {
            null
        }
    }

    /** 删除已设置的图片 */
    fun clear(context: Context, targetName: String) {
        try {
            File(imagesDir(context), "$targetName.png").delete()
        } catch (_: Throwable) {
        }
    }

    /** 判断图片文件是否存在 */
    fun exists(context: Context, targetName: String): Boolean {
        return File(imagesDir(context), "$targetName.png").exists()
    }

    private fun getFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                afd.length
            } ?: 0L
        } catch (t: Throwable) {
            0L
        }
    }
}
