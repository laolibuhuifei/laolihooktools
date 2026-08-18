package com.laoli.hooktools.util

import android.app.Activity
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.laoli.hooktools.prefs.PrefsManager
import java.io.File

/**
 * 模块自身界面字体管理。
 *
 * 读取模块字体配置,将自定义字体应用到模块所有界面的 TextView。
 */
object ModuleFont {

    @Volatile private var cachedPath: String? = null
    @Volatile private var cachedTypeface: Typeface? = null

    /** 将模块字体应用到整个 Activity 的 View 树 */
    fun applyToActivity(activity: Activity) {
        try {
            val prefs = PrefsManager.get(activity)
            if (!prefs.isModuleFontEnabled()) return
            val path = prefs.getModuleFontPath() ?: return
            val tf = typefaceFor(path) ?: return
            val root = activity.window?.decorView ?: return
            applyToView(root, tf)
        } catch (_: Throwable) {
        }
    }

    /** 清除字体缓存(设置变更后调用) */
    fun invalidate() {
        cachedPath = null
        cachedTypeface = null
    }

    private fun typefaceFor(path: String): Typeface? {
        if (cachedPath == path && cachedTypeface != null) return cachedTypeface
        return try {
            val file = File(path)
            if (!file.exists()) {
                cachedPath = null
                cachedTypeface = null
                null
            } else {
                val tf = Typeface.createFromFile(file)
                cachedPath = path
                cachedTypeface = tf
                tf
            }
        } catch (_: Throwable) {
            cachedPath = null
            cachedTypeface = null
            null
        }
    }

    /** 递归设置 View 树中所有 TextView 的字体 */
    private fun applyToView(view: View, tf: Typeface) {
        if (view is TextView) {
            view.typeface = tf
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyToView(view.getChildAt(i), tf)
            }
        }
    }
}
