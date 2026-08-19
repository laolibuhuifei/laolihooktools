package com.laoli.hooktools.ui

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.laoli.hooktools.BuildConfig
import com.laoli.hooktools.R
import com.laoli.hooktools.util.ActivationChecker
import com.laoli.hooktools.util.RootUtil
import com.laoli.hooktools.util.UpdateManager
import com.laoli.hooktools.util.addPressScale
import java.io.File

class MainActivity : AppCompatActivity() {

    // 状态视图
    private lateinit var statusDot: View
    private lateinit var tvStatus: TextView
    private lateinit var tvLastActive: TextView
    private lateinit var tvTarget: TextView
    private lateinit var tvVersion: TextView
    private lateinit var tvRootStatus: TextView
    private lateinit var tvStatusSummary: TextView
    private lateinit var tvExpandIndicator: TextView
    private lateinit var statusHeader: View
    private lateinit var detailContainer: View

    // 详情是否展开
    private var detailExpanded = false

    // 是否已检查过强制更新(避免 onResume 重复弹窗)
    private var forceUpdateChecked = false

    // 操作
    private lateinit var btnFriendCircleSettings: MaterialButton
    private lateinit var btnSportSettings: MaterialButton
    private lateinit var btnPersonalCenterSettings: MaterialButton
    private lateinit var btnApps: MaterialButton
    private lateinit var btnFontCenter: MaterialButton
    private lateinit var btnModuleFont: MaterialButton
    private lateinit var btnAnnouncement: MaterialButton
    private lateinit var btnCheckUpdate: MaterialButton

    /**
     * 模块激活自检方法。
     *
     * 默认返回 false。当模块被 Xposed 框架正确激活并 hook 自身时,
     * [com.laoli.hooktools.hook.MomentHook] 会用 XC_MethodReplacement.returnConstant(true)
     * 替换此方法,使其返回 true。
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun isModuleActive(): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        // 每次回到前台重新检测激活状态
        refreshActivationStatus()
        // 检测 root 状态
        refreshRootStatus()
        // 首次进入时检查强制更新
        if (!forceUpdateChecked) {
            forceUpdateChecked = true
            checkForceUpdate()
        }
    }

    private fun refreshRootStatus() {
        tvRootStatus.text = getString(R.string.root_status_checking)
        tvRootStatus.setTextColor(getColor(R.color.md_on_surface_variant))
        Thread {
            val granted = RootUtil.hasRootAccess()
            runOnUiThread {
                if (granted) {
                    tvRootStatus.text = getString(R.string.root_status_granted)
                    tvRootStatus.setTextColor(getColor(R.color.md_success))
                } else {
                    tvRootStatus.text = getString(R.string.root_status_denied)
                    tvRootStatus.setTextColor(getColor(R.color.md_error))
                }
            }
        }.start()
    }

    private fun bindViews() {
        statusDot = findViewById(R.id.statusDot)
        tvStatus = findViewById(R.id.tvStatus)
        tvLastActive = findViewById(R.id.tvLastActive)
        tvTarget = findViewById(R.id.tvTarget)
        tvVersion = findViewById(R.id.tvVersion)
        tvRootStatus = findViewById(R.id.tvRootStatus)
        tvStatusSummary = findViewById(R.id.tvStatusSummary)
        tvExpandIndicator = findViewById(R.id.tvExpandIndicator)
        statusHeader = findViewById(R.id.statusHeader)
        detailContainer = findViewById(R.id.detailContainer)
        btnFriendCircleSettings = findViewById(R.id.btnFriendCircleSettings)
        btnSportSettings = findViewById(R.id.btnSportSettings)
        btnPersonalCenterSettings = findViewById(R.id.btnPersonalCenterSettings)
        btnApps = findViewById(R.id.btnApps)
        btnFontCenter = findViewById(R.id.btnFontCenter)
        btnModuleFont = findViewById(R.id.btnModuleFont)
        btnAnnouncement = findViewById(R.id.btnAnnouncement)
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate)
        tvVersion.text = getString(R.string.module_version, BuildConfig.VERSION_NAME)

        findViewById<TextView>(R.id.tvGithubLink).setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/laolibuhuifei/laolihooktools")))
            } catch (_: Throwable) {
            }
        }
    }

    private fun setupListeners() {
        btnFriendCircleSettings.addPressScale()
        btnSportSettings.addPressScale()
        btnPersonalCenterSettings.addPressScale()
        btnApps.addPressScale()
        btnFontCenter.addPressScale()
        btnModuleFont.addPressScale()
        btnAnnouncement.addPressScale()
        btnCheckUpdate.addPressScale()
        statusHeader.addPressScale(0.98f)

        btnFriendCircleSettings.setOnClickListener {
            startActivity(Intent(this, FriendCircleSettingsActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        btnSportSettings.setOnClickListener {
            startActivity(Intent(this, SportSettingsActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        btnPersonalCenterSettings.setOnClickListener {
            startActivity(Intent(this, PersonalCenterSettingsActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        btnApps.setOnClickListener {
            startActivity(Intent(this, DownloadAppsActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        btnFontCenter.setOnClickListener {
            startActivity(Intent(this, FontCenterActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        btnModuleFont.setOnClickListener {
            startActivity(Intent(this, ModuleFontActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        btnAnnouncement.setOnClickListener {
            startActivity(Intent(this, AnnouncementActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        btnCheckUpdate.setOnClickListener {
            checkUpdateAndPrompt()
        }
        statusHeader.setOnClickListener {
            toggleDetail()
        }
    }

    /** 展开/折叠激活详情(带动画) */
    private fun toggleDetail() {
        detailExpanded = !detailExpanded
        val root = detailContainer.parent as? ViewGroup
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && root != null) {
            TransitionManager.beginDelayedTransition(root, AutoTransition())
        }
        detailContainer.visibility = if (detailExpanded) View.VISIBLE else View.GONE
        tvExpandIndicator.text = if (detailExpanded) "▴" else "▾"
    }

    // ---------- 更新检查与下载 ----------

    /** 进入 APP 时检查强制更新 */
    private fun checkForceUpdate() {
        Thread {
            val info = UpdateManager.checkUpdate()
            runOnUiThread {
                if (info != null && info.hasUpdate && info.forceUpdate && !info.apkUrl.isNullOrEmpty()) {
                    showUpdateDialog(info, forced = true)
                }
            }
        }.start()
    }

    /** 点击"检测更新"按钮 */
    private fun checkUpdateAndPrompt() {
        Toast.makeText(this, R.string.update_checking, Toast.LENGTH_SHORT).show()
        Thread {
            val info = UpdateManager.checkUpdate()
            runOnUiThread {
                when {
                    info == null ->
                        Toast.makeText(this, R.string.update_check_failed, Toast.LENGTH_SHORT).show()
                    !info.hasUpdate ->
                        Toast.makeText(this, R.string.update_no_update, Toast.LENGTH_SHORT).show()
                    info.apkUrl.isNullOrEmpty() ->
                        Toast.makeText(this, R.string.update_no_update, Toast.LENGTH_SHORT).show()
                    else -> showUpdateDialog(info, forced = false)
                }
            }
        }.start()
    }

    /** 显示更新对话框(含下载进度) */
    private fun showUpdateDialog(info: UpdateManager.UpdateInfo, forced: Boolean) {
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_update, null)
        dialog.setContentView(view)
        dialog.setCancelable(!forced)
        dialog.setCanceledOnTouchOutside(false)

        val tvTitle = view.findViewById<TextView>(R.id.tvUpdateTitle)
        val tvVersion = view.findViewById<TextView>(R.id.tvUpdateVersion)
        val tvLog = view.findViewById<TextView>(R.id.tvUpdateLog)
        val pb = view.findViewById<ProgressBar>(R.id.pbDownload)
        val tvProgress = view.findViewById<TextView>(R.id.tvProgress)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btnCancel)
        val btnAction = view.findViewById<MaterialButton>(R.id.btnAction)

        tvTitle.text = if (forced) {
            getString(R.string.update_force_title)
        } else {
            getString(R.string.update_new_version)
        }
        tvVersion.text = getString(R.string.update_version, info.versionName)
        tvLog.text = info.updateLog

        if (forced) {
            btnCancel.visibility = View.GONE
        }

        var downloadedFile: File? = null

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnAction.setOnClickListener {
            val apk = downloadedFile
            if (apk != null) {
                // 已下载完成,执行安装
                dialog.dismiss()
                doInstall(apk)
                return@setOnClickListener
            }

            // 开始下载
            val apkFile = UpdateManager.apkFile(this)
            btnAction.isEnabled = false
            btnCancel.isEnabled = false
            pb.visibility = View.VISIBLE
            tvProgress.visibility = View.VISIBLE
            tvProgress.text = "0%"

            Thread {
                val ok = UpdateManager.downloadApk(info.apkUrl!!, apkFile) { downloaded, total ->
                    val percent = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                    runOnUiThread {
                        pb.progress = percent
                        tvProgress.text = "$percent%"
                    }
                }
                runOnUiThread {
                    if (ok) {
                        downloadedFile = apkFile
                        tvProgress.text = getString(R.string.update_download_done)
                        btnAction.text = getString(R.string.update_install_btn)
                        btnAction.isEnabled = true
                        if (forced) {
                            // 强制更新:下载完成后自动安装
                            dialog.dismiss()
                            doInstall(apkFile)
                        }
                    } else {
                        tvProgress.text = getString(R.string.update_download_failed)
                        btnAction.isEnabled = true
                        btnCancel.isEnabled = true
                    }
                }
            }.start()
        }

        dialog.show()
        // 全屏显示弹窗
        dialog.window?.apply {
            setLayout(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.MATCH_PARENT
            )
            setBackgroundDrawableResource(R.color.md_background)
        }
    }

    /** 执行 APK 安装(调起系统安装器) */
    private fun doInstall(apkFile: File) {
        val launched = UpdateManager.installApk(this, apkFile.absolutePath)
        if (!launched) {
            Toast.makeText(this, R.string.update_install_failed, Toast.LENGTH_LONG).show()
        }
    }

    // ---------- 激活检测 ----------

    private fun refreshActivationStatus() {
        statusDot.setBackgroundResource(R.drawable.dot_checking)
        tvStatus.text = getString(R.string.status_checking)

        // 直接在主线程调用 isModuleActive()(方法被 hook 后是 returnConstant,不阻塞)
        // PackageManager 查询也很快,无需后台线程
        val result = ActivationChecker.check(this)
        renderActivationStatus(result)
    }

    private fun renderActivationStatus(result: ActivationChecker.Result) {
        when (result.state) {
            ActivationChecker.State.NO_FRAMEWORK -> {
                statusDot.setBackgroundResource(R.drawable.dot_inactive)
                tvStatus.text = getString(R.string.status_no_framework)
                tvLastActive.text = getString(R.string.hint_no_framework)
                tvStatusSummary.text = getString(R.string.status_summary_inactive)
            }
            ActivationChecker.State.FRAMEWORK_ONLY -> {
                statusDot.setBackgroundResource(R.drawable.dot_inactive)
                tvStatus.text = getString(R.string.status_framework_only)
                tvLastActive.text = getString(R.string.hint_framework_only)
                tvStatusSummary.text = getString(R.string.status_summary_inactive)
            }
            ActivationChecker.State.MODULE_LOADED_NOT_TARGET -> {
                statusDot.setBackgroundResource(R.drawable.dot_checking)
                tvStatus.text = getString(R.string.status_module_loaded)
                tvLastActive.text = getString(R.string.hint_module_loaded)
                tvStatusSummary.text = getString(R.string.status_summary_inactive)
            }
            ActivationChecker.State.ACTIVE -> {
                statusDot.setBackgroundResource(R.drawable.dot_active)
                tvStatus.text = getString(R.string.status_active)
                val timeStr = ActivationChecker.formatTime(result.lastActiveTime)
                tvLastActive.text = if (timeStr.isNotEmpty()) {
                    getString(R.string.last_active_time, timeStr)
                } else {
                    getString(R.string.hint_active)
                }
                tvStatusSummary.text = getString(R.string.status_summary_active)
            }
        }
    }
}
