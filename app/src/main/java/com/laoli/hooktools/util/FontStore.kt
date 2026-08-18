package com.laoli.hooktools.util

import android.os.Environment
import java.io.File

/**
 * 已下载字体管理。
 *
 * 下载的字体统一存放在 /sdcard/laoli_hooktools/fonts/ 目录,
 * 供「自定义字体」功能选择使用。
 */
object FontStore {

    /** 字体存放目录(/sdcard/laoli_hooktools/fonts) */
    fun fontsDir(): File {
        val dir = File(
            Environment.getExternalStorageDirectory(),
            "laoli_hooktools/${Constants.FONT_DOWNLOAD_DIR_NAME}"
        )
        if (!dir.exists()) dir.mkdirs()
        try {
            dir.setReadable(true, false)
            dir.setExecutable(true, false)
            dir.setWritable(true, false)
        } catch (_: Throwable) {
        }
        return dir
    }

    /** 列出已下载的字体文件(按名称排序) */
    fun listFonts(): List<File> {
        val files = fontsDir().listFiles() ?: return emptyList()
        return files
            .filter {
                it.isFile && (it.name.lowercase().endsWith(".ttf") || it.name.lowercase().endsWith(".otf"))
            }
            .sortedBy { it.name }
    }

    /** 根据字体名称生成目标文件(过滤非法字符,避免路径问题) */
    fun targetFile(name: String, ext: String): File {
        val safe = name.replace(Regex("[^a-zA-Z0-9\u4e00-\u9fa5_\\-]"), "_")
        val extNorm = if (ext.startsWith(".")) ext else ".$ext"
        return File(fontsDir(), safe + extNorm)
    }

    /** 从字体 URL 推断扩展名(.ttf/.otf),默认 .ttf */
    fun extFromUrl(url: String?): String {
        val lower = url?.lowercase() ?: return ".ttf"
        return when {
            lower.contains(".otf") -> ".otf"
            else -> ".ttf"
        }
    }
}
