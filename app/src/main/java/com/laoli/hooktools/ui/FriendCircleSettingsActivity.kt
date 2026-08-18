package com.laoli.hooktools.ui

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.materialswitch.MaterialSwitch
import com.laoli.hooktools.R
import com.laoli.hooktools.prefs.PrefsManager
import com.laoli.hooktools.util.Constants
import com.laoli.hooktools.util.EditedCommentStore
import com.laoli.hooktools.util.EditedMomentStore
import com.laoli.hooktools.util.FontStore
import com.laoli.hooktools.util.ImageManager
import com.laoli.hooktools.util.RootUtil
import com.laoli.hooktools.util.addPressScale
import java.io.File

/**
 * 好友圈设置页:集中所有好友圈相关功能。
 *
 * - 资源替换(背景/消息背景)
 * - 标题文字替换
 * - 时间详细显示 / 防删除 / 链接自动跳转
 * - 主题色 / 收藏夹 / 还原编辑 / 查看日志 / 重启好友圈
 */
class FriendCircleSettingsActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager

    // 操作
    private lateinit var btnBack: View
    private lateinit var btnViewLog: MaterialButton
    private lateinit var btnColorSettings: MaterialButton
    private lateinit var btnRestoreEdit: MaterialButton
    private lateinit var btnFavorites: MaterialButton
    private lateinit var btnRestart: MaterialButton

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

    // 自定义字体
    private lateinit var switchFont: MaterialSwitch
    private lateinit var tvFontState: TextView
    private lateinit var btnSelectFont: MaterialButton
    private lateinit var btnSelectDownloadedFont: MaterialButton
    private lateinit var btnClearFont: MaterialButton

    // 待选择图片的目标(用于回调)
    private var pendingTarget: Constants.TargetResource? = null

    /** 字体文件选择回调 */
    private val pickFontLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val data = result.data
        // 小天才文件选择可能返回文件路径(MediaStore.EXTRA_OUTPUT)
        val fontPath = data?.extras?.getString(MediaStore.EXTRA_OUTPUT, null)
        if (fontPath != null) {
            handlePickedFontPath(fontPath)
            return@registerForActivityResult
        }
        val uri: Uri = data?.data ?: return@registerForActivityResult
        handlePickedFont(uri)
    }

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
        setContentView(R.layout.activity_friend_circle_settings)

        prefs = PrefsManager.get(this)

        bindViews()
        setupCards()
        setupStringCard()
        setupTimeDetailCard()
        setupAntiDeleteCard()
        setupLinkJumpCard()
        setupFontCard()
        setupListeners()
        refreshAll()
    }

    private fun bindViews() {
        btnBack = findViewById(R.id.btnBack)
        btnViewLog = findViewById(R.id.btnViewLog)
        btnColorSettings = findViewById(R.id.btnColorSettings)
        btnRestoreEdit = findViewById(R.id.btnRestoreEdit)
        btnFavorites = findViewById(R.id.btnFavorites)
        btnRestart = findViewById(R.id.btnRestart)
        switchString = findViewById(R.id.switchString)
        tvStringState = findViewById(R.id.tvStringState)
        etStringValue = findViewById(R.id.etStringValue)
        btnSaveString = findViewById(R.id.btnSaveString)
        switchTimeDetail = findViewById(R.id.switchTimeDetail)
        switchAntiDelete = findViewById(R.id.switchAntiDelete)
        switchLinkJump = findViewById(R.id.switchLinkJump)
        switchFont = findViewById(R.id.switchFont)
        tvFontState = findViewById(R.id.tvFontState)
        btnSelectFont = findViewById(R.id.btnSelectFont)
        btnSelectDownloadedFont = findViewById(R.id.btnSelectDownloadedFont)
        btnClearFont = findViewById(R.id.btnClearFont)
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
        btnBack.addPressScale()
        btnViewLog.addPressScale()
        btnColorSettings.addPressScale()
        btnRestoreEdit.addPressScale()
        btnFavorites.addPressScale()
        btnRestart.addPressScale()
        btnSaveString.addPressScale()
        btnSelectFont.addPressScale()
        btnSelectDownloadedFont.addPressScale()
        btnClearFont.addPressScale()

        btnBack.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        btnViewLog.setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        btnColorSettings.setOnClickListener {
            startActivity(Intent(this, ColorActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        btnRestoreEdit.setOnClickListener {
            confirmRestoreAllEdit()
        }
        btnFavorites.setOnClickListener {
            startActivity(Intent(this, FavoriteActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        btnRestart.setOnClickListener {
            forceRestartTarget()
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

    // ---------- 自定义字体 ----------

    private fun setupFontCard() {
        switchFont.setOnCheckedChangeListener { _, isChecked ->
            prefs.setFontEnabled(isChecked)
            refreshFontCard()
        }
        btnSelectFont.setOnClickListener {
            launchFontPicker()
        }
        btnSelectDownloadedFont.setOnClickListener {
            showDownloadedFontPicker()
        }
        btnClearFont.setOnClickListener {
            clearFont()
        }
    }

    private fun refreshFontCard() {
        val enabled = prefs.isFontEnabled()
        val path = prefs.getFontPath()
        switchFont.isChecked = enabled
        updateFontVisual(path)
    }

    private fun updateFontVisual(path: String?) {
        val exists = !path.isNullOrEmpty() && File(path).exists()
        if (exists) {
            tvFontState.text = getString(R.string.font_set)
            tvFontState.setTextColor(getColor(R.color.md_success))
        } else {
            tvFontState.text = getString(R.string.font_not_set)
            tvFontState.setTextColor(getColor(R.color.md_on_surface_variant))
        }
        btnClearFont.isEnabled = exists
    }

    private fun launchFontPicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra("com.xtc.camera.LEFT_BUTTON_TEXT", getString(R.string.cancel))
            putExtra("com.xtc.camera.RIGHT_BUTTON_TEXT", getString(R.string.confirm))
        }
        try {
            pickFontLauncher.launch(intent)
        } catch (t: Throwable) {
            try {
                pickFontLauncher.launch(Intent.createChooser(intent, getString(R.string.select_font)))
            } catch (_: Throwable) {
                Toast.makeText(this, R.string.toast_no_permission, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handlePickedFont(uri: Uri) {
        Thread {
            val name = queryDisplayName(uri)
            if (!isValidFontName(name)) {
                runOnUiThread {
                    Toast.makeText(this, R.string.toast_font_invalid, Toast.LENGTH_SHORT).show()
                }
                return@Thread
            }
            val path = copyFontUri(uri)
            runOnUiThread { onFontCopied(path) }
        }.start()
    }

    private fun handlePickedFontPath(sourcePath: String) {
        Thread {
            val src = File(sourcePath)
            if (!src.exists() || !isValidFontName(src.name)) {
                runOnUiThread {
                    Toast.makeText(this, R.string.toast_font_invalid, Toast.LENGTH_SHORT).show()
                }
                return@Thread
            }
            if (src.length() > Constants.FONT_MAX_BYTES) {
                runOnUiThread {
                    Toast.makeText(this, R.string.toast_font_invalid, Toast.LENGTH_SHORT).show()
                }
                return@Thread
            }
            val path = try {
                val target = fontTargetFile()
                src.copyTo(target, overwrite = true)
                target.setReadable(true, false)
                try { RootUtil.execRoot("chmod 644 ${target.absolutePath}") } catch (_: Throwable) {}
                target.absolutePath
            } catch (t: Throwable) {
                null
            }
            runOnUiThread { onFontCopied(path) }
        }.start()
    }

    private fun copyFontUri(uri: Uri): String? {
        return try {
            val target = fontTargetFile()
            contentResolver.openInputStream(uri)?.use { input ->
                java.io.FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            if (target.length() > Constants.FONT_MAX_BYTES) {
                target.delete()
                return null
            }
            target.setReadable(true, false)
            try { RootUtil.execRoot("chmod 644 ${target.absolutePath}") } catch (_: Throwable) {}
            target.absolutePath
        } catch (t: Throwable) {
            null
        }
    }

    private fun fontTargetFile(): File {
        val dir = File(android.os.Environment.getExternalStorageDirectory(), "laoli_hooktools")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, Constants.FONT_FILE_NAME)
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        } catch (t: Throwable) {
            null
        }
    }

    private fun isValidFontName(name: String?): Boolean {
        val n = name?.lowercase() ?: return false
        return n.endsWith(".ttf") || n.endsWith(".otf")
    }

    private fun onFontCopied(path: String?) {
        if (path == null) {
            Toast.makeText(this, R.string.toast_save_failed, Toast.LENGTH_SHORT).show()
            return
        }
        prefs.setFontPath(path)
        // 若未启用,自动开启
        if (!prefs.isFontEnabled()) {
            prefs.setFontEnabled(true)
        }
        refreshFontCard()
        Toast.makeText(this, R.string.toast_saved_success, Toast.LENGTH_SHORT).show()
    }

    private fun clearFont() {
        try {
            val path = prefs.getFontPath()
            if (!path.isNullOrEmpty()) File(path).delete()
        } catch (_: Throwable) {
        }
        prefs.setFontPath(null)
        prefs.setFontEnabled(false)
        refreshFontCard()
    }

    /** 弹出已下载字体列表供选择(全屏) */
    private fun showDownloadedFontPicker() {
        val fonts = FontStore.listFonts()
        if (fonts.isEmpty()) {
            Toast.makeText(this, R.string.font_no_downloaded, Toast.LENGTH_LONG).show()
            return
        }

        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_font_picker, null)
        dialog.setContentView(view)

        val container = view.findViewById<LinearLayout>(R.id.fontListContainer)
        val density = resources.displayMetrics.density

        for (file in fonts) {
            val tv = TextView(this).apply {
                text = file.name
                textSize = 14f
                setTextColor(getColor(R.color.md_on_surface))
                setPadding(
                    (10 * density).toInt(),
                    (13 * density).toInt(),
                    (10 * density).toInt(),
                    (13 * density).toInt()
                )
                setOnClickListener {
                    prefs.setFontPath(file.absolutePath)
                    if (!prefs.isFontEnabled()) {
                        prefs.setFontEnabled(true)
                    }
                    refreshFontCard()
                    Toast.makeText(this@FriendCircleSettingsActivity, R.string.toast_saved_success, Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
            container.addView(tv)
        }

        view.findViewById<TextView>(R.id.btnFontPickerClose).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.apply {
            setLayout(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.MATCH_PARENT
            )
            setBackgroundDrawableResource(R.color.md_background)
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
        cardHolders.forEach { refreshCard(it.target) }
        refreshStringCard()
        refreshTimeDetailCard()
        refreshAntiDeleteCard()
        refreshLinkJumpCard()
        refreshFontCard()
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
