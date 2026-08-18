package com.laoli.hooktools.hook

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.res.XResources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import de.robv.android.xposed.IXposedHookInitPackageResources
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_InitPackageResources
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.laoli.hooktools.util.ActivationChecker
import com.laoli.hooktools.util.Constants
import com.laoli.hooktools.util.EditedCommentStore
import com.laoli.hooktools.util.EditedMomentStore
import com.laoli.hooktools.util.FavoriteStore
import com.laoli.hooktools.util.Logger
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

/**
 * Xposed 模块入口(超详细日志版)。
 */
class MomentHook :
    IXposedHookZygoteInit,
    IXposedHookLoadPackage,
    IXposedHookInitPackageResources {

    companion object {
        private const val TAG = "Hook"
        @Volatile
        private var modulePath: String = ""

        /** 缓存的图片路径(从配置读取),key = 资源名 */
        private val replacements = mutableMapOf<String, String>()

        /** 缓存的 string 替换值(从配置读取),key = 资源名 */
        private val stringReplacements = mutableMapOf<String, String>()

        /** View ID(十六进制) -> 资源名 映射,用于按 View id 兜底替换 */
        private val viewIdToResName = mutableMapOf<Int, String>()

        /** 诊断:已记录的好友圈 ImageView id(去重,用于确认目标 id 是否存在) */
        private val seenImageIds = HashSet<Int>()

        /** 诊断:已记录的好友圈 string id(去重,用于确认目标 id 是否被读取) */
        private val seenStringIds = HashSet<Int>()

        /** string 替换对应 TextView 的 View id -> string 资源名 映射 */
        private val stringViewIdToResName = mutableMapOf<Int, String>()

        /** 缓存的 color 替换值(已解析为 int),key = 资源名 */
        private val colorReplacements = mutableMapOf<String, Int>()

        /** color 替换对应 TextView 的 View id -> color 资源名 映射 */
        private val colorViewIdToResName = mutableMapOf<Int, String>()

        /** 时间详细显示是否启用 */
        @Volatile
        private var timeDetailEnabled = false

        /** 防删除(只防同步/他人删除)是否启用 */
        @Volatile
        private var antiDeleteEnabled = false

        /** 链接自动跳转是否启用 */
        @Volatile
        private var linkJumpEnabled = false

        /** 自定义字体是否启用 */
        @Volatile
        private var fontEnabled = false

        /** 自定义字体文件路径 */
        @Volatile
        private var fontPath: String? = null

        /** 已加载的自定义 Typeface(缓存,避免每次都 createFromFile) */
        @Volatile
        private var customTypeface: Typeface? = null

        /** 运动:能量值修改是否启用 */
        @Volatile
        private var sportEnergyEnabled = false

        /** 运动:自定义能量值 */
        @Volatile
        private var sportEnergyValue: Int? = null

        /** 运动:一键红环是否启用 */
        @Volatile
        private var sportRedRingEnabled = false

        /** 运动:红环数量(1 个红环 = 625,level = 数量 * 625) */
        @Volatile
        private var sportRedRingCount = 1

        /** 运动:自定义字体是否启用 */
        @Volatile
        private var sportFontEnabled = false

        /** 运动:自定义字体文件路径 */
        @Volatile
        private var sportFontPath: String? = null

        /** 运动:已加载的自定义 Typeface(缓存) */
        @Volatile
        private var sportCustomTypeface: Typeface? = null

        /** 运动:自定义头像是否启用 */
        @Volatile
        private var sportAvatarEnabled = false

        /** 运动:自定义头像图片路径 */
        @Volatile
        private var sportAvatarPath: String? = null

        /** URL 匹配正则(http/https/www) */
        private val URL_PATTERN = Pattern.compile(
            "((https?://|www\\.)[\\w\\-._~:/?#\\[\\]@!&'()*+,;=%]+)",
            Pattern.CASE_INSENSITIVE
        )

        /** 被拦截删除的动态 createTime 集合(用于给这些动态的时间加标记) */
        private val interceptedCreateTimes = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

        /** 防删除标记文字 */
        private const val ANTI_DELETE_MARK = "[已成功拦截删除的动态😋] "

        private var recordDebugCount = 0

        /** 当前长按的动态(供弹窗注入编辑按钮时读取) */
        @Volatile
        private var pendingEditMoment: Any? = null

        /** 目标应用(好友圈)的 Application,用于取 classLoader 反序列化 content */
        @Volatile
        private var targetApp: Application? = null

        /** 当前 onResume 的 Activity(用于弹 Dialog 时兜底获取有效 context) */
        @Volatile
        private var currentActivity: Activity? = null

        /** 最近的主列表适配器(用于刷新) */
        private var lastMomentAdapter: java.lang.ref.WeakReference<Any>? = null

        /** 最近一次 getLikeTotal 命中的编辑点赞数(供 setCount 绕过 99+ 模糊) */
        @Volatile
        private var lastEditedLikeCount: Int? = null
    }

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        modulePath = startupParam.modulePath
        // Logger 已改为仅输出 Logcat(不再写文件),initZygote 阶段调用是安全的。
        Logger.log(TAG, "======== initZygote ========")
        Logger.log(TAG, "modulePath = $modulePath")
        Logger.log(TAG, "process = ${android.os.Process.myPid()}, uid = ${android.os.Process.myUid()}")
        Logger.log(TAG, "target package = ${Constants.TARGET_PACKAGE}")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 先判断包名:非目标包/模块包直接返回。
        // 避免在 system_server 等系统早期进程写文件日志导致开机卡第一屏。
        val pkg = lpparam.packageName
        if (pkg != Constants.TARGET_PACKAGE && pkg != Constants.SPORT_PACKAGE && pkg != Constants.MODULE_PACKAGE) {
            return
        }

        Logger.log(TAG, "======== handleLoadPackage ========")
        Logger.log(TAG, "packageName = $pkg")
        Logger.log(TAG, "processName = ${lpparam.processName}")
        Logger.log(TAG, "classLoader = ${lpparam.classLoader}")
        Logger.log(TAG, "appInfo.sourceDir = ${lpparam.appInfo?.sourceDir}")
        Logger.log(TAG, "isFirstApplication = ${lpparam.isFirstApplication}")

        when (pkg) {
            Constants.TARGET_PACKAGE -> {
                Logger.log(TAG, ">>> 进入目标进程:好友圈")
                handleTarget(lpparam)
            }
            Constants.SPORT_PACKAGE -> {
                Logger.log(TAG, ">>> 进入运动进程")
                handleSportTarget(lpparam)
            }
            else -> {
                Logger.log(TAG, ">>> 进入模块自身进程")
                handleSelf(lpparam)
            }
        }
    }

    override fun handleInitPackageResources(resparam: XC_InitPackageResources.InitPackageResourcesParam) {
        // 先判断包名:非目标包直接返回,避免系统早期进程写文件日志阻塞。
        if (resparam.packageName != Constants.TARGET_PACKAGE) {
            return
        }

        Logger.log(TAG, "======== handleInitPackageResources ========")
        Logger.log(TAG, "packageName = ${resparam.packageName}")
        Logger.log(TAG, "res class = ${resparam.res.javaClass.name}")
        Logger.log(TAG, "res = $resparam.res")

        Logger.log(TAG, ">>> 开始注册资源替换(setReplacement 方式)")
        registerReplacements(resparam.res)
    }

    // ---------- 目标进程:好友圈 ----------

    private fun handleTarget(lpparam: XC_LoadPackage.LoadPackageParam) {
        Logger.log(TAG, ">>> handleTarget 开始")

        // 1. 从配置加载替换映射
        Logger.log(TAG, ">>> 步骤1: 加载配置")
        loadReplacements()

        // 2. hook Application.onCreate
        Logger.log(TAG, ">>> 步骤2: 注册 Application.onCreate hook")
        try {
            XposedHelpers.findAndHookMethod(
                Application::class.java,
                "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val app = param.thisObject as? Application ?: return
                        Logger.log(TAG, "========== Application.onCreate 被触发 ==========")
                        Logger.log(TAG, "application class = ${app.javaClass.name}")
                        Logger.log(TAG, "packageName = ${app.packageName}")
                        Logger.log(TAG, "processName = ${getCurrentProcessName(app)}")
                        Logger.log(TAG, "dataDir = ${app.applicationInfo.dataDir}")
                        Logger.log(TAG, "sourceDir = ${app.applicationInfo.sourceDir}")

                        // 写激活回传
                        Logger.log(TAG, ">>> 步骤3: 写激活回传")
                        writeActivationFlag(app)

                        // Toast 注入成功
                        Logger.log(TAG, ">>> 步骤4: 弹出 Toast")
                        showInjectionToast(app)

                        // hook Resources 替换图片
                        Logger.log(TAG, ">>> 步骤5: hook Resources 替换图片")
                        hookResourcesForDrawable(app)

                        // hook 按 View id 替换图片(ImageView.setImageDrawable)
                        Logger.log(TAG, ">>> 步骤6: hook 按 View id 替换图片")
                        hookViewIdReplacement(app)

                        // hook 字符串资源替换
                        Logger.log(TAG, ">>> 步骤7: hook 字符串资源替换")
                        hookStringReplacement(app)

                        // hook 颜色资源替换
                        Logger.log(TAG, ">>> 步骤8: hook 颜色资源替换")
                        hookColorReplacement(app)

                        // hook 时间详细显示
                        Logger.log(TAG, ">>> 步骤9: hook 时间详细显示")
                        hookTimeDetail(app)

                        // hook 防删除(只防同步/他人删除)
                        Logger.log(TAG, ">>> 步骤11: hook 防删除")
                        hookAntiDelete(app)

                        // hook 动态编辑(长按文本动态弹窗加编辑按钮)
                        Logger.log(TAG, ">>> 步骤12: hook 动态编辑")
                        hookMomentEdit(app)

                        // hook 链接自动跳转
                        Logger.log(TAG, ">>> 步骤13: hook 链接自动跳转")
                        hookLinkJump(app)

                        // hook 点赞数量修改 + 详情页点赞用户名
                        Logger.log(TAG, ">>> 步骤14: hook 点赞数量/用户名")
                        hookLikeDisplay(app)

                        // hook 保存用户头像(长按头像)
                        Logger.log(TAG, ">>> 步骤15: hook 保存头像")
                        hookSaveAvatar(app)

                        // hook 评论修改(长按评论 -> 修改颜色/下划线/评论者名称)
                        Logger.log(TAG, ">>> 步骤16: hook 评论修改")
                        hookCommentEdit(app)

                        // hook 自定义字体
                        Logger.log(TAG, ">>> 步骤17: hook 自定义字体")
                        hookFont(app)
                    }
                }
            )
            Logger.log(TAG, "Application.onCreate hook 注册成功")
        } catch (t: Throwable) {
            Logger.e(TAG, "Application.onCreate hook 注册失败", t)
        }
    }

    // ---------- 目标进程:运动应用 ----------

    private fun handleSportTarget(lpparam: XC_LoadPackage.LoadPackageParam) {
        Logger.log(TAG, ">>> handleSportTarget 开始")

        // 读取运动配置(能量/红环/字体)
        loadSportConfig()

        try {
            XposedHelpers.findAndHookMethod(
                Application::class.java,
                "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val app = param.thisObject as? Application ?: return
                        Logger.log(TAG, "========== 运动 Application.onCreate 被触发 ==========")
                        Logger.log(TAG, "application class = ${app.javaClass.name}")

                        // 能量值 + 一键红环
                        hookSportEnergyAndRing(app)

                        // 自定义字体
                        hookSportFont(app)

                        // 自定义虚拟形象头像
                        hookSportAvatar(app)
                    }
                }
            )
            Logger.log(TAG, "运动 Application.onCreate hook 注册成功")
        } catch (t: Throwable) {
            Logger.e(TAG, "运动 Application.onCreate hook 注册失败", t)
        }
    }

    /** 读取运动相关配置(能量/红环/字体),直接读 config.json */
    private fun loadSportConfig() {
        val json = try {
            val file = File("/sdcard/laoli_hooktools/config.json")
            if (file.exists()) org.json.JSONObject(file.readText()) else org.json.JSONObject()
        } catch (t: Throwable) {
            org.json.JSONObject()
        }

        val energyJson = json.optJSONObject("sport_energy")
        sportEnergyEnabled = energyJson?.optBoolean("enabled", false) ?: false
        sportEnergyValue = if (energyJson != null && energyJson.has("value")) {
            energyJson.optInt("value")
        } else null

        val ringJson = json.optJSONObject("sport_red_ring")
        sportRedRingEnabled = ringJson?.optBoolean("enabled", false) ?: false
        sportRedRingCount = ringJson?.optInt("count", 1)?.coerceIn(1, 20) ?: 1

        val fontJson = json.optJSONObject("sport_font")
        sportFontEnabled = fontJson?.optBoolean("enabled", false) ?: false
        sportFontPath = fontJson?.optString("path", null)
        sportCustomTypeface = null

        val avatarJson = json.optJSONObject("sport_avatar")
        sportAvatarEnabled = avatarJson?.optBoolean("enabled", false) ?: false
        sportAvatarPath = avatarJson?.optString("path", null)

        Logger.log(TAG, "运动配置: 能量 enabled=$sportEnergyEnabled value=$sportEnergyValue, 红环=$sportRedRingEnabled, 字体 enabled=$sportFontEnabled path=$sportFontPath, 头像 enabled=$sportAvatarEnabled path=$sportAvatarPath")
    }

    /** hook 运动应用能量值与一键红环(改 HomeBean getter 返回值) */
    private fun hookSportEnergyAndRing(app: Application) {
        Logger.log(TAG, ">>> hookSportEnergyAndRing 开始")

        val homeBeanClass = try {
            XposedHelpers.findClass("com.xtc.sport.home.bean.HomeBean", app.classLoader)
        } catch (t: Throwable) {
            Logger.e(TAG, "找不到 HomeBean", t)
            return
        }

        // 能量值修改:统一覆盖多个能量相关 getter
        if (sportEnergyEnabled && sportEnergyValue != null) {
            val value = sportEnergyValue!!
            val methods = arrayOf(
                "getCurrentEngery",
                "getCurrentLevelEngery",
                "getUpgradeEngery",
                "getEnergyLimit",
                "getUpperLimit",
                "getDayLimit"
            )
            for (method in methods) {
                try {
                    XposedHelpers.findAndHookMethod(
                        homeBeanClass,
                        method,
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                param.result = value
                            }
                        }
                    )
                    Logger.log(TAG, "运动能量 hook: HomeBean.$method -> $value")
                } catch (t: Throwable) {
                    Logger.log(TAG, "运动能量 hook 跳过(方法可能不存在): $method")
                }
            }
        }

        // 一键红环:运动环是 5 进制等级编码,第 5 级(ic_class_5,红色)需要 level >= 5^4=625。
        // 直接改 LevelLayout 的渲染参数,不影响虚拟形象与等级数字文本。
        if (sportRedRingEnabled) {
            val targetLevel = sportRedRingCount * 625
            val levelLayoutClass = try {
                XposedHelpers.findClass("com.xtc.sport.home.widget.LevelLayout", app.classLoader)
            } catch (t: Throwable) {
                Logger.e(TAG, "一键红环: 找不到 LevelLayout", t)
                null
            }
            if (levelLayoutClass != null) {
                // setLevel 有最高位溢出处理,可显示任意数量红环,强制为 targetLevel。
                try {
                    XposedHelpers.findAndHookMethod(
                        levelLayoutClass,
                        "setLevel",
                        Int::class.javaPrimitiveType,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                param.args[0] = targetLevel
                            }
                        }
                    )
                    Logger.log(TAG, "一键红环 hook: LevelLayout.setLevel -> $targetLevel ($sportRedRingCount 个红环)")
                } catch (t: Throwable) {
                    Logger.e(TAG, "一键红环 hook 失败: setLevel", t)
                }

                // e 是动画增量更新,缺少最高位溢出处理,红环数量 > 4 时会算成 0 把红环清空。
                // 直接跳过 e,保持 setLevel 设置的红环状态。
                try {
                    XposedHelpers.findAndHookMethod(
                        levelLayoutClass,
                        "e",
                        Int::class.javaPrimitiveType,
                        XC_MethodReplacement.DO_NOTHING
                    )
                    Logger.log(TAG, "一键红环 hook: LevelLayout.e 已跳过")
                } catch (t: Throwable) {
                    Logger.e(TAG, "一键红环 hook 失败: e", t)
                }
            }

            // 虚拟形象周围的光环也达到最高等级(红色)。
            // 核心:等级字段 j 通过合成方法 g(StaticVirtualSelf,int) 设置,j>=625 才会同时
            // 显示红色普通光环(k/l)以及第 5 级特殊光环 ivAbove5/ivBelow5。
            val virtualSelfClass = try {
                XposedHelpers.findClass("com.xtc.sport.home.widget.virtualself.StaticVirtualSelf", app.classLoader)
            } catch (t: Throwable) {
                Logger.e(TAG, "一键红环: 找不到 StaticVirtualSelf", t)
                null
            }
            if (virtualSelfClass != null) {
                // 1) 直接改等级字段 j = 625(最根本,一次性让所有光环变红)
                try {
                    XposedHelpers.findAndHookMethod(
                        virtualSelfClass,
                        "g",
                        virtualSelfClass,
                        Int::class.javaPrimitiveType,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                param.args[1] = 625
                            }
                        }
                    )
                    Logger.log(TAG, "一键红环 hook: StaticVirtualSelf.g -> 625 (等级字段)")
                } catch (t: Throwable) {
                    Logger.e(TAG, "一键红环 hook 失败: StaticVirtualSelf.g", t)
                }
                // 2) 兜底:普通光环资源选择 k/l 也强制 625
                for (method in arrayOf("k", "l")) {
                    try {
                        XposedHelpers.findAndHookMethod(
                            virtualSelfClass,
                            method,
                            Int::class.javaPrimitiveType,
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam) {
                                    param.args[0] = 625
                                }
                            }
                        )
                        Logger.log(TAG, "一键红环 hook: StaticVirtualSelf.$method -> 625 (普通光环)")
                    } catch (t: Throwable) {
                        Logger.e(TAG, "一键红环 hook 失败: StaticVirtualSelf.$method", t)
                    }
                }
            }

            // 虚拟形象光环的真正渲染路径是 PAG 动画:
            // 混淆类 e.o.u.h.s.h.b 的 h(int) 根据等级选 level1~5.pag,level >= 625 才是 level5.pag(红色)。
            try {
                val pagSelfClass = XposedHelpers.findClass("e.o.u.h.s.h.b", app.classLoader)
                XposedHelpers.findAndHookMethod(
                    pagSelfClass,
                    "h",
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            param.args[0] = 625
                        }
                    }
                )
                Logger.log(TAG, "一键红环 hook: e.o.u.h.s.h.b.h -> 625 (红色光环 PAG)")
            } catch (t: Throwable) {
                Logger.e(TAG, "一键红环 hook 失败: e.o.u.h.s.h.b.h", t)
            }
        }

        Logger.log(TAG, ">>> hookSportEnergyAndRing 完成")
    }

    /** hook 运动应用自定义字体(拦截 TextView 构造 + setText 兜底) */
    private fun hookSportFont(app: Application) {
        Logger.log(TAG, ">>> hookSportFont 开始")
        if (!sportFontEnabled) {
            Logger.log(TAG, ">>> 运动字体未启用,跳过")
            return
        }

        val typeface = resolveSportTypeface()
        if (typeface == null) {
            Logger.e(TAG, ">>> 运动 Typeface 加载失败,跳过")
            return
        }

        val constructors: List<Array<Class<*>>> = listOf(
            arrayOf(Context::class.java),
            arrayOf(Context::class.java, android.util.AttributeSet::class.java),
            arrayOf(Context::class.java, android.util.AttributeSet::class.java, java.lang.Integer.TYPE)
        )

        for (sig in constructors) {
            try {
                XposedHelpers.findAndHookConstructor(
                    TextView::class.java,
                    *sig,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val tv = param.thisObject as? TextView ?: return
                            val tf = sportCustomTypeface ?: return
                            try {
                                tv.typeface = tf
                            } catch (_: Throwable) {
                            }
                        }
                    }
                )
                Logger.log(TAG, ">>> hookSportFont: TextView 构造器 hook 成功: ${sig.joinToString { it.name }}")
            } catch (t: Throwable) {
                Logger.e(TAG, ">>> hookSportFont: 构造器 hook 失败: ${t.message}", t)
            }
        }

        try {
            XposedHelpers.findAndHookMethod(
                TextView::class.java,
                "setText",
                CharSequence::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val tv = param.thisObject as? TextView ?: return
                        val tf = sportCustomTypeface ?: return
                        try {
                            if (tv.typeface !== tf) {
                                tv.typeface = tf
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
            )
            Logger.log(TAG, ">>> hookSportFont: TextView.setText hook 成功")
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookSportFont: setText hook 失败", t)
        }

        Logger.log(TAG, ">>> hookSportFont 完成")
    }

    /**
     * hook 运动应用自定义虚拟形象头像。
     *
     * 虚拟形象头像最终由 PAG 骨骼动画渲染,头像 Bitmap 通过
     * PAGImage.FromBitmap(Bitmap) 送入 libpag(全应用唯一调用点,
     * 见 DynamicVirtualSelf$playAnim$1.invokeSuspend)。这里拦截该方法,
     * 把传入的原始头像 Bitmap 换成用户从相册选择的图片。
     */
    private fun hookSportAvatar(app: Application) {
        Logger.log(TAG, ">>> hookSportAvatar 开始")
        if (!sportAvatarEnabled) {
            Logger.log(TAG, ">>> 运动头像未启用,跳过")
            return
        }
        val path = sportAvatarPath
        if (path.isNullOrEmpty()) {
            Logger.log(TAG, ">>> 运动头像路径为空,跳过")
            return
        }

        try {
            val pagImageClass = XposedHelpers.findClass("org.libpag.PAGImage", app.classLoader)
            XposedHelpers.findAndHookMethod(
                pagImageClass,
                "FromBitmap",
                Bitmap::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val bmp = BitmapFactory.decodeFile(path)
                            if (bmp != null) {
                                param.args[0] = bmp
                                Logger.log(TAG, ">>> hookSportAvatar: 已替换头像 Bitmap ${bmp.width}x${bmp.height}")
                            } else {
                                Logger.log(TAG, ">>> hookSportAvatar: 头像解码失败,保持原头像")
                            }
                        } catch (t: Throwable) {
                            Logger.e(TAG, ">>> hookSportAvatar: 解码头像失败", t)
                        }
                    }
                }
            )
            Logger.log(TAG, ">>> hookSportAvatar: PAGImage.FromBitmap hook 注册成功")
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookSportAvatar: hook 注册失败", t)
        }

        Logger.log(TAG, ">>> hookSportAvatar 完成")
    }

    /** 从配置路径加载运动自定义 Typeface(带缓存) */
    private fun resolveSportTypeface(): Typeface? {
        sportCustomTypeface?.let { return it }
        if (!sportFontEnabled) return null
        val path = sportFontPath ?: return null
        return try {
            val file = File(path)
            if (!file.exists()) {
                Logger.e(TAG, ">>> 运动字体文件不存在: $path")
                null
            } else {
                Typeface.createFromFile(file).also {
                    sportCustomTypeface = it
                    Logger.log(TAG, ">>> 运动字体加载成功: $path")
                }
            }
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> 运动字体加载失败", t)
            null
        }
    }

    /**
     * 加载配置中的图片替换映射。
     * 直接读 /sdcard/laoli_hooktools/config.json(不依赖 XSharedPreferences)。
     */
    private fun loadReplacements() {
        Logger.log(TAG, ">>> loadReplacements 开始")
        Logger.log(TAG, ">>> 读取配置文件: /sdcard/laoli_hooktools/config.json")

        val json = try {
            val file = java.io.File("/sdcard/laoli_hooktools/config.json")
            Logger.log(TAG, "配置文件路径: ${file.absolutePath}")
            Logger.log(TAG, "exists = ${file.exists()}")
            Logger.log(TAG, "canRead = ${file.canRead()}")
            Logger.log(TAG, "length = ${file.length()}")

            if (!file.exists()) {
                Logger.log(TAG, ">>> 配置文件不存在!")
                replacements.clear()
                return
            }

            if (!file.canRead()) {
                Logger.log(TAG, ">>> 配置文件不可读!")
                replacements.clear()
                return
            }

            org.json.JSONObject(file.readText())
        } catch (t: Throwable) {
            Logger.e(TAG, "读取配置文件失败", t)
            replacements.clear()
            return
        }

        Logger.log(TAG, "配置文件内容: $json")

        replacements.clear()
        for (target in Constants.TargetResource.values()) {
            val targetJson = json.optJSONObject(target.resName)
            if (targetJson == null) {
                Logger.log(TAG, "配置项 ${target.resName}: 不存在")
                continue
            }

            val enabled = targetJson.optBoolean("enabled", false)
            val path = targetJson.optString("path", null)

            Logger.log(TAG, "配置项: ${target.resName}")
            Logger.log(TAG, "  enabled = $enabled")
            Logger.log(TAG, "  path = $path")

            if (enabled && !path.isNullOrEmpty()) {
                // 检查图片文件
                val imgFile = java.io.File(path)
                Logger.log(TAG, "  图片文件检查:")
                Logger.log(TAG, "    exists = ${imgFile.exists()}")
                Logger.log(TAG, "    canRead = ${imgFile.canRead()}")
                Logger.log(TAG, "    length = ${if (imgFile.exists()) imgFile.length() else -1}")

                replacements[target.resName] = path
                Logger.log(TAG, "  >>> 已加入替换列表")
            } else {
                Logger.log(TAG, "  >>> 未启用或路径为空,跳过")
            }
        }

        // 加载 string 替换配置
        stringReplacements.clear()
        for (target in Constants.TargetString.values()) {
            val key = "string_" + target.resName
            val targetJson = json.optJSONObject(key)
            if (targetJson == null) {
                Logger.log(TAG, "string 配置项 $key: 不存在")
                continue
            }
            val enabled = targetJson.optBoolean("enabled", false)
            val value = targetJson.optString("value", null)
            Logger.log(TAG, "string 配置项: $key")
            Logger.log(TAG, "  enabled = $enabled")
            Logger.log(TAG, "  value = $value")
            if (enabled && !value.isNullOrEmpty()) {
                stringReplacements[target.resName] = value
                Logger.log(TAG, "  >>> 已加入 string 替换: ${target.resName} <- $value")
            } else {
                Logger.log(TAG, "  >>> string 未启用或值为空,跳过")
            }
        }

        // 加载 color 替换配置
        colorReplacements.clear()
        for (target in Constants.TargetColor.values()) {
            val key = "color_" + target.resName
            val targetJson = json.optJSONObject(key)
            if (targetJson == null) {
                Logger.log(TAG, "color 配置项 $key: 不存在")
                continue
            }
            val enabled = targetJson.optBoolean("enabled", false)
            val value = targetJson.optString("value", null)
            Logger.log(TAG, "color 配置项: $key")
            Logger.log(TAG, "  enabled = $enabled")
            Logger.log(TAG, "  value = $value")
            if (enabled && !value.isNullOrEmpty()) {
                try {
                    val colorInt = android.graphics.Color.parseColor(value)
                    colorReplacements[target.resName] = colorInt
                    Logger.log(TAG, "  >>> 已加入 color 替换: ${target.resName} <- $value (0x${Integer.toHexString(colorInt)})")
                } catch (t: Throwable) {
                    Logger.e(TAG, "  >>> color 解析失败: $value", t)
                }
            } else {
                Logger.log(TAG, "  >>> color 未启用或值为空,跳过")
            }
        }

        // 加载时间详细显示配置
        val timeDetailJson = json.optJSONObject("time_detail")
        timeDetailEnabled = timeDetailJson?.optBoolean("enabled", false) ?: false
        Logger.log(TAG, "时间详细显示配置: enabled = $timeDetailEnabled")

        // 加载防删除配置(只防同步/他人删除)
        val antiDeleteJson = json.optJSONObject("anti_delete")
        antiDeleteEnabled = antiDeleteJson?.optBoolean("enabled", false) ?: false
        Logger.log(TAG, "防删除配置: enabled = $antiDeleteEnabled")

        // 加载链接自动跳转配置
        val linkJumpJson = json.optJSONObject("link_jump")
        linkJumpEnabled = linkJumpJson?.optBoolean("enabled", false) ?: false
        Logger.log(TAG, "链接自动跳转配置: enabled = $linkJumpEnabled")

        // 加载自定义字体配置
        val fontJson = json.optJSONObject("custom_font")
        fontEnabled = fontJson?.optBoolean("enabled", false) ?: false
        fontPath = fontJson?.optString("path", null)
        customTypeface = null
        Logger.log(TAG, "自定义字体配置: enabled = $fontEnabled, path = $fontPath")

        Logger.log(TAG, ">>> loadReplacements 完成,共 ${replacements.size} 个图片替换, ${stringReplacements.size} 个 string 替换, ${colorReplacements.size} 个 color 替换")
        for ((k, v) in replacements) {
            Logger.log(TAG, "  图片替换: $k <- $v")
        }
        for ((k, v) in stringReplacements) {
            Logger.log(TAG, "  string 替换: $k <- $v")
        }
        for ((k, v) in colorReplacements) {
            Logger.log(TAG, "  color 替换: $k <- 0x${Integer.toHexString(v)}")
        }
    }

    /**
     * Toast 弹出"注入成功"。
     */
    private fun showInjectionToast(app: Application) {
        try {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(app, "注入成功", Toast.LENGTH_LONG).show()
            }
            Logger.log(TAG, "Toast 已提交到主线程")
        } catch (t: Throwable) {
            Logger.e(TAG, "Toast 失败", t)
        }
    }

    /**
     * 运行时 hook Resources,拦截 getDrawable 等方法。
     */
    private fun hookResourcesForDrawable(app: Application) {
        Logger.log(TAG, ">>> hookResourcesForDrawable 开始")

        val res = app.resources
        Logger.log(TAG, "Resources 类 = ${res.javaClass.name}")

        // 构建资源 ID → 资源名 的映射
        val targetResIds = mutableMapOf<Int, String>()
        Logger.log(TAG, ">>> 查找目标资源 ID:")
        for (resName in replacements.keys) {
            try {
                val id = res.getIdentifier(resName, "drawable", Constants.TARGET_PACKAGE)
                Logger.log(TAG, "  getIdentifier('$resName', 'drawable', '${Constants.TARGET_PACKAGE}') = 0x${id.toString(16)}")
                if (id != 0) {
                    targetResIds[id] = resName
                    Logger.log(TAG, "    >>> 映射成功: 0x${id.toString(16)} -> $resName")
                } else {
                    Logger.log(TAG, "    >>> 资源 ID 未找到!")
                }
            } catch (t: Throwable) {
                Logger.e(TAG, "  getIdentifier('$resName') 异常", t)
            }
        }

        Logger.log(TAG, ">>> 目标资源 ID 映射完成,共 ${targetResIds.size} 个")
        if (targetResIds.isEmpty()) {
            Logger.log(TAG, ">>> 没有目标资源 ID,跳过 hook")
            return
        }

        // hook Resources.getDrawable(int id)
        Logger.log(TAG, ">>> 注册 Resources.getDrawable(int) hook")
        try {
            XposedHelpers.findAndHookMethod(
                android.content.res.Resources::class.java,
                "getDrawable",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val id = param.args[0] as Int
                        val resName = targetResIds[id]
                        if (resName != null) {
                            val path = replacements[resName]
                            Logger.log(TAG, ">>> [getDrawable] 拦截! id=0x${id.toString(16)} resName=$resName path=$path")
                            if (path != null) {
                                val drawable = createDrawableFromFile(param.thisObject as android.content.res.Resources, path)
                                Logger.log(TAG, ">>> [getDrawable] 替换结果: ${drawable.javaClass.name}, size=${(drawable as? BitmapDrawable)?.bitmap?.width}x${(drawable as? BitmapDrawable)?.bitmap?.height}")
                                param.result = drawable
                            }
                        }
                    }
                }
            )
            Logger.log(TAG, "Resources.getDrawable(int) hook 注册成功")
        } catch (t: Throwable) {
            Logger.e(TAG, "Resources.getDrawable(int) hook 注册失败", t)
        }

        // hook Resources.getDrawableForDensity(int id, int density)
        Logger.log(TAG, ">>> 注册 Resources.getDrawableForDensity(int,int) hook")
        try {
            XposedHelpers.findAndHookMethod(
                android.content.res.Resources::class.java,
                "getDrawableForDensity",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val id = param.args[0] as Int
                        val resName = targetResIds[id]
                        if (resName != null) {
                            val path = replacements[resName]
                            Logger.log(TAG, ">>> [getDrawableForDensity] 拦截! id=0x${id.toString(16)} resName=$resName path=$path")
                            if (path != null) {
                                param.result = createDrawableFromFile(param.thisObject as android.content.res.Resources, path)
                            }
                        }
                    }
                }
            )
            Logger.log(TAG, "Resources.getDrawableForDensity(int,int) hook 注册成功")
        } catch (t: Throwable) {
            Logger.e(TAG, "Resources.getDrawableForDensity(int,int) hook 注册失败(可能方法不存在)", t)
        }

        // hook Context.getDrawable(int id)
        Logger.log(TAG, ">>> 注册 Context.getDrawable(int) hook")
        try {
            XposedHelpers.findAndHookMethod(
                Context::class.java,
                "getDrawable",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val id = param.args[0] as Int
                        val resName = targetResIds[id]
                        if (resName != null) {
                            val path = replacements[resName]
                            Logger.log(TAG, ">>> [Context.getDrawable] 拦截! id=0x${id.toString(16)} resName=$resName path=$path")
                            if (path != null) {
                                param.result = createDrawableFromFile(
                                    (param.thisObject as Context).resources, path
                                )
                            }
                        }
                    }
                }
            )
            Logger.log(TAG, "Context.getDrawable(int) hook 注册成功")
        } catch (t: Throwable) {
            Logger.e(TAG, "Context.getDrawable(int) hook 注册失败", t)
        }

        // 额外:hook 更多 drawable 加载路径
        Logger.log(TAG, ">>> 尝试 hook 加载图片相关方法")

        // ★ 关键:hook AppCompatResources.getDrawable(Context, int)
        // 好友圈用 AppCompat,TintTypedArray.getDrawable 走的是 AppCompatResources.getDrawable
        Logger.log(TAG, ">>> 注册 AppCompatResources.getDrawable(Context,int) hook")
        try {
            val appCompatResClass = XposedHelpers.findClass(
                "android.support.v7.content.res.AppCompatResources", app.classLoader
            )
            XposedHelpers.findAndHookMethod(
                appCompatResClass,
                "getDrawable",
                Context::class.java,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val id = param.args[1] as Int
                        val resName = targetResIds[id]
                        if (resName != null) {
                            val path = replacements[resName]
                            Logger.log(TAG, ">>> [AppCompatResources.getDrawable] 拦截! id=0x${id.toString(16)} resName=$resName path=$path 原始结果=${param.result?.javaClass?.name}")
                            if (path != null) {
                                val drawable = createDrawableFromFile(
                                    (param.args[0] as Context).resources, path
                                )
                                param.result = drawable
                                Logger.log(TAG, ">>> [AppCompatResources.getDrawable] 替换成功!")
                            }
                        }
                    }
                }
            )
            Logger.log(TAG, "AppCompatResources.getDrawable(Context,int) hook 注册成功")
        } catch (t: Throwable) {
            Logger.e(TAG, "AppCompatResources.getDrawable hook 注册失败", t)
        }

        // hook ResourcesWrapper.getDrawable(int) — AppCompat 的 Resources 包装类
        Logger.log(TAG, ">>> 注册 ResourcesWrapper.getDrawable(int) hook")
        try {
            val resWrapperClass = XposedHelpers.findClass(
                "android.support.v7.widget.ResourcesWrapper", app.classLoader
            )
            XposedHelpers.findAndHookMethod(
                resWrapperClass,
                "getDrawable",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val id = param.args[0] as Int
                        val resName = targetResIds[id]
                        if (resName != null) {
                            val path = replacements[resName]
                            Logger.log(TAG, ">>> [ResourcesWrapper.getDrawable] 拦截! id=0x${id.toString(16)} resName=$resName path=$path")
                            if (path != null) {
                                val wrapper = param.thisObject
                                val innerRes = XposedHelpers.getObjectField(wrapper, "mResources") as android.content.res.Resources
                                param.result = createDrawableFromFile(innerRes, path)
                                Logger.log(TAG, ">>> [ResourcesWrapper.getDrawable] 替换成功!")
                            }
                        }
                    }
                }
            )
            Logger.log(TAG, "ResourcesWrapper.getDrawable(int) hook 注册成功")
        } catch (t: Throwable) {
            Logger.e(TAG, "ResourcesWrapper.getDrawable(int) hook 注册失败", t)
        }

        // hook TintResources.getDrawable(int) — AppCompat 的 TintResources
        Logger.log(TAG, ">>> 注册 TintResources.getDrawable(int) hook")
        try {
            val tintResClass = XposedHelpers.findClass(
                "android.support.v7.widget.TintResources", app.classLoader
            )
            XposedHelpers.findAndHookMethod(
                tintResClass,
                "getDrawable",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val id = param.args[0] as Int
                        val resName = targetResIds[id]
                        if (resName != null) {
                            val path = replacements[resName]
                            Logger.log(TAG, ">>> [TintResources.getDrawable] 拦截! id=0x${id.toString(16)} resName=$resName path=$path 原始结果=${param.result?.javaClass?.name}")
                            if (path != null) {
                                val ctx = app
                                param.result = createDrawableFromFile(ctx.resources, path)
                                Logger.log(TAG, ">>> [TintResources.getDrawable] 替换成功!")
                            }
                        }
                    }
                }
            )
            Logger.log(TAG, "TintResources.getDrawable(int) hook 注册成功")
        } catch (t: Throwable) {
            Logger.e(TAG, "TintResources.getDrawable(int) hook 注册失败", t)
        }

        // hook ImageView.setBackgroundResource(int) — XML android:background 走这里
        Logger.log(TAG, ">>> 注册 ImageView.setBackgroundResource(int) hook")
        try {
            XposedHelpers.findAndHookMethod(
                android.widget.ImageView::class.java,
                "setBackgroundResource",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val id = param.args[0] as Int
                        val resName = targetResIds[id]
                        if (resName != null) {
                            val path = replacements[resName]
                            Logger.log(TAG, ">>> [setBackgroundResource] 拦截! id=0x${id.toString(16)} resName=$resName path=$path")
                            if (path != null) {
                                val iv = param.thisObject as android.widget.ImageView
                                val bmp = BitmapFactory.decodeFile(path)
                                if (bmp != null) {
                                    iv.setImageBitmap(bmp)
                                    Logger.log(TAG, ">>> [setBackgroundResource] 替换为自定义图片 ${bmp.width}x${bmp.height}")
                                }
                                param.result = null
                            }
                        }
                    }
                }
            )
            Logger.log(TAG, "ImageView.setBackgroundResource(int) hook 注册成功")
        } catch (t: Throwable) {
            Logger.e(TAG, "ImageView.setBackgroundResource(int) hook 注册失败", t)
        }

        // hook TypedArray.getDrawable(int index) — 拦截并替换
        // TypedArray.getDrawable(index) 内部先 getResourceId(index) 再加载
        // 我们 hook 后先查 resourceId,匹配则替换
        Logger.log(TAG, ">>> 注册 TypedArray.getDrawable(int) hook")
        try {
            XposedHelpers.findAndHookMethod(
                android.content.res.TypedArray::class.java,
                "getDrawable",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val index = param.args[0] as Int
                        // 获取这个 index 对应的资源 ID
                        val ta = param.thisObject as android.content.res.TypedArray
                        val resId = ta.getResourceId(index, 0)
                        val resName = targetResIds[resId]
                        if (resName != null) {
                            val path = replacements[resName]
                            Logger.log(TAG, ">>> [TypedArray.getDrawable] 拦截! index=$index resId=0x${resId.toString(16)} resName=$resName path=$path 原始结果=${param.result?.javaClass?.name}")
                            if (path != null) {
                                val drawable = createDrawableFromFile(app.resources, path)
                                param.result = drawable
                                Logger.log(TAG, ">>> [TypedArray.getDrawable] 替换成功!")
                            }
                        }
                    }
                }
            )
            Logger.log(TAG, "TypedArray.getDrawable(int) hook 注册成功")
        } catch (t: Throwable) {
            Logger.e(TAG, "TypedArray.getDrawable(int) hook 注册失败", t)
        }

        // hook ImageView.setImageResource(int resId)
        Logger.log(TAG, ">>> 注册 ImageView.setImageResource(int) hook")
        try {
            XposedHelpers.findAndHookMethod(
                android.widget.ImageView::class.java,
                "setImageResource",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val id = param.args[0] as Int
                        val resName = targetResIds[id]
                        if (resName != null) {
                            val path = replacements[resName]
                            Logger.log(TAG, ">>> [setImageResource] 拦截! id=0x${id.toString(16)} resName=$resName path=$path")
                            if (path != null) {
                                val iv = param.thisObject as android.widget.ImageView
                                val bmp = BitmapFactory.decodeFile(path)
                                if (bmp != null) {
                                    iv.setImageBitmap(bmp)
                                    Logger.log(TAG, ">>> [setImageResource] 替换为自定义图片 ${bmp.width}x${bmp.height}")
                                }
                                param.result = null
                            }
                        }
                    }
                }
            )
            Logger.log(TAG, "ImageView.setImageResource(int) hook 注册成功")
        } catch (t: Throwable) {
            Logger.e(TAG, "ImageView.setImageResource(int) hook 注册失败", t)
        }

        Logger.log(TAG, ">>> hookResourcesForDrawable 完成")
    }

    /**
     * 按 View id 兜底替换图片。
     *
     * 好友圈的 header_recycle_moment.xml 中 ImageView id = iv_head_bg(0x7f090119),
     * android:src="@drawable/bg_head_view"。ImageView 所有设置图片的路径
     * (setImageResource / setImageBitmap / setImageDrawable / XML android:src)
     * 最终都会汇聚到 setImageDrawable(Drawable)。
     *
     * 这里 hook setImageDrawable,只要 view.id 命中目标 id,就把入参 drawable 换成自定义图片。
     */
    private fun hookViewIdReplacement(app: Application) {
        Logger.log(TAG, ">>> hookViewIdReplacement 开始")

        val res = app.resources
        viewIdToResName.clear()
        for (target in Constants.TargetResource.values()) {
            val name = target.viewIdName ?: continue
            try {
                val id = res.getIdentifier(name, "id", Constants.TARGET_PACKAGE)
                Logger.log(TAG, "  getIdentifier('$name', 'id', '${Constants.TARGET_PACKAGE}') = 0x${id.toString(16)}")
                if (id != 0) {
                    viewIdToResName[id] = target.resName
                    Logger.log(TAG, "  viewId 映射: 0x${id.toString(16)} -> ${target.resName}")
                } else {
                    Logger.log(TAG, "  >>> view id '$name' 未找到!")
                }
            } catch (t: Throwable) {
                Logger.e(TAG, "  getIdentifier('$name') 异常", t)
            }
        }

        if (viewIdToResName.isEmpty()) {
            Logger.log(TAG, ">>> 没有 viewId 映射,跳过")
            return
        }

        try {
            XposedHelpers.findAndHookMethod(
                ImageView::class.java,
                "setImageDrawable",
                Drawable::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as? ImageView ?: return
                        val vId = view.id
                        // 诊断:记录所有好友圈资源 id 范围内的 ImageView(去重)
                        if (vId in 0x7f000000..0x7f1fffff && seenImageIds.add(vId)) {
                            Logger.log(TAG, "  [诊断] 好友圈 ImageView id=0x${vId.toString(16)}")
                        }
                        val resName = viewIdToResName[vId] ?: return
                        val path = replacements[resName] ?: return

                        Logger.log(TAG, ">>> [ViewId/setImageDrawable] 命中! viewId=0x${vId.toString(16)} resName=$resName path=$path")

                        val bmp = BitmapFactory.decodeFile(path)
                        if (bmp == null) {
                            Logger.log(TAG, ">>> [ViewId/setImageDrawable] 图片解码失败,跳过")
                            return
                        }

                        param.args[0] = BitmapDrawable(view.resources, bmp)
                        Logger.log(TAG, ">>> [ViewId/setImageDrawable] 替换成功! size=${bmp.width}x${bmp.height}")
                    }
                }
            )
            Logger.log(TAG, "ImageView.setImageDrawable(Drawable) hook 注册成功")
        } catch (t: Throwable) {
            Logger.e(TAG, "ImageView.setImageDrawable(Drawable) hook 注册失败", t)
        }

        // 兜底:View.onAttachedToWindow 时,若目标 View 已带原图,直接再覆盖一次
        try {
            XposedHelpers.findAndHookMethod(
                View::class.java,
                "onAttachedToWindow",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as? View ?: return
                        val resName = viewIdToResName[view.id] ?: return
                        val path = replacements[resName] ?: return
                        if (view !is ImageView) return

                        val bmp = BitmapFactory.decodeFile(path)
                        if (bmp == null) return
                        view.setImageBitmap(bmp)
                        Logger.log(TAG, ">>> [ViewId/onAttachedToWindow] 覆盖成功! viewId=0x${view.id.toString(16)} resName=$resName")
                    }
                }
            )
            Logger.log(TAG, "View.onAttachedToWindow hook 注册成功")
        } catch (t: Throwable) {
            Logger.e(TAG, "View.onAttachedToWindow hook 注册失败", t)
        }

        // 兜底:Activity.onResume 后延迟遍历 View 树,直接替换目标 ImageView
        try {
            XposedHelpers.findAndHookMethod(
                Activity::class.java,
                "onResume",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val activity = param.thisObject as? Activity ?: return
                        val handler = Handler(Looper.getMainLooper())
                        val delays = longArrayOf(500L, 1500L, 3000L, 5000L)
                        for (i in delays.indices) {
                            val first = i == 0
                            handler.postDelayed({
                                val root = activity.window?.decorView
                                Logger.log(TAG, ">>> [遍历] onResume 后遍历 View 树(${delays[i]}ms), activity=${activity.javaClass.simpleName}")
                                if (first) dumpViewTree(root)
                                traverseAndReplace(root)
                            }, delays[i])
                        }
                    }
                }
            )
            Logger.log(TAG, "Activity.onResume hook 注册成功")
        } catch (t: Throwable) {
            Logger.e(TAG, "Activity.onResume hook 注册失败", t)
        }

        Logger.log(TAG, ">>> hookViewIdReplacement 完成")
    }

    /** 递归遍历 View 树,替换命中目标 id 的 ImageView 背景与 TextView 文字 */
    private fun traverseAndReplace(root: View?) {
        if (root == null) return
        if (root is ImageView) {
            val resName = viewIdToResName[root.id]
            val path = resName?.let { replacements[it] }
            if (path != null) {
                val bmp = BitmapFactory.decodeFile(path)
                if (bmp != null) {
                    root.setImageBitmap(bmp)
                    Logger.log(TAG, ">>> [遍历] 替换 ImageView id=0x${root.id.toString(16)} resName=$resName size=${bmp.width}x${bmp.height}")
                }
            }
        }
        if (root is android.widget.TextView) {
            // 文字替换
            val resName = stringViewIdToResName[root.id]
            val value = resName?.let { stringReplacements[it] }
            if (value != null) {
                root.text = value
                Logger.log(TAG, ">>> [遍历] 替换 TextView id=0x${root.id.toString(16)} resName=$resName text='$value'")
            }
            // 颜色替换
            val colorResName = colorViewIdToResName[root.id]
            val colorInt = colorResName?.let { colorReplacements[it] }
            if (colorInt != null) {
                root.setTextColor(colorInt)
                Logger.log(TAG, ">>> [遍历] 替换 TextView 颜色 id=0x${root.id.toString(16)} resName=$colorResName color=0x${Integer.toHexString(colorInt)}")
            }
        }
        if (root is android.view.ViewGroup) {
            for (i in 0 until root.childCount) {
                traverseAndReplace(root.getChildAt(i))
            }
        }
    }

    /** 递归 dump View 树,打印每个 View 的类名和 id(用于定位目标 View 真实 id) */
    private fun dumpViewTree(root: View?, depth: Int = 0) {
        if (root == null || depth > 25) return
        val id = root.id
        val idStr = if (id != View.NO_ID) "0x${Integer.toHexString(id)}" else "-"
        Logger.log(TAG, "  [dump] ${"  ".repeat(depth)}${root.javaClass.simpleName} id=$idStr")
        if (root is android.view.ViewGroup) {
            for (i in 0 until root.childCount) {
                dumpViewTree(root.getChildAt(i), depth + 1)
            }
        }
    }

    /**
     * hook 字符串资源替换(app_name)。
     *
     * Resources.getString(int) / getString(int, Object...) 最终都走 getText(int),
     * 因此 hook getText(int) 覆盖最广。
     */
    private fun hookStringReplacement(app: Application) {
        Logger.log(TAG, ">>> hookStringReplacement 开始")

        if (stringReplacements.isEmpty()) {
            Logger.log(TAG, ">>> 没有 string 替换项,跳过")
            return
        }

        val res = app.resources
        val targetStringIds = mutableMapOf<Int, String>()
        stringViewIdToResName.clear()
        for (target in Constants.TargetString.values()) {
            val value = stringReplacements[target.resName] ?: continue

            // 1. 获取 string 资源真实 id
            try {
                val id = res.getIdentifier(target.resName, "string", Constants.TARGET_PACKAGE)
                Logger.log(TAG, "  getIdentifier('${target.resName}', 'string', '${Constants.TARGET_PACKAGE}') = 0x${id.toString(16)}")
                if (id != 0) {
                    targetStringIds[id] = value
                } else {
                    Logger.log(TAG, "    >>> string 资源 ID 未找到!")
                }
            } catch (t: Throwable) {
                Logger.e(TAG, "  getIdentifier('${target.resName}') 异常", t)
            }

            // 2. 获取显示该字符串的 TextView 真实 id(用于遍历兜底)
            val viewName = target.viewIdName ?: continue
            try {
                val vid = res.getIdentifier(viewName, "id", Constants.TARGET_PACKAGE)
                Logger.log(TAG, "  getIdentifier('$viewName', 'id', '${Constants.TARGET_PACKAGE}') = 0x${vid.toString(16)}")
                if (vid != 0) {
                    stringViewIdToResName[vid] = target.resName
                } else {
                    Logger.log(TAG, "    >>> string View id '$viewName' 未找到!")
                }
            } catch (t: Throwable) {
                Logger.e(TAG, "  getIdentifier('$viewName') 异常", t)
            }
        }

        if (targetStringIds.isEmpty()) {
            Logger.log(TAG, ">>> 没有可用的 string 资源 ID,跳过")
            return
        }

        // hook Resources.getText(int)
        try {
            XposedHelpers.findAndHookMethod(
                android.content.res.Resources::class.java,
                "getText",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val id = param.args[0] as Int
                        // 诊断:记录所有好友圈资源 id 范围内的 string(去重)
                        if (id in 0x7f000000..0x7f1fffff && seenStringIds.add(id)) {
                            Logger.log(TAG, "  [诊断] 好友圈 string id=0x${id.toString(16)}")
                        }
                        val value = targetStringIds[id] ?: return
                        Logger.log(TAG, ">>> [getText] 拦截! id=0x${id.toString(16)} 替换为 '$value'")
                        param.result = value
                    }
                }
            )
            Logger.log(TAG, "Resources.getText(int) hook 注册成功")
        } catch (t: Throwable) {
            Logger.e(TAG, "Resources.getText(int) hook 注册失败", t)
        }

        // hook Resources.getString(int)
        try {
            XposedHelpers.findAndHookMethod(
                android.content.res.Resources::class.java,
                "getString",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val id = param.args[0] as Int
                        val value = targetStringIds[id] ?: return
                        Logger.log(TAG, ">>> [getString] 拦截! id=0x${id.toString(16)} 替换为 '$value'")
                        param.result = value
                    }
                }
            )
            Logger.log(TAG, "Resources.getString(int) hook 注册成功")
        } catch (t: Throwable) {
            Logger.e(TAG, "Resources.getString(int) hook 注册失败", t)
        }

        Logger.log(TAG, ">>> hookStringReplacement 完成")
    }

    /**
     * hook 颜色资源替换(banner / 发布按钮 / 名字 文字色)。
     * setReplacement 方式优先(handleInitPackageResources 已注册),
     * 这里再 hook Resources.getColor 作为运行时兜底,
     * 主要靠 View 树遍历兜底直接 setTextColor(header 是异步预加载)。
     */
    private fun hookColorReplacement(app: Application) {
        Logger.log(TAG, ">>> hookColorReplacement 开始")

        if (colorReplacements.isEmpty()) {
            Logger.log(TAG, ">>> 没有 color 替换项,跳过")
            return
        }

        val res = app.resources
        val targetColorIds = mutableMapOf<Int, Int>()
        colorViewIdToResName.clear()
        for (target in Constants.TargetColor.values()) {
            val colorInt = colorReplacements[target.resName] ?: continue

            // 1. 获取 color 资源真实 id
            try {
                val id = res.getIdentifier(target.resName, "color", Constants.TARGET_PACKAGE)
                Logger.log(TAG, "  getIdentifier('${target.resName}', 'color', '${Constants.TARGET_PACKAGE}') = 0x${id.toString(16)}")
                if (id != 0) {
                    targetColorIds[id] = colorInt
                } else {
                    Logger.log(TAG, "    >>> color 资源 ID 未找到!")
                }
            } catch (t: Throwable) {
                Logger.e(TAG, "  getIdentifier('${target.resName}') 异常", t)
            }

            // 2. 获取显示该颜色的 TextView 真实 id(用于遍历兜底)
            val viewName = target.viewIdName ?: continue
            try {
                val vid = res.getIdentifier(viewName, "id", Constants.TARGET_PACKAGE)
                Logger.log(TAG, "  getIdentifier('$viewName', 'id', '${Constants.TARGET_PACKAGE}') = 0x${vid.toString(16)}")
                if (vid != 0) {
                    colorViewIdToResName[vid] = target.resName
                } else {
                    Logger.log(TAG, "    >>> color View id '$viewName' 未找到!")
                }
            } catch (t: Throwable) {
                Logger.e(TAG, "  getIdentifier('$viewName') 异常", t)
            }
        }

        if (targetColorIds.isEmpty()) {
            Logger.log(TAG, ">>> 没有可用的 color 资源 ID,跳过")
            return
        }

        // hook Resources.getColor(int)
        try {
            XposedHelpers.findAndHookMethod(
                android.content.res.Resources::class.java,
                "getColor",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val id = param.args[0] as Int
                        val colorInt = targetColorIds[id] ?: return
                        Logger.log(TAG, ">>> [getColor] 拦截! id=0x${id.toString(16)} 替换为 0x${Integer.toHexString(colorInt)}")
                        param.result = colorInt
                    }
                }
            )
            Logger.log(TAG, "Resources.getColor(int) hook 注册成功")
        } catch (t: Throwable) {
            Logger.e(TAG, "Resources.getColor(int) hook 注册失败", t)
        }

        // hook Resources.getColor(int, Theme)
        try {
            XposedHelpers.findAndHookMethod(
                android.content.res.Resources::class.java,
                "getColor",
                Int::class.javaPrimitiveType,
                android.content.res.Resources.Theme::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val id = param.args[0] as Int
                        val colorInt = targetColorIds[id] ?: return
                        Logger.log(TAG, ">>> [getColor(theme)] 拦截! id=0x${id.toString(16)} 替换为 0x${Integer.toHexString(colorInt)}")
                        param.result = colorInt
                    }
                }
            )
            Logger.log(TAG, "Resources.getColor(int,Theme) hook 注册成功")
        } catch (t: Throwable) {
            Logger.e(TAG, "Resources.getColor(int,Theme) hook 注册失败", t)
        }

        Logger.log(TAG, ">>> hookColorReplacement 完成")
    }

    /**
     * hook 时间详细显示。
     *
     * 好友圈时间工具类(反编译 source = TimeUtils.java)用静态方法
     * g(Context,long) / f(Context,long) 把发布时间戳转成"几分钟前 / 昨天 18:30 / MM月dd日"。
     * 这里直接替换这两个方法的返回值为完整时间 yyyy-MM-dd HH:mm。
     *
     * 注意:该类是混淆类,类名随版本变化(旧版 com.xtc.moment.util.bi,当前版 ...bg)。
     * 方法名 g/f 保持不变。
     */
    private fun hookTimeDetail(app: Application) {
        Logger.log(TAG, ">>> hookTimeDetail 开始")

        if (!timeDetailEnabled) {
            Logger.log(TAG, ">>> 时间详细显示未启用,跳过")
            return
        }

        // 混淆后的候选类名(均为时间工具类 TimeUtils,按版本新增)
        val candidateClasses = arrayOf(
            "com.xtc.moment.util.bg",
            "com.xtc.moment.util.bi"
        )

        val methods = arrayOf("g", "f")
        var hooked = false

        for (className in candidateClasses) {
            if (hooked) break
            try {
                val clazz = XposedHelpers.findClass(className, app.classLoader)
                Logger.log(TAG, ">>> [timeDetail] 找到候选时间工具类: $className")

                var allOk = true
                for (methodName in methods) {
                    try {
                        XposedHelpers.findAndHookMethod(
                            clazz,
                            methodName,
                            Context::class.java,
                            Long::class.javaPrimitiveType,
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam) {
                                    val ts = param.args[1] as Long
                                    val detailed = java.text.SimpleDateFormat(
                                        Constants.TIME_DETAIL_FORMAT,
                                        java.util.Locale.getDefault()
                                    ).format(java.util.Date(ts))
                                    val intercepted = interceptedCreateTimes.contains(ts)
                                    val result = if (intercepted) ANTI_DELETE_MARK + detailed else detailed
                                    param.result = result
                                    Logger.log(TAG, ">>> [timeDetail/$methodName] 替换为详细时间: $result 命中拦截=$intercepted")
                                }
                            }
                        )
                        Logger.log(TAG, ">>> [timeDetail] $className.$methodName(Context,long) hook 注册成功")
                    } catch (t: Throwable) {
                        allOk = false
                        Logger.e(TAG, ">>> [timeDetail] $className.$methodName hook 注册失败(非时间工具类或版本不符)", t)
                    }
                }

                if (allOk) {
                    hooked = true
                    Logger.log(TAG, ">>> [timeDetail] 时间 Hook 已在 $className 上生效")
                }
            } catch (t: Throwable) {
                Logger.log(TAG, ">>> [timeDetail] 候选类 $className 不存在,尝试下一个")
            }
        }

        if (!hooked) {
            Logger.log(TAG, ">>> [timeDetail] 所有候选类均未匹配到时间工具类,时间详细显示未生效")
        }

        Logger.log(TAG, ">>> hookTimeDetail 完成")
    }

    /**
     * hook 防删除(只防同步/他人删除)。
     *
     * 好友圈动态删除有三条路径:
     *   1. 自己删除: serve/q.a(DbMoment) -> 网络删除 -> 按 momentId 删本地(不拦截)
     *   2. 好友删动态(同步对比): MomentCachePoor.deleteCacheByNet -> deleteForBatch 批量删(拦截)
     *   3. 好友关系变化: ContactChangeReceiver -> deleteByColumnName("watchId") 删(拦截)
     */
    private fun hookAntiDelete(app: Application) {
        Logger.log(TAG, ">>> hookAntiDelete 开始")

        if (!antiDeleteEnabled) {
            Logger.log(TAG, ">>> 防删除未启用,跳过")
            return
        }

        // 1. 拦截"好友删动态"的同步对比删除(MomentCachePoor.deleteCacheByNet)
        hookDeleteCacheByNet(app)

        // 2. 拦截 DAO 层删除(批量删 / 按 watchId 删)
        hookDaoDelete(app)

        Logger.log(TAG, ">>> hookAntiDelete 完成")
    }

    /** 拦截 MomentCachePoor.deleteCacheByNet(同步对比删除本地多余动态) */
    private fun hookDeleteCacheByNet(app: Application) {
        try {
            val cachePoorClass = XposedHelpers.findClass("com.xtc.moment.a.a", app.classLoader)
            val callbackClass = XposedHelpers.findClass("com.xtc.moment.a.c\$a", app.classLoader)
            XposedHelpers.findAndHookMethod(
                cachePoorClass,
                "a",
                java.util.Map::class.java,
                java.util.Map::class.java,
                java.util.List::class.java,
                callbackClass,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        // 记录"本地有但网络没有"的动态 momentId(这些是被拦截保留下来的)
                        val networkMap = param.args.getOrNull(0) as? Map<*, *>
                        val localMap = param.args.getOrNull(1) as? Map<*, *>
                        if (localMap != null) {
                            for (entry in localMap.entries) {
                                try {
                                    val key = entry.key
                                    if (networkMap == null || !networkMap.containsKey(key)) {
                                        val value = entry.value
                                        if (value != null) {
                                            val createTime = XposedHelpers.callMethod(value, "getCreateTime") as? Long
                                            if (createTime != null) {
                                                interceptedCreateTimes.add(createTime)
                                                if (recordDebugCount < 15) {
                                                    recordDebugCount++
                                                    Logger.log(TAG, ">>> [antiDelete][记录] createTime=$createTime")
                                                }
                                            }
                                        }
                                    }
                                } catch (_: Throwable) {
                                }
                            }
                        }
                        param.result = null
                        Logger.log(TAG, ">>> [antiDelete] 阻止同步删除(deleteCacheByNet), 已记录 ${interceptedCreateTimes.size} 条")
                    }
                }
            )
            Logger.log(TAG, ">>> hookAntiDelete: deleteCacheByNet hook 注册成功")
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookAntiDelete: deleteCacheByNet hook 注册失败", t)
        }
    }

    /** 拦截 DAO 层删除(仅针对 MomentDao) */
    private fun hookDaoDelete(app: Application) {
        val baseDaoClass = try {
            XposedHelpers.findClass("com.xtc.database.a.k", app.classLoader)
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookAntiDelete: 找不到基类 DAO com.xtc.database.a.k", t)
            return
        }

        // deleteByColumnName(String, Object): 按列删除,只拦 watchId
        try {
            XposedHelpers.findAndHookMethod(
                baseDaoClass,
                "deleteByColumnName",
                String::class.java,
                Any::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val thisClass = param.thisObject?.javaClass?.name
                        val column = param.args.getOrNull(0) as? String
                        if (thisClass == "com.xtc.moment.db.a.f" && column == "watchId") {
                            param.result = false
                            Logger.log(TAG, ">>> [antiDelete] 阻止按 watchId 删除: ${param.args.getOrNull(1)}")
                        }
                    }
                }
            )
            Logger.log(TAG, ">>> hookAntiDelete: deleteByColumnName hook 注册成功")
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookAntiDelete: deleteByColumnName hook 注册失败", t)
        }

        // deleteForBatch(List): 批量删除,拦同步删除
        try {
            XposedHelpers.findAndHookMethod(
                baseDaoClass,
                "deleteForBatch",
                java.util.List::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val thisClass = param.thisObject?.javaClass?.name
                        if (thisClass == "com.xtc.moment.db.a.f") {
                            // 记录被拦截的动态 createTime
                            val list = param.args.getOrNull(0) as? List<*>
                            list?.forEach { item ->
                                try {
                                    val createTime = XposedHelpers.callMethod(item, "getCreateTime") as? Long
                                    if (createTime != null) interceptedCreateTimes.add(createTime)
                                } catch (_: Throwable) {
                                }
                            }
                            param.result = false
                            Logger.log(TAG, ">>> [antiDelete] 阻止批量删除(deleteForBatch), 已记录 ${interceptedCreateTimes.size} 条")
                        }
                    }
                }
            )
            Logger.log(TAG, ">>> hookAntiDelete: deleteForBatch hook 注册成功")
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookAntiDelete: deleteForBatch hook 注册失败", t)
        }
    }

    /**
     * hook 动态编辑:在长按文本动态后弹出的"复制/取消"弹窗中增加"编辑"按钮,
     * 可编辑别人发的文本动态内容(本地覆盖显示)。
     */
    private fun hookMomentEdit(app: Application) {
        Logger.log(TAG, ">>> hookMomentEdit 开始")
        targetApp = app

        // 1. 记录长按的动态(供后续弹窗注入编辑按钮时读取)
        try {
            val adapterClass = XposedHelpers.findClass("com.xtc.moment.module.a", app.classLoader)
            val dbMomentClass = XposedHelpers.findClass("com.xtc.moment.db.bean.DbMoment", app.classLoader)
            XposedHelpers.findAndHookMethod(
                adapterClass,
                "a",
                dbMomentClass,
                Boolean::class.javaPrimitiveType,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val moment = param.args.getOrNull(0)
                        if (moment != null && moment.javaClass.name == "com.xtc.moment.db.bean.DbMoment") {
                            pendingEditMoment = moment
                            lastMomentAdapter = java.lang.ref.WeakReference(param.thisObject)
                        }
                    }
                }
            )
            Logger.log(TAG, ">>> hookMomentEdit: 记录长按动态 hook 注册成功")
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookMomentEdit: 记录长按动态 hook 注册失败", t)
        }

        // 2. 在长按弹窗注入"编辑"/"保存"按钮
        //    文本动态走"取消/复制"(f) -> 注入"编辑"(第 4 槽位)
        //    含图片/视频的动态走"取消/举报/复制"(b) -> 注入"保存"(第 4 槽位)
        try {
            val dialogClass = XposedHelpers.findClass("com.xtc.moment.util.i", app.classLoader)
            val listenerClass = XposedHelpers.findClass("com.xtc.moment.util.j", app.classLoader)

            // f/b: 文本/图片视频长按弹窗 -> 按动态类型注入"编辑"或"保存"
            // 注意:部分纯文本动态(type 3/7)也会走 b(取消/举报/复制),所以这里统一按 getType 判断。
            try {
                XposedHelpers.findAndHookMethod(
                    dialogClass,
                    "f",
                    listenerClass,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val moment = pendingEditMoment
                            pendingEditMoment = null
                            injectMomentButton(param.thisObject as? android.app.Dialog, moment)
                        }
                    }
                )
                Logger.log(TAG, ">>> hookMomentEdit: 注入按钮 hook(f) 注册成功")
            } catch (t: Throwable) {
                Logger.e(TAG, ">>> hookMomentEdit: 注入按钮 hook(f) 注册失败", t)
            }

            try {
                XposedHelpers.findAndHookMethod(
                    dialogClass,
                    "b",
                    listenerClass,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val moment = extractDbMoment(param.args.getOrNull(0)) ?: pendingEditMoment
                            pendingEditMoment = null
                            injectMomentButton(param.thisObject as? android.app.Dialog, moment)
                        }
                    }
                )
                Logger.log(TAG, ">>> hookMomentEdit: 注入按钮 hook(b) 注册成功")
            } catch (t: Throwable) {
                Logger.e(TAG, ">>> hookMomentEdit: 注入按钮 hook(b) 注册失败", t)
            }

            // 图片/视频长按弹"取消/举报"(showReportBtnDialog, 不含复制) -> 替换为 保存/取消/举报
            // 说明:纯图片/视频动态长按走 AbsMomentView.showReportBtnDialog,并不会走上面的 i.b()
            try {
                val absMomentViewClass = XposedHelpers.findClass("com.xtc.moment.module.widget.AbsMomentView", app.classLoader)
                val reportCallbackClass = XposedHelpers.findClass("com.xtc.moment.module.report.a.a\$a", app.classLoader)
                XposedHelpers.findAndHookMethod(
                    absMomentViewClass,
                    "showReportBtnDialog",
                    reportCallbackClass,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val moment = extractDbMoment(param.args.getOrNull(0)) ?: return
                            val view = param.thisObject as? View ?: return
                            param.result = null
                            showSaveReportDialog(view, moment, param.args.getOrNull(0))
                        }
                    }
                )
                Logger.log(TAG, ">>> hookMomentEdit: showReportBtnDialog hook 注册成功")
            } catch (t: Throwable) {
                Logger.e(TAG, ">>> hookMomentEdit: showReportBtnDialog hook 注册失败", t)
            }
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookMomentEdit: 注入按钮 hook 注册失败", t)
        }

        // 3. 覆盖 getContent 应用编辑后的文本(跨进程文件存储)
        try {
            val dbMomentClass = XposedHelpers.findClass("com.xtc.moment.db.bean.DbMoment", app.classLoader)
            XposedHelpers.findAndHookMethod(
                dbMomentClass,
                "getContent",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val momentId = XposedHelpers.callMethod(param.thisObject, "getMomentId") as? String ?: return
                            val edited = EditedMomentStore.get(momentId) ?: return
                            val text = edited.text ?: return
                            param.result = text
                        } catch (_: Throwable) {
                        }
                    }
                }
            )
            Logger.log(TAG, ">>> hookMomentEdit: getContent 覆盖 hook 注册成功")
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookMomentEdit: getContent 覆盖 hook 注册失败", t)
        }

        // 4. 应用编辑后的颜色/下划线(在 ExpandTextView 渲染时)
        try {
            val expandTextViewClass = XposedHelpers.findClass("com.xtc.moment.module.widget.ExpandTextView", app.classLoader)
            val clickCheckAllListenerClass = XposedHelpers.findClass("com.xtc.moment.module.widget.ExpandTextView\$ClickCheckAllListener", app.classLoader)

            // setText:先应用一次(覆盖部分动态无 setTextColor 的情况)
            XposedHelpers.findAndHookMethod(
                expandTextViewClass,
                "setText",
                CharSequence::class.java,
                clickCheckAllListenerClass,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        applyEditStyle(param.thisObject as? View)
                    }
                }
            )

            // setTextColor:原色设置之后,再覆盖为编辑色(因为 setContent 里 setTextColor 在 setText 之后调用)
            XposedHelpers.findAndHookMethod(
                expandTextViewClass,
                "setTextColor",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        applyEditStyle(param.thisObject as? View)
                    }
                }
            )
            Logger.log(TAG, ">>> hookMomentEdit: 颜色/下划线 hook 注册成功")
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookMomentEdit: 颜色/下划线 hook 注册失败", t)
        }

        // 5. 覆盖发布者名字(编辑后)
        try {
            val dbMomentClass = XposedHelpers.findClass("com.xtc.moment.db.bean.DbMoment", app.classLoader)
            val viewHolderClass = XposedHelpers.findClass("com.xtc.moment.module.b.a", app.classLoader)

            // 主列表 Adapter 设置名字的方法 a(DbMoment, ViewHolder, boolean, String)
            try {
                val adapterClass = XposedHelpers.findClass("com.xtc.moment.module.a", app.classLoader)
                XposedHelpers.findAndHookMethod(
                    adapterClass, "a",
                    dbMomentClass, viewHolderClass, Boolean::class.javaPrimitiveType, String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            replaceMomentName(param)
                        }
                    }
                )
                Logger.log(TAG, ">>> hookMomentEdit: 主列表发布者名字 hook 注册成功")
            } catch (t: Throwable) {
                Logger.e(TAG, ">>> hookMomentEdit: 主列表发布者名字 hook 注册失败", t)
            }

            // 详情页 Adapter 设置名字的方法 a(DbMoment, ViewHolder, boolean, String)
            try {
                val detailsAdapterClass = XposedHelpers.findClass("com.xtc.moment.module.details.a.a", app.classLoader)
                XposedHelpers.findAndHookMethod(
                    detailsAdapterClass, "a",
                    dbMomentClass, viewHolderClass, Boolean::class.javaPrimitiveType, String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            replaceMomentName(param)
                        }
                    }
                )
                Logger.log(TAG, ">>> hookMomentEdit: 详情页发布者名字 hook 注册成功")
            } catch (t: Throwable) {
                Logger.e(TAG, ">>> hookMomentEdit: 详情页发布者名字 hook 注册失败", t)
            }

            // 官方消息兜底:覆盖 DbMoment.getName()
            try {
                XposedHelpers.findAndHookMethod(
                    dbMomentClass,
                    "getName",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                val momentId = XposedHelpers.callMethod(param.thisObject, "getMomentId") as? String ?: return
                                val edited = EditedMomentStore.get(momentId) ?: return
                                val name = edited.name ?: return
                                param.result = name
                            } catch (_: Throwable) {
                            }
                        }
                    }
                )
                Logger.log(TAG, ">>> hookMomentEdit: 官方消息名字 hook 注册成功")
            } catch (t: Throwable) {
                Logger.e(TAG, ">>> hookMomentEdit: 官方消息名字 hook 注册失败", t)
            }
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookMomentEdit: 发布者名字 hook 注册失败", t)
        }

        Logger.log(TAG, ">>> hookMomentEdit 完成")
    }

    /** 替换设置名字方法里的名字参数为编辑后的发布者名字 */
    private fun replaceMomentName(param: XC_MethodHook.MethodHookParam) {
        try {
            val dbMoment = param.args.getOrNull(0) ?: return
            val momentId = XposedHelpers.callMethod(dbMoment, "getMomentId") as? String ?: return
            val edited = EditedMomentStore.get(momentId) ?: return
            val name = edited.name ?: return
            param.args[3] = name
        } catch (_: Throwable) {
        }
    }

    /**
     * hook 链接自动跳转:检测好友圈动态文字中的链接,变蓝色 + 下划线,点击用浏览器打开。
     * 动态文字统一由 ExpandTextView.setText(CharSequence, ClickCheckAllListener) 设置,
     * 实际文字在内部的 ExpandCusTomTextView(tvMoodContent) 上。
     */
    private fun hookLinkJump(app: Application) {
        Logger.log(TAG, ">>> hookLinkJump 开始")
        try {
            val expandTextViewClass = XposedHelpers.findClass("com.xtc.moment.module.widget.ExpandTextView", app.classLoader)
            val listenerClass = XposedHelpers.findClass("com.xtc.moment.module.widget.ExpandTextView\$ClickCheckAllListener", app.classLoader)
            XposedHelpers.findAndHookMethod(
                expandTextViewClass,
                "setText",
                CharSequence::class.java,
                listenerClass,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!linkJumpEnabled) return
                        val tv = try {
                            XposedHelpers.getObjectField(param.thisObject, "tvMoodContent") as? TextView
                        } catch (_: Throwable) {
                            null
                        } ?: return
                        applyLinkSpans(tv)
                    }
                }
            )
            Logger.log(TAG, ">>> hookLinkJump: hook 注册成功")
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookLinkJump: hook 注册失败", t)
        }
        Logger.log(TAG, ">>> hookLinkJump 完成")
    }

    /**
     * hook 自定义字体:拦截好友圈进程内所有 TextView 的构造,统一设置自定义 Typeface。
     * 字体文件由模块 UI 拷贝到 /sdcard/laoli_hooktools/custom_font.ttf,
     * 这里读配置决定是否启用,并缓存 Typeface 避免反复加载。
     */
    private fun hookFont(app: Application) {
        Logger.log(TAG, ">>> hookFont 开始")
        if (!fontEnabled) {
            Logger.log(TAG, ">>> hookFont: 字体未启用,跳过")
            return
        }

        val typeface = resolveCustomTypeface()
        if (typeface == null) {
            Logger.e(TAG, ">>> hookFont: Typeface 加载失败,跳过")
            return
        }

        // 三个 TextView 构造器:拦截所有 TextView 子类(含好友圈自定义 View)的创建
        val constructors: List<Array<Class<*>>> = listOf(
            arrayOf(Context::class.java),
            arrayOf(Context::class.java, android.util.AttributeSet::class.java),
            arrayOf(
                Context::class.java,
                android.util.AttributeSet::class.java,
                java.lang.Integer.TYPE
            )
        )

        for (sig in constructors) {
            try {
                XposedHelpers.findAndHookConstructor(
                    TextView::class.java,
                    *sig,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val tv = param.thisObject as? TextView ?: return
                            val tf = customTypeface ?: return
                            try {
                                tv.typeface = tf
                            } catch (_: Throwable) {
                            }
                        }
                    }
                )
                Logger.log(TAG, ">>> hookFont: TextView 构造器 hook 注册成功: ${sig.joinToString { it.name }}")
            } catch (t: Throwable) {
                Logger.e(TAG, ">>> hookFont: 构造器 hook 注册失败: ${t.message}", t)
            }
        }

        // 兜底:hook TextView.setText 后再次应用字体,
        // 防止某些 View 在 setText 时通过 setTypeface 重置字体。
        try {
            XposedHelpers.findAndHookMethod(
                TextView::class.java,
                "setText",
                CharSequence::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val tv = param.thisObject as? TextView ?: return
                        val tf = customTypeface ?: return
                        try {
                            if (tv.typeface !== tf) {
                                tv.typeface = tf
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
            )
            Logger.log(TAG, ">>> hookFont: TextView.setText hook 注册成功")
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookFont: setText hook 注册失败", t)
        }

        Logger.log(TAG, ">>> hookFont 完成")
    }

    /** 从配置路径加载自定义 Typeface(带缓存) */
    private fun resolveCustomTypeface(): Typeface? {
        customTypeface?.let { return it }
        if (!fontEnabled) return null
        val path = fontPath ?: return null
        return try {
            val file = File(path)
            if (!file.exists()) {
                Logger.e(TAG, ">>> 自定义字体文件不存在: $path")
                null
            } else {
                android.graphics.Typeface.createFromFile(file).also {
                    customTypeface = it
                    Logger.log(TAG, ">>> 自定义字体加载成功: $path")
                }
            }
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> 自定义字体加载失败", t)
            null
        }
    }

    /**
     * hook 点赞数量修改 + 详情页点赞用户名。
     *
     * 1. 覆盖 DbMoment.getLikeTotal(),返回编辑界面设置的点赞数。
     * 2. 覆盖 MomentLikeView.setCount(),被编辑的动态强制显示具体数字(绕过 99+ 模糊)。
     * 3. 详情页(MomentLikesActivity)点赞用户名统一显示"老李不会飞"。
     */
    private fun hookLikeDisplay(app: Application) {
        Logger.log(TAG, ">>> hookLikeDisplay 开始")

        // 1. 覆盖 getLikeTotal 返回编辑后的点赞数
        try {
            val dbMomentClass = XposedHelpers.findClass("com.xtc.moment.db.bean.DbMoment", app.classLoader)
            XposedHelpers.findAndHookMethod(
                dbMomentClass,
                "getLikeTotal",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val momentId = XposedHelpers.callMethod(param.thisObject, "getMomentId") as? String
                            if (momentId == null) return
                            val edited = EditedMomentStore.get(momentId)
                            val likeCount = edited?.likeCount
                            lastEditedLikeCount = likeCount
                            if (likeCount != null) {
                                param.result = likeCount
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
            )
            Logger.log(TAG, ">>> hookLikeDisplay: getLikeTotal hook 注册成功")
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookLikeDisplay: getLikeTotal hook 注册失败", t)
        }

        // 2. 覆盖 setCount,被编辑的动态强制显示具体数字(绕过 99+)
        try {
            val likeViewClass = XposedHelpers.findClass("com.xtc.moment.module.widget.MomentLikeView", app.classLoader)
            XposedHelpers.findAndHookMethod(
                likeViewClass,
                "setCount",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val editedCount = lastEditedLikeCount ?: return
                        try {
                            val tv = XposedHelpers.getObjectField(param.thisObject, "mTvLikeCount") as? TextView ?: return
                            tv.text = editedCount.toString()
                        } catch (_: Throwable) {
                        }
                    }
                }
            )
            Logger.log(TAG, ">>> hookLikeDisplay: setCount hook 注册成功")
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookLikeDisplay: setCount hook 注册失败", t)
        }

        // 3. 分享页/动态页的点赞用户区域,仅对"修改了点赞数"的动态显示 N 个"老李不会飞"
        //    点赞用户文本由 share.b.a.a(DbMoment, Map, String, ab) 拼接,这里在之后覆盖为 N 个。
        try {
            val shareHolderClass = XposedHelpers.findClass("com.xtc.moment.module.share.b.a", app.classLoader)
            val dbMomentClass = XposedHelpers.findClass("com.xtc.moment.db.bean.DbMoment", app.classLoader)
            val abClass = XposedHelpers.findClass("com.xtc.moment.util.ab", app.classLoader)
            XposedHelpers.findAndHookMethod(
                shareHolderClass,
                "a",
                dbMomentClass,
                java.util.Map::class.java,
                String::class.java,
                abClass,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val dbMoment = param.args.getOrNull(0) ?: return
                            val momentId = XposedHelpers.callMethod(dbMoment, "getMomentId") as? String ?: return
                            val edited = EditedMomentStore.get(momentId)
                            val likeCount = edited?.likeCount ?: return
                            if (likeCount <= 0) return
                            val tv = XposedHelpers.getObjectField(param.thisObject, "o") as? TextView ?: return
                            val sb = StringBuilder()
                            for (i in 0 until likeCount) {
                                if (i > 0) sb.append(", ")
                                sb.append("老李不会飞")
                            }
                            tv.text = sb.toString()
                            tv.visibility = View.VISIBLE
                        } catch (_: Throwable) {
                        }
                    }
                }
            )
            Logger.log(TAG, ">>> hookLikeDisplay: 点赞用户数量 hook 注册成功")
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookLikeDisplay: 点赞用户数量 hook 注册失败", t)
        }

        Logger.log(TAG, ">>> hookLikeDisplay 完成")
    }

    /**
     * hook 保存用户头像:长按动态里的头像,弹出"保存头像"。
     *
     * 好友圈主列表/详情页的头像都通过 AbsMomentView.setIconOnClickListener(DbMoment)
     * 绑定点击事件,这里在 after 阶段给 mIcon 额外挂一个 onLongClickListener。
     * 头像 URL 优先取 getIcon()(mIconPath),为空则从 ImageView 当前 drawable 取 bitmap 兜底。
     */
    private fun hookSaveAvatar(app: Application) {
        Logger.log(TAG, ">>> hookSaveAvatar 开始")
        try {
            val absMomentViewClass = XposedHelpers.findClass("com.xtc.moment.module.widget.AbsMomentView", app.classLoader)
            val dbMomentClass = XposedHelpers.findClass("com.xtc.moment.db.bean.DbMoment", app.classLoader)
            XposedHelpers.findAndHookMethod(
                absMomentViewClass,
                "setIconOnClickListener",
                dbMomentClass,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val view = param.thisObject as? View
                            if (view == null) {
                                Logger.log(TAG, ">>> hookSaveAvatar: thisObject 不是 View: ${param.thisObject?.javaClass?.name}")
                                return
                            }
                            val icon = XposedHelpers.getObjectField(view, "mIcon") as? ImageView
                            if (icon == null) {
                                Logger.log(TAG, ">>> hookSaveAvatar: mIcon 字段获取失败, view=${view.javaClass.name}")
                                return
                            }
                            icon.isLongClickable = true
                            icon.setOnLongClickListener {
                                Logger.log(TAG, ">>> hookSaveAvatar: 头像被长按")
                                onAvatarLongClick(view, icon)
                                true
                            }
                        } catch (t: Throwable) {
                            Logger.e(TAG, ">>> hookSaveAvatar: after 阶段异常", t)
                        }
                    }
                }
            )
            Logger.log(TAG, ">>> hookSaveAvatar: hook 注册成功")
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookSaveAvatar: hook 注册失败", t)
        }
        Logger.log(TAG, ">>> hookSaveAvatar 完成")
    }

    /**
     * hook 评论修改:长按评论弹出好朋友圈风格的弹窗,可修改评论颜色、下划线、评论者名称。
     *
     * 好友圈评论有两个展示位置:
     *   1. 主列表: MainMomentCommentView.setCommentText(TextView, Context, DbMomentComment)
     *   2. 详情页: MomentCommentAdapter(c).onBindViewHolder -> ViewHolder(b).a(TextView)
     * 两者都用 DbMomentComment 的 commentId 唯一标识,通过 getWatchName() 取评论者名。
     */
    private fun hookCommentEdit(app: Application) {
        Logger.log(TAG, ">>> hookCommentEdit 开始")
        targetApp = app

        // 0. 记录当前 onResume 的 Activity,弹 Dialog 时用它兜底获取有效 context
        try {
            XposedHelpers.findAndHookMethod(
                Activity::class.java,
                "onResume",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        currentActivity = param.thisObject as? Activity
                    }
                }
            )
            Logger.log(TAG, ">>> hookCommentEdit: Activity.onResume hook 注册成功")
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookCommentEdit: Activity.onResume hook 注册失败", t)
        }

        val dbMomentCommentClass = try {
            XposedHelpers.findClass("com.xtc.moment.db.bean.DbMomentComment", app.classLoader)
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookCommentEdit: 找不到 DbMomentComment", t)
            null
        }

        // 1. 评论者名称覆盖(getWatchName 全局生效,主列表/详情页都走这里取名字)
        try {
            XposedHelpers.findAndHookMethod(
                dbMomentCommentClass,
                "getWatchName",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val commentId = XposedHelpers.callMethod(param.thisObject, "getCommentId") as? String ?: return
                            val edited = EditedCommentStore.get(commentId) ?: return
                            val name = edited.name ?: return
                            param.result = name
                        } catch (_: Throwable) {
                        }
                    }
                }
            )
            Logger.log(TAG, ">>> hookCommentEdit: getWatchName 覆盖 hook 注册成功")
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookCommentEdit: getWatchName 覆盖 hook 注册失败", t)
        }

        // 1.5 评论内容覆盖(getComment 全局生效,主列表/详情页都走这里取评论内容)
        try {
            XposedHelpers.findAndHookMethod(
                dbMomentCommentClass,
                "getComment",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val commentId = XposedHelpers.callMethod(param.thisObject, "getCommentId") as? String ?: return
                            val edited = EditedCommentStore.get(commentId) ?: return
                            val text = edited.text ?: return
                            param.result = text
                        } catch (_: Throwable) {
                        }
                    }
                }
            )
            Logger.log(TAG, ">>> hookCommentEdit: getComment 覆盖 hook 注册成功")
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookCommentEdit: getComment 覆盖 hook 注册失败", t)
        }

        // 2. 主列表评论渲染(setCommentText):应用颜色/下划线
        try {
            val mainViewClass = XposedHelpers.findClass(
                "com.xtc.moment.module.widget.MainMomentCommentView", app.classLoader
            )
            XposedHelpers.findAndHookMethod(
                mainViewClass,
                "setCommentText",
                TextView::class.java,
                Context::class.java,
                dbMomentCommentClass,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val tv = param.args.getOrNull(0) as? TextView ?: return
                        val comment = param.args.getOrNull(2) ?: return
                        applyCommentContentStyle(tv, comment)
                    }
                }
            )
            Logger.log(TAG, ">>> hookCommentEdit: 主列表 setCommentText hook 注册成功")
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookCommentEdit: 主列表 setCommentText hook 注册失败", t)
        }

        // 3. 详情页评论渲染(onBindViewHolder):应用颜色/下划线
        try {
            val adapterClass = XposedHelpers.findClass("com.xtc.moment.module.b.a.c", app.classLoader)
            val viewHolderClass = XposedHelpers.findClass(
                "android.support.v7.widget.RecyclerView\$ViewHolder", app.classLoader
            )
            XposedHelpers.findAndHookMethod(
                adapterClass,
                "onBindViewHolder",
                viewHolderClass,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val holder = param.args.getOrNull(0) ?: return
                        val tv = try {
                            XposedHelpers.getObjectField(holder, "a") as? TextView
                        } catch (_: Throwable) {
                            null
                        } ?: return
                        val comment = try {
                            XposedHelpers.getObjectField(holder, "d")
                        } catch (_: Throwable) {
                            null
                        } ?: return
                        applyCommentContentStyle(tv, comment)
                    }
                }
            )
            Logger.log(TAG, ">>> hookCommentEdit: 详情页 onBindViewHolder hook 注册成功")
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookCommentEdit: 详情页 onBindViewHolder hook 注册失败", t)
        }

        // 4. 评论长按统一入口:长按别人的评论时,拦截原生"返回/举报"弹窗,换成"编辑/取消/举报"
        try {
            val dbMomentClass = XposedHelpers.findClass("com.xtc.moment.db.bean.DbMoment", app.classLoader)
            val reportAdapterClass = XposedHelpers.findClass("com.xtc.moment.module.report.a.a", app.classLoader)
            XposedHelpers.findAndHookMethod(
                reportAdapterClass,
                "a",
                dbMomentClass,
                dbMomentCommentClass,
                String::class.java,
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val comment = param.args.getOrNull(1) ?: return
                        val myWatchId = param.args.getOrNull(2) as? String
                        val commentWatchId = try {
                            XposedHelpers.callMethod(comment, "getWatchId") as? String
                        } catch (_: Throwable) {
                            null
                        }
                        // 自己的评论走原生删除逻辑,不拦截
                        if (myWatchId != null && commentWatchId != null && myWatchId == commentWatchId) return
                        val adapter = param.thisObject
                        val context = try {
                            XposedHelpers.getObjectField(adapter, "u") as? Context
                        } catch (_: Throwable) {
                            null
                        } ?: currentActivity ?: return
                        Logger.log(TAG, ">>> hookCommentEdit: 长按别人评论,弹编辑弹窗 context=${context.javaClass.name}")
                        showCommentEditDialog(context, comment, adapter)
                        param.result = null
                    }
                }
            )
            Logger.log(TAG, ">>> hookCommentEdit: 评论长按入口 hook 注册成功")
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookCommentEdit: 评论长按入口 hook 注册失败", t)
        }

        Logger.log(TAG, ">>> hookCommentEdit 完成")
    }

    /** 对已渲染的评论 TextView 应用编辑后的颜色/下划线(只改评论内容部分,名字保持原灰) */
    private fun applyCommentContentStyle(tv: TextView, comment: Any) {
        try {
            val commentId = XposedHelpers.callMethod(comment, "getCommentId") as? String ?: return
            val edited = EditedCommentStore.get(commentId) ?: return
            if (edited.color == null && !edited.underline) return
            val raw = tv.text ?: return
            val spannable = if (raw is android.text.Spannable) raw else android.text.SpannableString(raw)
            val contentStart = commentContentStart(tv.context, comment)
            val contentEnd = spannable.length
            if (contentStart < 0 || contentStart >= contentEnd) return
            if (edited.color != null) {
                // 先移除内容区间原有颜色 span,再应用新色,避免叠加冲突
                spannable.getSpans(contentStart, contentEnd, android.text.style.ForegroundColorSpan::class.java)
                    .forEach { spannable.removeSpan(it) }
                spannable.setSpan(
                    android.text.style.ForegroundColorSpan(edited.color),
                    contentStart, contentEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            if (edited.underline) {
                spannable.setSpan(
                    android.text.style.UnderlineSpan(),
                    contentStart, contentEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            if (spannable !== raw) tv.text = spannable
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookCommentEdit: 应用评论样式失败", t)
        }
    }

    /** 计算评论内容(分隔符+评论文字)在整条评论文本中的起始偏移 */
    private fun commentContentStart(context: Context, comment: Any): Int {
        val watchName = XposedHelpers.callMethod(comment, "getWatchName") as? String ?: ""
        val displayName = if (watchName.isEmpty()) stringRes(context, "unknown_watch") else watchName
        var start = displayName.length
        val replyName = XposedHelpers.callMethod(comment, "getReplyName") as? String ?: ""
        if (replyName.isNotEmpty()) {
            start += stringRes(context, "reply").length + replyName.length
        }
        return start
    }

    /** 读取好友圈字符串资源(不存在返回空串) */
    private fun stringRes(context: Context, name: String): String {
        val id = context.resources.getIdentifier(name, "string", "com.xtc.moment")
        return if (id != 0) context.getString(id) else ""
    }

    /** 沿 ContextWrapper 链向上找 Activity(找不到返回 null) */
    private fun findActivity(context: Context): android.app.Activity? {
        var ctx: Context? = context
        while (ctx != null) {
            if (ctx is android.app.Activity) return ctx
            ctx = if (ctx is android.content.ContextWrapper) ctx.baseContext else null
        }
        return null
    }

    /** 长按别人的评论:用 CommonCopyDialog 弹"取消/编辑/举报" */
    private fun showCommentEditDialog(context: Context, comment: Any, adapter: Any?) {
        val app = targetApp
        if (app == null) {
            postToast(context, "模块未初始化")
            return
        }
        try {
            // CommonCopyDialog.show() 内部会用 k.b(context) 校验 Activity 有效性,
            // 优先从 context 链找 Activity,找不到就用当前 onResume 的 Activity 兜底。
            val activity = findActivity(context) ?: currentActivity
            val dialogContext = activity ?: context
            Logger.log(TAG, ">>> hookCommentEdit: 弹窗 context=${dialogContext.javaClass.name}")

            val dialogClass = XposedHelpers.findClass("com.xtc.moment.util.i", app.classLoader)
            val dialog = XposedHelpers.newInstance(dialogClass, dialogContext) as? android.app.Dialog ?: return

            val res = dialogContext.resources
            val pkg = "com.xtc.moment"

            val slot1Id = res.getIdentifier("ll_common_one_root", "id", pkg)
            val slot1ImgId = res.getIdentifier("iv_common_one_image", "id", pkg)
            val slot1TxtId = res.getIdentifier("tv_common_one_text", "id", pkg)
            val slot2Id = res.getIdentifier("ll_common_two_root", "id", pkg)
            val slot2ImgId = res.getIdentifier("iv_common_two_image", "id", pkg)
            val slot2TxtId = res.getIdentifier("tv_common_two_text", "id", pkg)
            val slot3Id = res.getIdentifier("ll_common_three_root", "id", pkg)
            val slot3ImgId = res.getIdentifier("iv_common_three_image", "id", pkg)
            val slot3TxtId = res.getIdentifier("tv_common_three_text", "id", pkg)
            val slot4Id = res.getIdentifier("ll_common_four_root", "id", pkg)

            val bgGray = res.getIdentifier("circle_btn_bg_gray", "drawable", pkg)
            val bgYellow = res.getIdentifier("circle_btn_bg_yellow", "drawable", pkg)
            val icCancel = res.getIdentifier("cancel", "drawable", pkg)
            val icEdit = res.getIdentifier("ic_edit_new", "drawable", pkg)
            val icReport = res.getIdentifier("ic_chat_report", "drawable", pkg)

            val slot1 = dialog.findViewById<LinearLayout>(slot1Id)
            val slot1Img = dialog.findViewById<ImageView>(slot1ImgId)
            val slot1Txt = dialog.findViewById<TextView>(slot1TxtId)
            val slot2 = dialog.findViewById<LinearLayout>(slot2Id)
            val slot2Img = dialog.findViewById<ImageView>(slot2ImgId)
            val slot2Txt = dialog.findViewById<TextView>(slot2TxtId)
            val slot3 = dialog.findViewById<LinearLayout>(slot3Id)
            val slot3Img = dialog.findViewById<ImageView>(slot3ImgId)
            val slot3Txt = dialog.findViewById<TextView>(slot3TxtId)
            val slot4 = dialog.findViewById<LinearLayout>(slot4Id)

            // 槽位1 = 取消
            slot1Img?.setBackgroundResource(bgGray)
            slot1Img?.setImageResource(icCancel)
            slot1Txt?.text = "取消"
            slot1.setOnClickListener { dialog.dismiss() }

            // 槽位2 = 编辑
            slot2Img?.setBackgroundResource(bgYellow)
            slot2Img?.setImageResource(icEdit)
            slot2Txt?.text = "编辑"
            slot2.setOnClickListener {
                dialog.dismiss()
                launchEditCommentActivity(context, comment)
            }

            // 槽位3 = 举报
            slot3?.visibility = View.VISIBLE
            slot3Img?.setBackgroundResource(bgYellow)
            slot3Img?.setImageResource(icReport)
            slot3Txt?.text = "举报"
            slot3.setOnClickListener {
                dialog.dismiss()
                reportComment(adapter, comment)
            }

            // 槽位4 隐藏
            slot4?.visibility = View.GONE

            dialog.show()
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookCommentEdit: 弹窗失败", t)
            postToast(context, "弹窗失败")
        }
    }

    /** 举报评论:调用好友圈 adapter 的举报方法(等价于原生"举报"按钮) */
    private fun reportComment(adapter: Any?, comment: Any) {
        if (adapter == null) return
        try {
            val watchId = XposedHelpers.callMethod(comment, "getWatchId") as? String ?: ""
            val momentId = XposedHelpers.callMethod(comment, "getMomentId") as? String ?: ""
            val commentId = XposedHelpers.callMethod(comment, "getCommentId") as? String ?: ""
            val momentWatchId = XposedHelpers.callMethod(comment, "getMomentWatchId") as? String ?: ""
            XposedHelpers.callMethod(adapter, "a", watchId, momentId, commentId, momentWatchId)
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookCommentEdit: 举报失败", t)
        }
    }

    /** 启动模块的评论编辑界面(跨进程) */
    private fun launchEditCommentActivity(context: Context, comment: Any) {
        try {
            val commentId = XposedHelpers.callMethod(comment, "getCommentId") as? String ?: return
            // 直接读原始字段,避免被 getWatchName/getComment hook 覆盖影响初始显示
            val realText = XposedHelpers.getObjectField(comment, "comment") as? String ?: ""
            val realName = XposedHelpers.getObjectField(comment, "watchName") as? String ?: ""
            val existing = EditedCommentStore.get(commentId)

            val intent = android.content.Intent().apply {
                setClassName("com.laoli.hooktools", "com.laoli.hooktools.ui.EditCommentActivity")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("commentId", commentId)
                putExtra("text", existing?.text ?: realText)
                putExtra("name", existing?.name ?: realName)
                putExtra("color", existing?.color ?: Color.parseColor("#D9D9D9"))
                putExtra("underline", existing?.underline ?: false)
            }
            context.startActivity(intent)
            Logger.log(TAG, ">>> hookCommentEdit: 已启动评论编辑界面 commentId=$commentId")
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookCommentEdit: 启动评论编辑界面失败", t)
            Toast.makeText(context, "打开评论编辑界面失败", Toast.LENGTH_SHORT).show()
        }
    }

    /** 长按头像:用好朋友圈自带的 CommonCopyDialog 弹"保存头像/取消" */
    private fun onAvatarLongClick(view: View, icon: ImageView) {
        val app = targetApp
        if (app == null) {
            postToast(icon.context, "模块未初始化")
            return
        }
        val context = try {
            XposedHelpers.callMethod(view, "getMyContext") as? Context
        } catch (_: Throwable) {
            null
        } ?: icon.context
        try {
            val dialogClass = XposedHelpers.findClass("com.xtc.moment.util.i", app.classLoader)
            val dialog = XposedHelpers.newInstance(dialogClass, context) as? android.app.Dialog ?: return

            val res = context.resources
            val pkg = "com.xtc.moment"

            val slot1Id = res.getIdentifier("ll_common_one_root", "id", pkg)
            val slot1ImgId = res.getIdentifier("iv_common_one_image", "id", pkg)
            val slot1TxtId = res.getIdentifier("tv_common_one_text", "id", pkg)
            val slot2Id = res.getIdentifier("ll_common_two_root", "id", pkg)
            val slot2ImgId = res.getIdentifier("iv_common_two_image", "id", pkg)
            val slot2TxtId = res.getIdentifier("tv_common_two_text", "id", pkg)
            val slot3Id = res.getIdentifier("ll_common_three_root", "id", pkg)
            val slot4Id = res.getIdentifier("ll_common_four_root", "id", pkg)

            val bgGray = res.getIdentifier("circle_btn_bg_gray", "drawable", pkg)
            val bgYellow = res.getIdentifier("circle_btn_bg_yellow", "drawable", pkg)
            val icCancel = res.getIdentifier("cancel", "drawable", pkg)
            val icSave = res.getIdentifier("ic_continue", "drawable", pkg)

            val slot1 = dialog.findViewById<LinearLayout>(slot1Id)
            val slot1Img = dialog.findViewById<ImageView>(slot1ImgId)
            val slot1Txt = dialog.findViewById<TextView>(slot1TxtId)
            val slot2 = dialog.findViewById<LinearLayout>(slot2Id)
            val slot2Img = dialog.findViewById<ImageView>(slot2ImgId)
            val slot2Txt = dialog.findViewById<TextView>(slot2TxtId)
            val slot3 = dialog.findViewById<LinearLayout>(slot3Id)
            val slot4 = dialog.findViewById<LinearLayout>(slot4Id)

            // 槽位1 = 取消
            slot1Img?.setBackgroundResource(bgGray)
            slot1Img?.setImageResource(icCancel)
            slot1Txt?.text = "取消"
            slot1.setOnClickListener { dialog.dismiss() }

            // 槽位2 = 保存头像
            slot2Img?.setBackgroundResource(bgYellow)
            slot2Img?.setImageResource(icSave)
            slot2Txt?.text = "保存头像"
            slot2.setOnClickListener {
                dialog.dismiss()
                Thread { saveAvatarToGallery(context, view, icon) }.start()
            }

            // 槽位3、4 隐藏
            slot3?.visibility = View.GONE
            slot4?.visibility = View.GONE

            dialog.show()
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookSaveAvatar: 弹窗失败", t)
            postToast(context, "长按已触发，但弹窗失败")
        }
    }

    /** 保存头像:优先 URL 下载,无 URL 则取 bitmap */
    private fun saveAvatarToGallery(context: Context, view: View, icon: ImageView) {
        try {
            val url = XposedHelpers.callMethod(view, "getIcon") as? String
            if (!url.isNullOrEmpty()) {
                val ok = downloadToGallery(context, url, false)
                postToast(context, if (ok) "头像已保存到 Pictures/Laoli" else "头像保存失败")
                return
            }
            val drawable = icon.drawable
            val bmp = if (drawable is BitmapDrawable) drawable.bitmap else null
            if (bmp == null) {
                postToast(context, "未获取到头像")
                return
            }
            val ok = saveBitmapToGallery(context, bmp)
            postToast(context, if (ok) "头像已保存到 Pictures/Laoli" else "头像保存失败")
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookSaveAvatar: 保存失败", t)
            postToast(context, "头像保存失败: ${t.message}")
        }
    }

    /** 保存 bitmap 到相册(兼容 Android 8.1 与 Android 10+) */
    private fun saveBitmapToGallery(context: Context, bmp: Bitmap): Boolean {
        return try {
            val name = "laoli_${System.currentTimeMillis()}.jpg"
            val mime = "image/jpeg"
            val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Laoli")
                }
                val uri = context.contentResolver.insert(collection, values) ?: return false
                context.contentResolver.openOutputStream(uri)?.use {
                    bmp.compress(Bitmap.CompressFormat.JPEG, 100, it)
                } ?: return false
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "Laoli"
                )
                dir.mkdirs()
                val file = File(dir, name)
                file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 100, it) }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.DATA, file.absolutePath)
                }
                context.contentResolver.insert(collection, values)
            }
            true
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookSaveAvatar: 保存 bitmap 失败", t)
            false
        }
    }

    /** 检测 TextView 文本中的链接,加蓝色下划线 URLSpan 并开启点击 */
    private fun applyLinkSpans(tv: TextView) {
        try {
            val raw = tv.text ?: return
            val spannable = if (raw is Spannable) raw else SpannableString(raw)
            val matcher = URL_PATTERN.matcher(spannable)
            var changed = false
            while (matcher.find()) {
                val start = matcher.start()
                val end = matcher.end()
                var url = matcher.group() ?: continue
                if (url.startsWith("www.", ignoreCase = true)) {
                    url = "http://$url"
                }
                spannable.setSpan(BlueUrlSpan(url), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                changed = true
            }
            if (changed) {
                tv.movementMethod = LinkMovementMethod.getInstance()
                if (spannable !== raw) {
                    tv.text = spannable
                }
            }
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookLinkJump: 应用链接样式失败", t)
        }
    }

    /** 自定义 URLSpan:强制蓝色 + 下划线 */
    private class BlueUrlSpan(url: String) : URLSpan(url) {
        override fun updateDrawState(ds: android.text.TextPaint) {
            super.updateDrawState(ds)
            ds.color = android.graphics.Color.parseColor("#3399FF")
            ds.isUnderlineText = true
        }
    }

    /** 按动态类型注入按钮:文本 -> 编辑,图片/视频 -> 保存 */
    private fun injectMomentButton(dialog: android.app.Dialog?, moment: Any?) {
        if (dialog == null || moment == null) return
        if (moment.javaClass.name != "com.xtc.moment.db.bean.DbMoment") return
        // 给"取消"按钮(槽位1)注入长按收藏
        injectFavoriteLongClick(dialog, moment)
        if (isMediaMoment(moment)) {
            // 媒体动态:保存(槽位3) + 编辑(槽位4)
            injectSaveButton(dialog, moment)
            injectEditButton(dialog, moment)
        } else {
            injectEditButton(dialog, moment)
        }
    }

    /** 给长按弹窗里的"取消"按钮(槽位1)注入长按收藏 */
    private fun injectFavoriteLongClick(dialog: android.app.Dialog?, moment: Any?) {
        if (dialog == null || moment == null) return
        try {
            val res = dialog.context.resources
            val pkg = "com.xtc.moment"
            val slotRootId = res.getIdentifier("ll_common_one_root", "id", pkg)
            if (slotRootId == 0) return
            val slotRoot = dialog.findViewById<LinearLayout>(slotRootId) ?: return
            slotRoot.setOnLongClickListener {
                favoriteMoment(dialog.context, moment)
                true
            }
            Logger.log(TAG, ">>> hookFavorite: 已给取消按钮注入长按收藏")
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookFavorite: 注入长按收藏失败", t)
        }
    }

    /** 判断动态是否为图片/视频(媒体)类型,而非纯文本 */
    private fun isMediaMoment(moment: Any): Boolean {
        val type = try {
            XposedHelpers.callMethod(moment, "getType") as? Int ?: -1
        } catch (_: Throwable) {
            -1
        }
        return when (type) {
            4, 5, 6, 8, 9, 11, 12, 13, 14, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30 -> true
            else -> false
        }
    }

    /** 向长按弹窗注入"编辑"按钮(使用第 4 个槽位,在 b/f 弹窗中默认隐藏) */
    private fun injectEditButton(dialog: android.app.Dialog?, moment: Any?) {
        if (dialog == null || moment == null) return
        try {
            if (moment.javaClass.name != "com.xtc.moment.db.bean.DbMoment") return

            val context = dialog.context
            val res = context.resources
            val pkg = "com.xtc.moment"

            val slotRootId = res.getIdentifier("ll_common_four_root", "id", pkg)
            val slotImgId = res.getIdentifier("iv_common_four_image", "id", pkg)
            val slotTxtId = res.getIdentifier("tv_common_four_text", "id", pkg)
            val icEditId = res.getIdentifier("ic_edit_new", "drawable", pkg)
            if (slotRootId == 0 || slotImgId == 0 || slotTxtId == 0 || icEditId == 0) {
                Logger.log(TAG, ">>> hookMomentEdit: 编辑按钮资源 id 未找到")
                return
            }

            val slotRoot = dialog.findViewById<LinearLayout>(slotRootId) ?: return
            val slotImg = dialog.findViewById<ImageView>(slotImgId)
            val slotTxt = dialog.findViewById<TextView>(slotTxtId)

            slotRoot.visibility = View.VISIBLE
            slotImg?.setImageResource(icEditId)
            slotTxt?.text = "编辑"

            slotRoot.setOnClickListener {
                launchEditActivity(dialog.context, moment)
            }
            Logger.log(TAG, ">>> hookMomentEdit: 已注入编辑按钮")
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookMomentEdit: 注入编辑按钮失败", t)
        }
    }

    /** 向长按弹窗注入"保存"按钮(使用第 3 个槽位,原为"复制",覆盖为保存) */
    private fun injectSaveButton(dialog: android.app.Dialog?, moment: Any?) {
        if (dialog == null || moment == null) return
        try {
            if (moment.javaClass.name != "com.xtc.moment.db.bean.DbMoment") return

            val context = dialog.context
            val res = context.resources
            val pkg = "com.xtc.moment"

            val slotRootId = res.getIdentifier("ll_common_three_root", "id", pkg)
            val slotImgId = res.getIdentifier("iv_common_three_image", "id", pkg)
            val slotTxtId = res.getIdentifier("tv_common_three_text", "id", pkg)
            val icSaveId = res.getIdentifier("ic_continue", "drawable", pkg)
            if (slotRootId == 0 || slotImgId == 0 || slotTxtId == 0 || icSaveId == 0) {
                Logger.log(TAG, ">>> hookSaveMedia: 保存按钮资源 id 未找到")
                return
            }

            val slotRoot = dialog.findViewById<LinearLayout>(slotRootId) ?: return
            val slotImg = dialog.findViewById<ImageView>(slotImgId)
            val slotTxt = dialog.findViewById<TextView>(slotTxtId)

            slotRoot.visibility = View.VISIBLE
            slotImg?.setImageResource(icSaveId)
            slotTxt?.text = "保存"

            slotRoot.setOnClickListener {
                saveMomentMedia(context, moment)
            }
            Logger.log(TAG, ">>> hookSaveMedia: 已注入保存按钮")
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookSaveMedia: 注入保存按钮失败", t)
        }
    }

    /**
     * 从匿名 listener / callback 对象的字段中提取 DbMoment。
     * 图片/视频长按弹窗的回调(如 i.b 的 listener、showReportBtnDialog 的 InterfaceC0148a)
     * 会把当前动态作为字段捕获,有时嵌套在外层匿名类的 this$0 里。
     * 这里只沿着 this$0 链向上扫描每层的直接字段,避免误入 adapter 等大对象取到错误动态。
     */
    private fun extractDbMoment(obj: Any?): Any? {
        if (obj == null) return null
        var current: Any? = obj
        var guard = 0
        while (current != null && guard < 6) {
            extractDirectDbMoment(current)?.let { return it }
            current = try {
                val f = current.javaClass.getDeclaredField("this$0")
                f.isAccessible = true
                f.get(current)
            } catch (_: Throwable) {
                null
            }
            guard++
        }
        return null
    }

    private fun extractDirectDbMoment(obj: Any?): Any? {
        if (obj == null) return null
        if (obj.javaClass.name == "com.xtc.moment.db.bean.DbMoment") return obj
        var clazz: Class<*>? = obj.javaClass
        while (clazz != null && clazz != Any::class.java) {
            for (f in clazz.declaredFields) {
                try {
                    f.isAccessible = true
                    val v = f.get(obj)
                    if (v != null && v.javaClass.name == "com.xtc.moment.db.bean.DbMoment") return v
                } catch (_: Throwable) {
                }
            }
            clazz = clazz.superclass
        }
        return null
    }

    /** 图片/视频长按弹"编辑/保存/收藏/取消/举报"弹窗(替代原"取消/举报") */
    private fun showSaveReportDialog(view: View, moment: Any, reportCallback: Any?) {
        val context = view.context
        try {
            AlertDialog.Builder(context)
                .setItems(arrayOf("编辑", "保存", "收藏", "取消", "举报")) { _, which ->
                    when (which) {
                        0 -> launchEditActivity(context, moment)
                        1 -> saveMomentMedia(context, moment)
                        2 -> favoriteMoment(context, moment)
                        4 -> {
                            if (reportCallback != null) {
                                try {
                                    XposedHelpers.callMethod(reportCallback, "onRightBtnClick")
                                } catch (t: Throwable) {
                                    Logger.e(TAG, ">>> hookSaveMedia: 举报回调失败", t)
                                }
                            }
                        }
                    }
                }
                .show()
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookSaveMedia: 弹出编辑/保存/举报弹窗失败", t)
        }
    }

    /** 保存当前动态的图片/视频到相册 */
    private fun saveMomentMedia(context: Context, moment: Any) {
        val app = targetApp ?: return
        Thread {
            try {
                val type = XposedHelpers.callMethod(moment, "getType") as? Int ?: 0
                val content = XposedHelpers.callMethod(moment, "getContent") as? String ?: ""
                val encodeG = XposedHelpers.findClass("com.xtc.utils.encode.g", app.classLoader)

                val urls = mutableListOf<String>()
                var isVideo = false

                fun addUrl(u: String?) {
                    u?.split(",")?.forEach { if (it.isNotBlank()) urls.add(it) }
                }

                when (type) {
                    // 视频动态(6/13/24/27): transfer(视频文件)优先, source 兜底
                    6, 13, 24, 27 -> {
                        isVideo = true
                        val videoClass = XposedHelpers.findClass("com.xtc.moment.module.bean.VideoMsg", app.classLoader)
                        var videoJson = content
                        if (type == 27) {
                            // type 27 的 content 是 MultiPhotoContent 包装,里面 videoMsgContent 才是 VideoMsg
                            val multiClass = XposedHelpers.findClass("com.xtc.moment.module.bean.MultiPhotoContent", app.classLoader)
                            val multi = XposedHelpers.callStaticMethod(encodeG, "a", content, multiClass)
                            if (multi != null) {
                                videoJson = XposedHelpers.callMethod(multi, "getVideoMsgContent") as? String ?: content
                            }
                        }
                        val video = XposedHelpers.callStaticMethod(encodeG, "a", videoJson, videoClass)
                        if (video != null) {
                            addUrl(resourceDownloadUrl(XposedHelpers.callMethod(video, "getTransfer"))
                                ?: resourceDownloadUrl(XposedHelpers.callMethod(video, "getSource")))
                        }
                    }
                    // 多图动态(26/28): resource.downloadUrl 为逗号分隔的多个图片 URL
                    26, 28 -> {
                        val multiClass = XposedHelpers.findClass("com.xtc.moment.module.bean.MultiPhotoContent", app.classLoader)
                        val multi = XposedHelpers.callStaticMethod(encodeG, "a", content, multiClass)
                        if (multi != null) {
                            addUrl(resourceDownloadUrl(XposedHelpers.callMethod(multi, "getResource")))
                        }
                    }
                    // 单图动态: source.downloadUrl 优先, smallPic 兜底
                    else -> {
                        val photoClass = XposedHelpers.findClass("com.xtc.moment.module.bean.PhotoMsg", app.classLoader)
                        val photo = XposedHelpers.callStaticMethod(encodeG, "a", content, photoClass)
                        if (photo != null) {
                            addUrl(resourceDownloadUrl(XposedHelpers.callMethod(photo, "getSource"))
                                ?: resourceDownloadUrl(XposedHelpers.callMethod(photo, "getSmallPic")))
                        }
                    }
                }

                if (urls.isEmpty()) {
                    postToast(context, "未获取到图片/视频链接")
                    return@Thread
                }

                var ok = 0
                for (url in urls) {
                    if (downloadToGallery(context, url, isVideo)) ok++
                }
                val saveDir = File(
                    Environment.getExternalStoragePublicDirectory(
                        if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
                    ),
                    "Laoli"
                )
                postToast(context, "已保存 $ok/${urls.size} 个文件到 ${saveDir.absolutePath}")
            } catch (t: Throwable) {
                Logger.e(TAG, ">>> hookSaveMedia: 保存失败", t)
                postToast(context, "保存失败: ${t.message}")
            }
        }.start()
    }

    /** 提取动态的媒体下载地址(返回 URL 列表 + 是否视频) */
    private fun extractMediaUrls(moment: Any): Pair<List<String>, Boolean> {
        val app = targetApp ?: return emptyList<String>() to false
        return try {
            val type = XposedHelpers.callMethod(moment, "getType") as? Int ?: 0
            val content = XposedHelpers.callMethod(moment, "getContent") as? String ?: ""
            val encodeG = XposedHelpers.findClass("com.xtc.utils.encode.g", app.classLoader)
            val urls = mutableListOf<String>()
            var isVideo = false
            fun addUrl(u: String?) {
                u?.split(",")?.forEach { if (it.isNotBlank()) urls.add(it) }
            }
            when (type) {
                6, 13, 24, 27 -> {
                    isVideo = true
                    val videoClass = XposedHelpers.findClass("com.xtc.moment.module.bean.VideoMsg", app.classLoader)
                    var videoJson = content
                    if (type == 27) {
                        val multiClass = XposedHelpers.findClass("com.xtc.moment.module.bean.MultiPhotoContent", app.classLoader)
                        val multi = XposedHelpers.callStaticMethod(encodeG, "a", content, multiClass)
                        if (multi != null) {
                            videoJson = XposedHelpers.callMethod(multi, "getVideoMsgContent") as? String ?: content
                        }
                    }
                    val video = XposedHelpers.callStaticMethod(encodeG, "a", videoJson, videoClass)
                    if (video != null) {
                        addUrl(resourceDownloadUrl(XposedHelpers.callMethod(video, "getTransfer"))
                            ?: resourceDownloadUrl(XposedHelpers.callMethod(video, "getSource")))
                    }
                }
                26, 28 -> {
                    val multiClass = XposedHelpers.findClass("com.xtc.moment.module.bean.MultiPhotoContent", app.classLoader)
                    val multi = XposedHelpers.callStaticMethod(encodeG, "a", content, multiClass)
                    if (multi != null) {
                        addUrl(resourceDownloadUrl(XposedHelpers.callMethod(multi, "getResource")))
                    }
                }
                else -> {
                    val photoClass = XposedHelpers.findClass("com.xtc.moment.module.bean.PhotoMsg", app.classLoader)
                    val photo = XposedHelpers.callStaticMethod(encodeG, "a", content, photoClass)
                    if (photo != null) {
                        addUrl(resourceDownloadUrl(XposedHelpers.callMethod(photo, "getSource"))
                            ?: resourceDownloadUrl(XposedHelpers.callMethod(photo, "getSmallPic")))
                    }
                }
            }
            urls to isVideo
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookFavorite: 提取媒体链接失败", t)
            emptyList<String>() to false
        }
    }

    /** 提取动态文字(媒体动态优先 description,文本动态用 content) */
    private fun extractMomentText(moment: Any, media: Boolean): String {
        val desc = try {
            XposedHelpers.callMethod(moment, "getDescription") as? String
        } catch (_: Throwable) {
            null
        }
        if (!desc.isNullOrBlank()) return desc
        if (!media) {
            return try {
                XposedHelpers.callMethod(moment, "getContent") as? String ?: ""
            } catch (_: Throwable) {
                ""
            }
        }
        return ""
    }

    /** 下载 url 到指定文件 */
    private fun downloadToFile(urlStr: String, dest: File): Boolean {
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.connect()
            if (conn.responseCode != 200) {
                conn.disconnect()
                return false
            }
            val bytes = conn.inputStream.use { it.readBytes() }
            conn.disconnect()
            if (bytes.isEmpty()) return false
            dest.parentFile?.mkdirs()
            dest.writeBytes(bytes)
            true
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookFavorite: 下载失败 url=$urlStr", t)
            false
        }
    }

    /** 收藏动态:保存文字与媒体到本地,并写入 FavoriteStore */
    private fun favoriteMoment(context: Context, moment: Any) {
        Thread {
            try {
                val momentId = XposedHelpers.callMethod(moment, "getMomentId") as? String ?: ""
                if (momentId.isEmpty()) {
                    postToast(context, "收藏失败：动态 ID 缺失")
                    return@Thread
                }
                if (FavoriteStore.contains(momentId)) {
                    postToast(context, "已收藏过该动态")
                    return@Thread
                }
                val name = XposedHelpers.callMethod(moment, "getName") as? String ?: ""
                val type = XposedHelpers.callMethod(moment, "getType") as? Int ?: 0
                val createTime = (XposedHelpers.callMethod(moment, "getCreateTime") as? Long) ?: System.currentTimeMillis()
                val iconUrl = try {
                    XposedHelpers.callMethod(moment, "getIconPath") as? String
                } catch (_: Throwable) {
                    null
                }
                val likeCount = (XposedHelpers.callMethod(moment, "getLikeTotal") as? Int) ?: 0
                val commentCount = (XposedHelpers.callMethod(moment, "getCommentsTotalCount") as? Int) ?: 0
                val media = isMediaMoment(moment)
                val text = extractMomentText(moment, media)

                val dir = FavoriteStore.dirOf(momentId)
                dir.mkdirs()
                File(dir, "text.txt").writeText(text)

                val mediaFiles = mutableListOf<String>()
                if (media) {
                    val (urls, isVideo) = extractMediaUrls(moment)
                    urls.forEachIndexed { i, url ->
                        val fileName = if (isVideo) "video_$i.mp4" else "image_$i.jpg"
                        if (downloadToFile(url, File(dir, fileName))) {
                            mediaFiles.add(fileName)
                        }
                    }
                }

                FavoriteStore.add(FavoriteStore.FavoriteMoment(momentId, name, text, type, createTime, likeCount, commentCount, iconUrl, mediaFiles))
                postToast(context, "已收藏")
            } catch (t: Throwable) {
                Logger.e(TAG, ">>> hookFavorite: 收藏失败", t)
                postToast(context, "收藏失败: ${t.message}")
            }
        }.start()
    }

    /** 从 CloudFileResource / SmallPicSouce 等对象安全取 downloadUrl */
    private fun resourceDownloadUrl(resourceObj: Any?): String? {
        if (resourceObj == null) return null
        return try {
            XposedHelpers.callMethod(resourceObj, "getDownloadUrl") as? String
        } catch (_: Throwable) {
            null
        }
    }

    /** 下载 url 到相册(兼容 Android 8.1 与 Android 10+) */
    private fun downloadToGallery(context: Context, urlStr: String, isVideo: Boolean): Boolean {
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.connect()
            if (conn.responseCode != 200) {
                conn.disconnect()
                return false
            }
            val bytes = conn.inputStream.use { it.readBytes() }
            conn.disconnect()
            if (bytes.isEmpty()) return false

            val name = "laoli_${System.currentTimeMillis()}.${if (isVideo) "mp4" else "jpg"}"
            val mime = if (isVideo) "video/mp4" else "image/jpeg"
            val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, if (isVideo) "Movies/Laoli" else "Pictures/Laoli")
                }
                val uri = context.contentResolver.insert(collection, values) ?: return false
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return false
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(
                        if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
                    ),
                    "Laoli"
                )
                dir.mkdirs()
                val file = File(dir, name)
                file.writeBytes(bytes)
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.DATA, file.absolutePath)
                }
                context.contentResolver.insert(collection, values)
            }
            true
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookSaveMedia: 下载失败 url=$urlStr", t)
            false
        }
    }

    /** 主线程弹 Toast */
    private fun postToast(context: Context, msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    /** 启动模块的动态编辑界面(跨进程) */
    private fun launchEditActivity(context: Context, moment: Any) {
        try {
            val momentId = XposedHelpers.callMethod(moment, "getMomentId") as? String ?: return
            val currentContent = XposedHelpers.callMethod(moment, "getContent") as? String ?: ""
            val currentName = (XposedHelpers.callMethod(moment, "getName") as? String) ?: ""
            val existing = EditedMomentStore.get(momentId)
            // 直接读原始 likeTotal 字段,避免被 getLikeTotal hook 覆盖影响初始显示
            val realLikeTotal = (XposedHelpers.getObjectField(moment, "likeTotal") as? java.lang.Integer)?.toInt() ?: 0
            val media = isMediaMoment(moment)

            val intent = android.content.Intent().apply {
                setClassName("com.laoli.hooktools", "com.laoli.hooktools.ui.EditMomentActivity")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("momentId", momentId)
                putExtra("text", existing?.text ?: currentContent)
                putExtra("color", existing?.color ?: Color.parseColor("#CCFFFFFF"))
                putExtra("underline", existing?.underline ?: false)
                putExtra("likeCount", existing?.likeCount ?: realLikeTotal)
                putExtra("name", existing?.name ?: currentName)
                putExtra("isMedia", media)
            }
            context.startActivity(intent)
            Logger.log(TAG, ">>> hookMomentEdit: 已启动编辑界面 momentId=$momentId isMedia=$media")
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookMomentEdit: 启动编辑界面失败", t)
            Toast.makeText(context, "打开编辑界面失败", Toast.LENGTH_SHORT).show()
        }
    }

    /** 应用编辑后的颜色/下划线(通过 ExpandTextView 向上找 AbsMomentView 定位 momentId) */
    private fun applyEditStyle(expandTextView: View?) {
        if (expandTextView == null) return
        try {
            val momentId = findMomentIdFromView(expandTextView) ?: return
            val edited = EditedMomentStore.get(momentId) ?: return
            val tv = XposedHelpers.getObjectField(expandTextView, "tvMoodContent") as? TextView ?: return
            edited.color?.let { tv.setTextColor(it) }
            val flags = tv.paintFlags
            tv.paintFlags = if (edited.underline) {
                flags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
            } else {
                flags and android.graphics.Paint.UNDERLINE_TEXT_FLAG.inv()
            }
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookMomentEdit: 应用颜色/下划线失败", t)
        }
    }

    /** 从 ExpandTextView 向上找到 AbsMomentView,取出其 DbMoment 的 momentId */
    private fun findMomentIdFromView(v: View): String? {
        var cur: View? = v
        while (cur != null) {
            if (isAbsMomentView(cur)) {
                val moment = try {
                    XposedHelpers.callMethod(cur, "getDbMoment")
                } catch (_: Throwable) {
                    null
                }
                if (moment != null) {
                    val id = try {
                        XposedHelpers.callMethod(moment, "getMomentId") as? String
                    } catch (_: Throwable) {
                        null
                    }
                    if (id != null) return id
                }
            }
            cur = cur.parent as? View
        }
        return null
    }

    /** 判断 View 是否为 AbsMomentView(或其子类) */
    private fun isAbsMomentView(v: View): Boolean {
        var c: Class<*>? = v.javaClass
        while (c != null) {
            if (c.name == "com.xtc.moment.module.widget.AbsMomentView") return true
            c = c.superclass
        }
        return false
    }

    /** 刷新主列表,让编辑结果立即生效 */
    private fun refreshMomentList() {
        try {
            val adapter = lastMomentAdapter?.get() ?: return
            XposedHelpers.callMethod(adapter, "notifyDataSetChanged")
            Logger.log(TAG, ">>> hookMomentEdit: 已刷新列表")
        } catch (t: Throwable) {
            Logger.e(TAG, ">>> hookMomentEdit: 刷新列表失败", t)
        }
    }

    /**
     * 写激活回传。
     */
    private fun writeActivationFlag(context: Context) {
        val now = System.currentTimeMillis()
        val version = getModuleVersion(context)
        Logger.log(TAG, ">>> writeActivationFlag: version=$version, time=$now")

        try {
            val sp = context.getSharedPreferences(Constants.PREFS_ACTIVE, Context.MODE_PRIVATE)
            sp.edit()
                .putBoolean(Constants.KEY_ACTIVE_FLAG, true)
                .putLong(Constants.KEY_ACTIVE_TIME, now)
                .putString(Constants.KEY_ACTIVE_VERSION, version)
                .commit()
            Logger.log(TAG, "激活标记写入 SharedPreferences 成功")
        } catch (t: Throwable) {
            Logger.e(TAG, "激活标记写入 SharedPreferences 失败", t)
        }

        try {
            val uri = android.net.Uri.parse("content://com.laoli.hooktools.provider/active")
            val cv = android.content.ContentValues().apply {
                put("active", true)
                put("time", now)
                put("version", version)
            }
            context.contentResolver.insert(uri, cv)
            Logger.log(TAG, "激活标记写入 ContentProvider 成功")
        } catch (t: Throwable) {
            Logger.e(TAG, "激活标记写入 ContentProvider 失败", t)
        }
    }

    private fun getModuleVersion(context: Context): String {
        return try {
            context.packageManager
                .getPackageInfo(Constants.MODULE_PACKAGE, 0)
                .versionName ?: "unknown"
        } catch (t: Throwable) {
            Logger.e(TAG, "获取模块版本失败", t)
            "unknown"
        }
    }

    private fun getCurrentProcessName(context: Context): String {
        return try {
            val pid = android.os.Process.myPid()
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            am.runningAppProcesses?.find { it.pid == pid }?.processName ?: "unknown"
        } catch (t: Throwable) {
            "unknown"
        }
    }

    // ---------- 模块自身进程:自检 ----------

    private fun handleSelf(lpparam: XC_LoadPackage.LoadPackageParam) {
        Logger.log(TAG, ">>> handleSelf 开始")

        try {
            XposedHelpers.findAndHookMethod(
                "com.laoli.hooktools.ui.MainActivity",
                lpparam.classLoader,
                "isModuleActive",
                XC_MethodReplacement.returnConstant(true)
            )
            Logger.log(TAG, "self-hook isModuleActive -> true 成功")
        } catch (t: Throwable) {
            Logger.e(TAG, "self-hook isModuleActive 失败", t)
        }

        try {
            XposedHelpers.findAndHookMethod(
                Application::class.java, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val ctx = param.thisObject as? Application ?: return
                        try {
                            ctx.getSharedPreferences(Constants.PREFS_CONFIG, Context.MODE_PRIVATE)
                                .edit()
                                .putBoolean(ActivationChecker.SELF_HOOKED_FLAG, true)
                                .putLong("self_hooked_time", System.currentTimeMillis())
                                .commit()
                            Logger.log(TAG, "self-hook prefs 标记写入成功")
                        } catch (t: Throwable) {
                            Logger.e(TAG, "self-hook prefs 标记写入失败", t)
                        }
                    }
                }
            )
            Logger.log(TAG, "self-hook Application.onCreate 注册成功")
        } catch (t: Throwable) {
            Logger.e(TAG, "self-hook Application.onCreate 注册失败", t)
        }
    }

    // ---------- 资源替换(zygote 阶段,双保险) ----------

    private fun registerReplacements(res: XResources) {
        Logger.log(TAG, ">>> registerReplacements 开始")
        loadReplacements()

        for ((resName, path) in replacements) {
            try {
                Logger.log(TAG, "setReplacement: $resName <- $path")
                res.setReplacement(
                    Constants.TARGET_PACKAGE, "drawable", resName,
                    object : XResources.DrawableLoader() {
                        override fun newDrawable(targetRes: XResources, id: Int): Drawable {
                            Logger.log(TAG, ">>> [setReplacement/newDrawable] 被调用! resName=$resName id=0x${id.toString(16)} path=$path")
                            return createDrawableFromFile(targetRes, path)
                        }
                    }
                )
                Logger.log(TAG, "setReplacement 注册成功: $resName")
            } catch (t: Throwable) {
                Logger.e(TAG, "setReplacement '$resName' 失败", t)
            }
        }

        // string 资源替换
        for ((resName, value) in stringReplacements) {
            try {
                Logger.log(TAG, "setReplacement(string): $resName <- $value")
                res.setReplacement(Constants.TARGET_PACKAGE, "string", resName, value)
                Logger.log(TAG, "setReplacement(string) 注册成功: $resName")
            } catch (t: Throwable) {
                Logger.e(TAG, "setReplacement(string) '$resName' 失败", t)
            }
        }

        // color 资源替换
        for ((resName, colorInt) in colorReplacements) {
            try {
                Logger.log(TAG, "setReplacement(color): $resName <- 0x${Integer.toHexString(colorInt)}")
                res.setReplacement(Constants.TARGET_PACKAGE, "color", resName, colorInt)
                Logger.log(TAG, "setReplacement(color) 注册成功: $resName")
            } catch (t: Throwable) {
                Logger.e(TAG, "setReplacement(color) '$resName' 失败", t)
            }
        }

        Logger.log(TAG, ">>> registerReplacements 完成")
    }

    /** 从文件路径创建 Drawable(超详细日志) */
    private fun createDrawableFromFile(res: android.content.res.Resources, imagePath: String): Drawable {
        Logger.log(TAG, ">>> createDrawableFromFile 开始: path=$imagePath")
        return try {
            val file = File(imagePath)
            Logger.log(TAG, "  文件路径: ${file.absolutePath}")
            Logger.log(TAG, "  exists = ${file.exists()}")

            if (!file.exists()) {
                Logger.log(TAG, "  >>> 文件不存在!")
                throw java.io.FileNotFoundException(imagePath)
            }

            Logger.log(TAG, "  canRead = ${file.canRead()}")
            Logger.log(TAG, "  length = ${file.length()} bytes")
            Logger.log(TAG, "  isFile = ${file.isFile}")

            if (!file.canRead()) {
                Logger.log(TAG, "  >>> 文件不可读! 尝试用 root 读取...")
                // 尝试读取父目录信息
                val parent = file.parentFile
                Logger.log(TAG, "  父目录: ${parent?.absolutePath}")
                Logger.log(TAG, "  父目录 exists = ${parent?.exists()}")
                Logger.log(TAG, "  父目录 canRead = ${parent?.canRead()}")
                throw java.io.IOException("not readable: $imagePath")
            }

            Logger.log(TAG, "  >>> 开始解码图片...")
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val bmp = BitmapFactory.decodeFile(imagePath, opts)

            if (bmp == null) {
                Logger.log(TAG, "  >>> 解码失败,返回 null!")
                throw IllegalArgumentException("decode failed: $imagePath")
            }

            Logger.log(TAG, "  >>> 解码成功! 尺寸=${bmp.width}x${bmp.height}, config=${bmp.config}")
            BitmapDrawable(res, bmp)
        } catch (t: Throwable) {
            Logger.e(TAG, "  >>> createDrawableFromFile 失败: $imagePath", t)
            // 失败时返回原图,避免崩溃
            Logger.log(TAG, "  >>> 返回原始资源兜底")
            BitmapDrawable(
                res,
                BitmapFactory.decodeResource(res, android.R.drawable.screen_background_light)
            )
        }
    }
}
