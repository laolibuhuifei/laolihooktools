package com.laoli.hooktools.ui

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.materialswitch.MaterialSwitch
import com.laoli.hooktools.BuildConfig
import com.laoli.hooktools.R
import com.laoli.hooktools.prefs.PrefsManager
import com.laoli.hooktools.util.ActivationChecker
import com.laoli.hooktools.util.Constants
import com.laoli.hooktools.util.EditedCommentStore
import com.laoli.hooktools.util.EditedMomentStore
import com.laoli.hooktools.util.ImageManager
import com.laoli.hooktools.util.RootUtil
import com.laoli.hooktools.util.UpdateManager
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager

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
    private lateinit var btnRestart: MaterialButton
    private lateinit var btnViewLog: MaterialButton
    private lateinit var btnColorSettings: MaterialButton
    private lateinit var btnAnnouncement: MaterialButton
    private lateinit var btnCheckUpdate: MaterialButton
    private lateinit var btnRestoreEdit: MaterialButton

    // 文字替换
    private lateinit var switchString: MaterialSwitch
    private lateinit var tvStringState: TextView
    private lateinit var etStringValue: EditText
    private lateinit var btnSaveString: MaterialButton

    // 时间详细显示
    private lateinit var switchTimeDetail: MaterialSwitch

    // 防删除
    private lateinit var switchAntiDelete: MaterialSwitch

    // 链接自动跳转
    private lateinit var switchLinkJump: MaterialSwitch

    // 待选择图片的目标(用于回调)
    private var pendingTarget: Constants.TargetResource? = null

    /**
     * 模块激活自检方法。
     *
     * 默认返回 false。当模块被 Xposed 框架正确激活并 hook 自身时,
     * [com.laoli.hooktools.hook.MomentHook] 会用 XC_MethodReplacement.returnConstant(true)
     * 替换此方法,使其返回 true。
     *
     * 这是验证模块是否被框架加载的最可靠方式:
     * - 不依赖跨进程通信
     * - 不依赖文件权限
     * - 不依赖反射类加载
     * - 直接验证 hook 机制是否生效
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun isModuleActive(): Boolean = false

    /** 图片选择回调 */
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val target = pendingTarget
        pendingTarget = null
        if (result.resultCode != Activity.RESULT_OK || target == null) return@registerForActivityResult
        val data = result.data
        // 小天才相册返回文件路径(MediaStore.EXTRA_OUTPUT)
        val photoPath = data?.extras?.getString(MediaStore.EXTRA_OUTPUT, null)
        if (photoPath != null) {
            handlePickedImagePath(target, photoPath)
            return@registerForActivityResult
        }
        // 兜底:系统相册返回 Uri
        val uri: Uri = data?.data ?: return@registerForActivityResult
        handlePickedImage(target, uri)
    }

    /** 卡片视图持有 */
    private data class CardHolder(
        val target: Constants.TargetResource,
        val root: View,
        val tvTitle: TextView,
        val tvDesc: TextView,
        val tvPath: TextView,
        val switchEnable: MaterialSwitch,
        val ivPreview: ShapeableImageView,
        val tvImageState: TextView,
        val btnSelect: MaterialButton,
        val btnClear: MaterialButton
    )

    private val cardHolders = mutableListOf<CardHolder>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = PrefsManager.get(this)

        bindViews()
        setupCards()
        setupStringCard()
        setupTimeDetailCard()
        setupAntiDeleteCard()
        setupLinkJumpCard()
        setupListeners()
        refreshAll()
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
        btnRestart = findViewById(R.id.btnRestart)
        btnViewLog = findViewById(R.id.btnViewLog)
        btnColorSettings = findViewById(R.id.btnColorSettings)
        btnAnnouncement = findViewById(R.id.btnAnnouncement)
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate)
        btnRestoreEdit = findViewById(R.id.btnRestoreEdit)
        switchString = findViewById(R.id.switchString)
        tvStringState = findViewById(R.id.tvStringState)
        etStringValue = findViewById(R.id.etStringValue)
        btnSaveString = findViewById(R.id.btnSaveString)
        switchTimeDetail = findViewById(R.id.switchTimeDetail)
        switchAntiDelete = findViewById(R.id.switchAntiDelete)
        switchLinkJump = findViewById(R.id.switchLinkJump)
        tvVersion.text = getString(R.string.module_version, BuildConfig.VERSION_NAME)

        findViewById<TextView>(R.id.tvGithubLink).setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/laolibuhuifei/laolihooktools")))
            } catch (_: Throwable) {
            }
        }
    }

    private fun setupCards() {
        // 好友圈背景卡
        bindCard(
            target = Constants.TargetResource.BG_HEAD_VIEW,
            cardRoot = findViewById(R.id.cardHeadBg),
            title = getString(R.string.feature_head_bg),
            desc = getString(R.string.feature_head_bg_desc)
        )
        // 消息背景卡
        bindCard(
            target = Constants.TargetResource.BG_NEW_MESSAGE,
            cardRoot = findViewById(R.id.cardMsgBg),
            title = getString(R.string.feature_msg_bg),
            desc = getString(R.string.feature_msg_bg_desc)
        )
    }

    private fun bindCard(
        target: Constants.TargetResource,
        cardRoot: View,
        title: String,
        desc: String
    ) {
        val holder = CardHolder(
            target = target,
            root = cardRoot,
            tvTitle = cardRoot.findViewById(R.id.tvTitle),
            tvDesc = cardRoot.findViewById(R.id.tvDesc),
            tvPath = cardRoot.findViewById(R.id.tvPath),
            switchEnable = cardRoot.findViewById(R.id.switchEnable),
            ivPreview = cardRoot.findViewById(R.id.ivPreview),
            tvImageState = cardRoot.findViewById(R.id.tvImageState),
            btnSelect = cardRoot.findViewById(R.id.btnSelect),
            btnClear = cardRoot.findViewById(R.id.btnClear)
        )
        holder.tvTitle.text = title
        holder.tvDesc.text = desc
        holder.tvPath.text = Constants.TargetResource.originalPath(target)

        holder.switchEnable.setOnCheckedChangeListener { _, isChecked ->
            prefs.setEnabled(target, isChecked)
            updateCardEnabledVisual(holder, isChecked)
        }

        holder.btnSelect.setOnClickListener {
            pendingTarget = target
            launchImagePicker()
        }
        holder.btnClear.setOnClickListener {
            clearImage(target)
        }

        cardHolders.add(holder)
    }

    private fun setupListeners() {
        btnRestart.setOnClickListener {
            forceRestartTarget()
        }
        btnViewLog.setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }
        btnColorSettings.setOnClickListener {
            startActivity(Intent(this, ColorActivity::class.java))
        }
        btnAnnouncement.setOnClickListener {
            startActivity(Intent(this, AnnouncementActivity::class.java))
        }
        btnCheckUpdate.setOnClickListener {
            checkUpdateAndPrompt()
        }
        btnRestoreEdit.setOnClickListener {
            confirmRestoreAllEdit()
        }
        statusHeader.setOnClickListener {
            toggleDetail()
        }
    }

    /** 一键还原所有编辑的动态(带确认) */
    private fun confirmRestoreAllEdit() {
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.restore_all_edit)
            .setMessage(R.string.restore_all_edit_confirm)
            .setPositiveButton(R.string.confirm) { _, _ ->
                EditedMomentStore.clear()
                EditedCommentStore.clear()
                Toast.makeText(this, R.string.restore_all_edit_done, Toast.LENGTH_LONG).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** 展开/折叠激活详情 */
    private fun toggleDetail() {
        detailExpanded = !detailExpanded
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

    // ---------- 文字替换 ----------

    private fun setupStringCard() {
        switchString.setOnCheckedChangeListener { _, isChecked ->
            prefs.setStringEnabled(Constants.TargetString.APP_NAME, isChecked)
            updateStringStateVisual()
        }
        btnSaveString.setOnClickListener {
            val value = etStringValue.text?.toString()?.trim().orEmpty()
            prefs.setStringValue(Constants.TargetString.APP_NAME, value)
            // 输入了文字但开关未开启时,自动开启
            if (value.isNotEmpty() && !prefs.isStringEnabled(Constants.TargetString.APP_NAME)) {
                prefs.setStringEnabled(Constants.TargetString.APP_NAME, true)
                switchString.isChecked = true
            }
            refreshStringCard()
            Toast.makeText(this, R.string.toast_saved_success, Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshStringCard() {
        val enabled = prefs.isStringEnabled(Constants.TargetString.APP_NAME)
        val value = prefs.getStringValue(Constants.TargetString.APP_NAME)
        switchString.isChecked = enabled
        etStringValue.setText(value ?: "")
        updateStringStateVisual()
    }

    private fun updateStringStateVisual() {
        val value = prefs.getStringValue(Constants.TargetString.APP_NAME)
        if (value.isNullOrEmpty()) {
            tvStringState.text = getString(R.string.string_not_set)
            tvStringState.setTextColor(getColor(R.color.md_on_surface_variant))
        } else {
            tvStringState.text = getString(R.string.string_set, value)
            tvStringState.setTextColor(getColor(R.color.md_success))
        }
    }

    // ---------- 时间详细显示 ----------

    private fun setupTimeDetailCard() {
        switchTimeDetail.setOnCheckedChangeListener { _, isChecked ->
            prefs.setTimeDetailEnabled(isChecked)
        }
    }

    private fun refreshTimeDetailCard() {
        switchTimeDetail.isChecked = prefs.isTimeDetailEnabled()
    }

    // ---------- 防删除(只防同步删除) ----------

    private fun setupAntiDeleteCard() {
        switchAntiDelete.setOnCheckedChangeListener { _, isChecked ->
            prefs.setAntiDeleteEnabled(isChecked)
        }
    }

    private fun refreshAntiDeleteCard() {
        switchAntiDelete.isChecked = prefs.isAntiDeleteEnabled()
    }

    // ---------- 链接自动跳转 ----------

    private fun setupLinkJumpCard() {
        switchLinkJump.setOnCheckedChangeListener { _, isChecked ->
            prefs.setLinkJumpEnabled(isChecked)
        }
    }

    private fun refreshLinkJumpCard() {
        switchLinkJump.isChecked = prefs.isLinkJumpEnabled()
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

    // ---------- 图片选择 ----------

    private fun launchImagePicker() {
        // 小天才相册:ACTION_GET_CONTENT + 左右按钮文本
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            putExtra("com.xtc.camera.LEFT_BUTTON_TEXT", getString(R.string.cancel))
            putExtra("com.xtc.camera.RIGHT_BUTTON_TEXT", getString(R.string.confirm))
        }
        try {
            pickImageLauncher.launch(intent)
        } catch (t: Throwable) {
            // 兜底:打开系统图库
            try {
                val fallback = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                pickImageLauncher.launch(Intent.createChooser(fallback, getString(R.string.select_image)))
            } catch (_: Throwable) {
                Toast.makeText(this, R.string.toast_no_permission, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handlePickedImage(target: Constants.TargetResource, uri: Uri) {
        Thread {
            val path = ImageManager.importUri(this, uri, target.resName)
            runOnUiThread {
                if (path == null) {
                    Toast.makeText(this, R.string.toast_image_too_large, Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                prefs.setPath(target, path)
                // 若用户未启用,自动开启
                if (!prefs.isEnabled(target)) {
                    prefs.setEnabled(target, true)
                    cardHolders.find { it.target == target }?.switchEnable?.isChecked = true
                }
                refreshCard(target)
                Toast.makeText(this, R.string.toast_saved_success, Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun handlePickedImagePath(target: Constants.TargetResource, sourcePath: String) {
        Thread {
            val path = ImageManager.importPath(this, sourcePath, target.resName)
            runOnUiThread {
                if (path == null) {
                    Toast.makeText(this, R.string.toast_image_too_large, Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                prefs.setPath(target, path)
                // 若用户未启用,自动开启
                if (!prefs.isEnabled(target)) {
                    prefs.setEnabled(target, true)
                    cardHolders.find { it.target == target }?.switchEnable?.isChecked = true
                }
                refreshCard(target)
                Toast.makeText(this, R.string.toast_saved_success, Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun clearImage(target: Constants.TargetResource) {
        ImageManager.clear(this, target.resName)
        prefs.setPath(target, null)
        refreshCard(target)
    }

    // ---------- 刷新 ----------

    private fun refreshAll() {
        refreshActivationStatus()
        cardHolders.forEach { refreshCard(it.target) }
        refreshStringCard()
        refreshTimeDetailCard()
        refreshAntiDeleteCard()
        refreshLinkJumpCard()
    }

    private fun refreshCard(target: Constants.TargetResource) {
        val holder = cardHolders.find { it.target == target } ?: return
        val enabled = prefs.isEnabled(target)
        val path = prefs.getPath(target)
        holder.switchEnable.isChecked = enabled
        updateCardEnabledVisual(holder, enabled)

        if (!path.isNullOrEmpty() && File(path).exists()) {
            holder.tvImageState.text = getString(R.string.image_set)
            holder.tvImageState.setTextColor(getColor(R.color.md_success))
            // 加载预览
            try {
                val bmp = android.graphics.BitmapFactory.decodeFile(path)
                holder.ivPreview.setImageBitmap(bmp)
            } catch (_: Throwable) {
                holder.ivPreview.setImageResource(R.drawable.ic_image)
            }
        } else {
            holder.tvImageState.text = getString(R.string.image_not_set)
            holder.tvImageState.setTextColor(getColor(R.color.md_on_surface_variant))
            holder.ivPreview.setImageResource(R.drawable.ic_image)
        }
    }

    private fun updateCardEnabledVisual(holder: CardHolder, enabled: Boolean) {
        holder.btnSelect.isEnabled = true
        holder.btnClear.isEnabled = enabled
        holder.ivPreview.alpha = if (enabled) 1f else 0.5f
    }

    // ---------- 重启好友圈 ----------

    private fun forceRestartTarget() {
        // 后台线程执行 su(避免阻塞 UI + 等待授权弹窗)
        Thread {
            // 1. 检查 root
            if (!RootUtil.hasRootAccess()) {
                runOnUiThread {
                    Toast.makeText(this, R.string.toast_no_root, Toast.LENGTH_LONG).show()
                }
                return@Thread
            }

            // 2. 强制停止好友圈(am force-stop 需要 FORCE_STOP_PACKAGE 权限,只有 root 或系统应用可用)
            val stopCode = RootUtil.execRoot("am force-stop ${Constants.TARGET_PACKAGE}")

            // 3. 等待进程完全退出
            try {
                Thread.sleep(500)
            } catch (_: InterruptedException) {
            }

            // 4. 重新启动好友圈(用 monkey 启动 launcher Activity,兼容性好)
            val startCode = RootUtil.execRoot(
                "monkey -p ${Constants.TARGET_PACKAGE} -c android.intent.category.LAUNCHER 1"
            )

            runOnUiThread {
                if (stopCode == 0 && startCode == 0) {
                    Toast.makeText(this, R.string.toast_restarted, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, R.string.toast_restart_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}
