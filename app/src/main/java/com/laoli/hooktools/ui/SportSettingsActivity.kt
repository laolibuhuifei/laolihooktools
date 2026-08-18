package com.laoli.hooktools.ui

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.laoli.hooktools.R
import com.laoli.hooktools.prefs.PrefsManager
import com.laoli.hooktools.util.Constants
import com.laoli.hooktools.util.FontStore
import com.laoli.hooktools.util.ImageManager
import com.laoli.hooktools.util.RootUtil
import com.laoli.hooktools.util.addPressScale
import java.io.File

/**
 * 运动设置页:能量值修改、一键红环(等级 5 级)、运动自定义字体。
 */
class SportSettingsActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager

    private lateinit var btnBack: View

    // 能量值
    private lateinit var switchEnergy: MaterialSwitch
    private lateinit var etEnergyValue: EditText
    private lateinit var btnSaveEnergy: MaterialButton

    // 一键红环
    private lateinit var switchRedRing: MaterialSwitch
    private lateinit var etRingCount: EditText
    private lateinit var btnSaveRingCount: MaterialButton

    // 自定义字体
    private lateinit var switchFont: MaterialSwitch
    private lateinit var tvFontState: TextView
    private lateinit var btnSelectDownloaded: MaterialButton
    private lateinit var btnSelectFile: MaterialButton
    private lateinit var btnClearFont: MaterialButton

    // 自定义头像
    private lateinit var switchAvatar: MaterialSwitch
    private lateinit var tvAvatarState: TextView
    private lateinit var btnSelectAvatar: MaterialButton
    private lateinit var btnClearAvatar: MaterialButton

    // 重启运动
    private lateinit var btnRestart: MaterialButton

    /** 字体文件选择回调 */
    private val pickFontLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val data = result.data
        val fontPath = data?.extras?.getString(MediaStore.EXTRA_OUTPUT, null)
        if (fontPath != null) {
            handlePickedFontPath(fontPath)
            return@registerForActivityResult
        }
        val uri: Uri = data?.data ?: return@registerForActivityResult
        handlePickedFont(uri)
    }

    /** 头像图片选择回调 */
    private val pickAvatarLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val data = result.data
        val avatarPath = data?.extras?.getString(MediaStore.EXTRA_OUTPUT, null)
        if (avatarPath != null) {
            handlePickedAvatarPath(avatarPath)
            return@registerForActivityResult
        }
        val uri: Uri = data?.data ?: return@registerForActivityResult
        handlePickedAvatar(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sport_settings)

        prefs = PrefsManager.get(this)

        bindViews()
        setupListeners()
        refreshAll()
    }

    private fun bindViews() {
        btnBack = findViewById(R.id.btnBack)
        switchEnergy = findViewById(R.id.switchEnergy)
        etEnergyValue = findViewById(R.id.etEnergyValue)
        btnSaveEnergy = findViewById(R.id.btnSaveEnergy)
        switchRedRing = findViewById(R.id.switchRedRing)
        etRingCount = findViewById(R.id.etRingCount)
        btnSaveRingCount = findViewById(R.id.btnSaveRingCount)
        switchFont = findViewById(R.id.switchFont)
        tvFontState = findViewById(R.id.tvFontState)
        btnSelectDownloaded = findViewById(R.id.btnSelectDownloaded)
        btnSelectFile = findViewById(R.id.btnSelectFile)
        btnClearFont = findViewById(R.id.btnClearFont)
        switchAvatar = findViewById(R.id.switchAvatar)
        tvAvatarState = findViewById(R.id.tvAvatarState)
        btnSelectAvatar = findViewById(R.id.btnSelectAvatar)
        btnClearAvatar = findViewById(R.id.btnClearAvatar)
        btnRestart = findViewById(R.id.btnRestart)
    }

    private fun setupListeners() {
        btnBack.addPressScale()
        btnSaveEnergy.addPressScale()
        btnSaveRingCount.addPressScale()
        btnSelectDownloaded.addPressScale()
        btnSelectFile.addPressScale()
        btnClearFont.addPressScale()
        btnSelectAvatar.addPressScale()
        btnClearAvatar.addPressScale()
        btnRestart.addPressScale()

        btnBack.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        switchEnergy.setOnCheckedChangeListener { _, isChecked ->
            prefs.setSportEnergyEnabled(isChecked)
        }

        btnSaveEnergy.setOnClickListener {
            val text = etEnergyValue.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) {
                Toast.makeText(this, R.string.sport_energy_hint, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val value = text.toIntOrNull()
            if (value == null || value < 0) {
                Toast.makeText(this, R.string.sport_energy_hint, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.setSportEnergyValue(value)
            if (!prefs.isSportEnergyEnabled()) {
                prefs.setSportEnergyEnabled(true)
                switchEnergy.isChecked = true
            }
            Toast.makeText(this, R.string.toast_saved_success, Toast.LENGTH_SHORT).show()
        }

        switchRedRing.setOnCheckedChangeListener { _, isChecked ->
            prefs.setSportRedRingEnabled(isChecked)
        }

        btnSaveRingCount.setOnClickListener {
            val text = etRingCount.text?.toString()?.trim().orEmpty()
            val count = text.toIntOrNull()
            if (count == null || count < 1 || count > 20) {
                Toast.makeText(this, R.string.sport_ring_count_hint, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.setSportRedRingCount(count)
            if (!prefs.isSportRedRingEnabled()) {
                prefs.setSportRedRingEnabled(true)
                switchRedRing.isChecked = true
            }
            Toast.makeText(this, R.string.toast_saved_success, Toast.LENGTH_SHORT).show()
        }

        switchFont.setOnCheckedChangeListener { _, isChecked ->
            prefs.setSportFontEnabled(isChecked)
            refreshFontCard()
        }

        btnSelectDownloaded.setOnClickListener {
            showDownloadedFontPicker()
        }
        btnSelectFile.setOnClickListener {
            launchFontPicker()
        }
        btnClearFont.setOnClickListener {
            clearFont()
        }

        switchAvatar.setOnCheckedChangeListener { _, isChecked ->
            prefs.setSportAvatarEnabled(isChecked)
            refreshAvatarCard()
        }
        btnSelectAvatar.setOnClickListener {
            launchAvatarPicker()
        }
        btnClearAvatar.setOnClickListener {
            clearAvatar()
        }

        btnRestart.setOnClickListener {
            forceRestartSport()
        }
    }

    private fun refreshAll() {
        switchEnergy.isChecked = prefs.isSportEnergyEnabled()
        val value = prefs.getSportEnergyValue()
        etEnergyValue.setText(if (value != null) value.toString() else "")
        switchRedRing.isChecked = prefs.isSportRedRingEnabled()
        etRingCount.setText(prefs.getSportRedRingCount().toString())
        refreshFontCard()
        refreshAvatarCard()
    }

    // ---------- 自定义字体 ----------

    private fun refreshFontCard() {
        val enabled = prefs.isSportFontEnabled()
        val path = prefs.getSportFontPath()
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
                    applyFontPath(file.absolutePath)
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
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            setBackgroundDrawableResource(R.color.md_background)
        }
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
            runOnUiThread {
                if (path == null) {
                    Toast.makeText(this, R.string.toast_save_failed, Toast.LENGTH_SHORT).show()
                } else {
                    applyFontPath(path)
                }
            }
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
                val target = sportFontTargetFile()
                src.copyTo(target, overwrite = true)
                target.setReadable(true, false)
                try { RootUtil.execRoot("chmod 644 ${target.absolutePath}") } catch (_: Throwable) {}
                target.absolutePath
            } catch (t: Throwable) {
                null
            }
            runOnUiThread {
                if (path == null) {
                    Toast.makeText(this, R.string.toast_save_failed, Toast.LENGTH_SHORT).show()
                } else {
                    applyFontPath(path)
                }
            }
        }.start()
    }

    private fun copyFontUri(uri: Uri): String? {
        return try {
            val target = sportFontTargetFile()
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

    private fun sportFontTargetFile(): File {
        val dir = File(Environment.getExternalStorageDirectory(), "laoli_hooktools")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, Constants.SPORT_FONT_FILE_NAME)
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

    /** 应用字体路径并刷新当前界面 */
    private fun applyFontPath(path: String) {
        prefs.setSportFontPath(path)
        if (!prefs.isSportFontEnabled()) {
            prefs.setSportFontEnabled(true)
        }
        refreshFontCard()
        Toast.makeText(this, R.string.toast_saved_success, Toast.LENGTH_SHORT).show()
    }

    private fun clearFont() {
        try {
            val path = prefs.getSportFontPath()
            if (!path.isNullOrEmpty()) File(path).delete()
        } catch (_: Throwable) {
        }
        prefs.setSportFontPath(null)
        prefs.setSportFontEnabled(false)
        refreshFontCard()
    }

    // ---------- 自定义头像 ----------

    private fun refreshAvatarCard() {
        val enabled = prefs.isSportAvatarEnabled()
        val path = prefs.getSportAvatarPath()
        switchAvatar.isChecked = enabled
        updateAvatarVisual(path)
    }

    private fun updateAvatarVisual(path: String?) {
        val exists = !path.isNullOrEmpty() && File(path).exists()
        if (exists) {
            tvAvatarState.text = getString(R.string.image_set)
            tvAvatarState.setTextColor(getColor(R.color.md_success))
        } else {
            tvAvatarState.text = getString(R.string.image_not_set)
            tvAvatarState.setTextColor(getColor(R.color.md_on_surface_variant))
        }
        btnClearAvatar.isEnabled = exists
    }

    private fun launchAvatarPicker() {
        // 与好友圈图片选择一致:ACTION_GET_CONTENT + 小天才相册左右按钮文本,直接打开相册
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            putExtra("com.xtc.camera.LEFT_BUTTON_TEXT", getString(R.string.cancel))
            putExtra("com.xtc.camera.RIGHT_BUTTON_TEXT", getString(R.string.confirm))
        }
        try {
            pickAvatarLauncher.launch(intent)
        } catch (t: Throwable) {
            // 兜底:打开系统图库
            try {
                val fallback = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                pickAvatarLauncher.launch(Intent.createChooser(fallback, getString(R.string.select_image)))
            } catch (_: Throwable) {
                Toast.makeText(this, R.string.toast_no_permission, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handlePickedAvatar(uri: Uri) {
        Thread {
            val path = ImageManager.importUri(this, uri, "sport_avatar")
            runOnUiThread {
                if (path == null) {
                    Toast.makeText(this, R.string.toast_image_too_large, Toast.LENGTH_SHORT).show()
                } else {
                    applyAvatarPath(path)
                }
            }
        }.start()
    }

    private fun handlePickedAvatarPath(sourcePath: String) {
        Thread {
            val path = ImageManager.importPath(this, sourcePath, "sport_avatar")
            runOnUiThread {
                if (path == null) {
                    Toast.makeText(this, R.string.toast_image_too_large, Toast.LENGTH_SHORT).show()
                } else {
                    applyAvatarPath(path)
                }
            }
        }.start()
    }

    private fun applyAvatarPath(path: String) {
        prefs.setSportAvatarPath(path)
        if (!prefs.isSportAvatarEnabled()) {
            prefs.setSportAvatarEnabled(true)
        }
        refreshAvatarCard()
        Toast.makeText(this, R.string.toast_saved_success, Toast.LENGTH_SHORT).show()
    }

    private fun clearAvatar() {
        try {
            val path = prefs.getSportAvatarPath()
            if (!path.isNullOrEmpty()) File(path).delete()
        } catch (_: Throwable) {
        }
        prefs.setSportAvatarPath(null)
        prefs.setSportAvatarEnabled(false)
        refreshAvatarCard()
    }

    // ---------- 重启运动 ----------

    private fun forceRestartSport() {
        Thread {
            // 1. 检查 root
            if (!RootUtil.hasRootAccess()) {
                runOnUiThread {
                    Toast.makeText(this, R.string.toast_no_root, Toast.LENGTH_LONG).show()
                }
                return@Thread
            }

            // 2. 强制停止运动
            val stopCode = RootUtil.execRoot("am force-stop ${Constants.SPORT_PACKAGE}")

            // 3. 等待进程完全退出
            try {
                Thread.sleep(500)
            } catch (_: InterruptedException) {
            }

            // 4. 重新启动运动
            val startCode = RootUtil.execRoot(
                "monkey -p ${Constants.SPORT_PACKAGE} -c android.intent.category.LAUNCHER 1"
            )

            runOnUiThread {
                if (stopCode == 0 && startCode == 0) {
                    Toast.makeText(this, R.string.toast_sport_restarted, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, R.string.toast_restart_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}
