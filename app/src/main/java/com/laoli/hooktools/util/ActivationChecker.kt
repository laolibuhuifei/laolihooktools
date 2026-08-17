package com.laoli.hooktools.util

import android.content.Context
import com.laoli.hooktools.ui.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 激活检测器(hook 自身方法版,最可靠)。
 *
 * 1. 模块自身检测:调用 [MainActivity.isModuleActive]。
 *    - 默认返回 false
 *    - 模块被 hook 时,[com.laoli.hooktools.hook.MomentHook] 用
 *      XC_MethodReplacement.returnConstant(true) 替换该方法 → 返回 true
 *    - 直接验证 hook 机制是否生效,不依赖跨进程通信/文件权限/反射类加载
 *
 * 2. 好友圈检测:读同进程 SharedPreferences(由 ActiveProvider 写入)。
 *    好友圈进程被 hook 后,通过 ContentProvider 跨包调用模块 Provider 写入激活状态。
 *
 * 3. 框架检测:检查 LSPosed Manager / Xposed Installer 包名是否安装。
 */
object ActivationChecker {

    /** 模块自身被框架 hook 的标记(兼容旧 prefs 检测,调试用) */
    const val SELF_HOOKED_FLAG = "self_hooked"

    /** LSPosed Manager 包名(新版) */
    private const val LSPOSED_MANAGER_PKG = "org.lsposed.manager"
    /** LSPosed Manager 包名(旧版/隐藏版) */
    private const val LSPOSED_MANAGER_PKG_LEGACY = "com.android.developer.xposed"
    /** 经典 Xposed Installer 包名 */
    private const val XPOSED_INSTALLER_PKG = "de.robv.android.xposed.installer"

    enum class State {
        /** 未安装 Xposed 框架 */
        NO_FRAMEWORK,
        /** 框架已装,但模块未被加载/启用 */
        FRAMEWORK_ONLY,
        /** 模块已在自身进程加载,但未在好友圈中生效 */
        MODULE_LOADED_NOT_TARGET,
        /** 完全激活:自身 + 好友圈都生效 */
        ACTIVE
    }

    data class Result(
        val state: State,
        val frameworkInstalled: Boolean,
        val moduleLoaded: Boolean,
        val targetActive: Boolean,
        val lastActiveTime: Long,
        val moduleVersion: String?
    ) {
        val isFullyActive: Boolean get() = state == State.ACTIVE
    }

    /**
     * 执行激活检测。
     *
     * @param activity 模块的 MainActivity,用于调用 isModuleActive()
     */
    fun check(activity: MainActivity): Result {
        val frameworkInstalled = isFrameworkInstalled(activity)

        // 模块自身检测:调用 isModuleActive()(被 hook 后返回 true)
        val moduleLoaded = activity.isModuleActive()

        // 好友圈检测:同进程 SharedPreferences(ActiveProvider 写入)
        val targetSp = activity.getSharedPreferences(Constants.PREFS_ACTIVE, Context.MODE_PRIVATE)
        val targetActive = targetSp.getBoolean(Constants.KEY_ACTIVE_FLAG, false)
        val lastTime = targetSp.getLong(Constants.KEY_ACTIVE_TIME, 0L)
        val version = targetSp.getString(Constants.KEY_ACTIVE_VERSION, null)

        val state = when {
            targetActive && moduleLoaded -> State.ACTIVE
            moduleLoaded -> State.MODULE_LOADED_NOT_TARGET
            frameworkInstalled -> State.FRAMEWORK_ONLY
            else -> State.NO_FRAMEWORK
        }
        return Result(state, frameworkInstalled, moduleLoaded, targetActive, lastTime, version)
    }

    /**
     * 检测 Xposed/LSPosed 框架是否安装。
     * 检查 Manager 应用包名(LSPosed 下 XposedBridge 类不在应用 classpath,不能用反射)。
     */
    private fun isFrameworkInstalled(context: Context): Boolean {
        val pm = context.packageManager
        val pkgs = arrayOf(LSPOSED_MANAGER_PKG, LSPOSED_MANAGER_PKG_LEGACY, XPOSED_INSTALLER_PKG)
        for (pkg in pkgs) {
            try {
                pm.getPackageInfo(pkg, 0)
                return true
            } catch (_: Throwable) {
                // 继续检查下一个
            }
        }
        return false
    }

    /** 格式化最近激活时间 */
    fun formatTime(timeMillis: Long): String {
        if (timeMillis <= 0L) return ""
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timeMillis))
    }
}
