package com.laoli.hooktools.ui

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.drawable.GradientDrawable
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
 * 个人中心设置页:昵称、积分、实名、账号 id、自定义字体。
 */
class PersonalCenterSettingsActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager

    private lateinit var btnBack: View

    // 昵称
    private lateinit var switchName: MaterialSwitch
    private lateinit var etNameValue: EditText
    private lateinit var btnSaveName: MaterialButton

    // 积分
    private lateinit var switchScore: MaterialSwitch
    private lateinit var etScoreValue: EditText
    private lateinit var btnSaveScore: MaterialButton

    // 实名
    private lateinit var switchRealName: MaterialSwitch
    private lateinit var etRealNameValue: EditText
    private lateinit var btnSaveRealName: MaterialButton

    // 自定义字体
    private lateinit var switchFont: MaterialSwitch
    private lateinit var tvFontState: TextView
    private lateinit var btnSelectDownloaded: MaterialButton
    private lateinit var btnSelectFile: MaterialButton
    private lateinit var btnClearFont: MaterialButton

    // 重启个人中心
    private lateinit var btnRestart: MaterialButton

    // 一键恢复
    private lateinit var btnResetAll: MaterialButton

    // 昵称颜色
    private lateinit var switchNameColor: MaterialSwitch
    private lateinit var tvNameColorState: TextView
    private lateinit var llNameColorSwatches: LinearLayout
    private lateinit var etNameColor: EditText
    private lateinit var btnSaveNameColor: MaterialButton

    /** 昵称颜色预设 */
    private val presetNameColors = listOf(
        "#ffffff", "#ff9800", "#ff5722", "#f44336", "#e91e63",
        "#9c27b0", "#673ab7", "#3f51b5", "#2196f3", "#03a9f4",
        "#00bcd4", "#009688", "#4caf50", "#8bc34a", "#ffeb3b",
        "#ffd700", "#000000", "#607d8b"
    )

    // 个人中心背景
    private lateinit var switchBgBoy: MaterialSwitch
    private lateinit var tvBgBoyState: TextView
    private lateinit var btnSelectBgBoy: MaterialButton
    private lateinit var btnClearBgBoy: MaterialButton
    private lateinit var switchBgGirl: MaterialSwitch
    private lateinit var tvBgGirlState: TextView
    private lateinit var btnSelectBgGirl: MaterialButton
    private lateinit var btnClearBgGirl: MaterialButton

    /** 当前正在选择的背景性别:true=男,false=女 */
    private var pendingBgBoy = false

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

    /** 背景图片选择回调 */
    private val pickBgLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val data = result.data
        val photoPath = data?.extras?.getString(MediaStore.EXTRA_OUTPUT, null)
        if (photoPath != null) {
            handlePickedBgPath(photoPath)
            return@registerForActivityResult
        }
        val uri: Uri = data?.data ?: return@registerForActivityResult
        handlePickedBg(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_personal_center_settings)

        prefs = PrefsManager.get(this)

        bindViews()
        setupListeners()
        refreshAll()
    }

    private fun bindViews() {
        btnBack = findViewById(R.id.btnBack)
        switchName = findViewById(R.id.switchName)
        etNameValue = findViewById(R.id.etNameValue)
        btnSaveName = findViewById(R.id.btnSaveName)
        switchScore = findViewById(R.id.switchScore)
        etScoreValue = findViewById(R.id.etScoreValue)
        btnSaveScore = findViewById(R.id.btnSaveScore)
        switchRealName = findViewById(R.id.switchRealName)
        etRealNameValue = findViewById(R.id.etRealNameValue)
        btnSaveRealName = findViewById(R.id.btnSaveRealName)
        switchFont = findViewById(R.id.switchFont)
        tvFontState = findViewById(R.id.tvFontState)
        btnSelectDownloaded = findViewById(R.id.btnSelectDownloaded)
        btnSelectFile = findViewById(R.id.btnSelectFile)
        btnClearFont = findViewById(R.id.btnClearFont)
        btnRestart = findViewById(R.id.btnRestart)
        btnResetAll = findViewById(R.id.btnResetAll)
        switchNameColor = findViewById(R.id.switchNameColor)
        tvNameColorState = findViewById(R.id.tvNameColorState)
        llNameColorSwatches = findViewById(R.id.llNameColorSwatches)
        etNameColor = findViewById(R.id.etNameColor)
        btnSaveNameColor = findViewById(R.id.btnSaveNameColor)
        switchBgBoy = findViewById(R.id.switchBgBoy)
        tvBgBoyState = findViewById(R.id.tvBgBoyState)
        btnSelectBgBoy = findViewById(R.id.btnSelectBgBoy)
        btnClearBgBoy = findViewById(R.id.btnClearBgBoy)
        switchBgGirl = findViewById(R.id.switchBgGirl)
        tvBgGirlState = findViewById(R.id.tvBgGirlState)
        btnSelectBgGirl = findViewById(R.id.btnSelectBgGirl)
        btnClearBgGirl = findViewById(R.id.btnClearBgGirl)
    }

    private fun setupListeners() {
        btnBack.addPressScale()
        btnSaveName.addPressScale()
        btnSaveScore.addPressScale()
        btnSaveRealName.addPressScale()
        btnSelectDownloaded.addPressScale()
        btnSelectFile.addPressScale()
        btnClearFont.addPressScale()
        btnRestart.addPressScale()
        btnResetAll.addPressScale()
        btnSaveNameColor.addPressScale()
        btnSelectBgBoy.addPressScale()
        btnClearBgBoy.addPressScale()
        btnSelectBgGirl.addPressScale()
        btnClearBgGirl.addPressScale()

        btnBack.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        switchName.setOnCheckedChangeListener { _, isChecked ->
            prefs.setPersonalNameEnabled(isChecked)
        }
        btnSaveName.setOnClickListener {
            val text = etNameValue.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) {
                Toast.makeText(this, R.string.pc_name_hint, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.setPersonalNameValue(text)
            if (!prefs.isPersonalNameEnabled()) {
                prefs.setPersonalNameEnabled(true)
                switchName.isChecked = true
            }
            Toast.makeText(this, R.string.toast_saved_success, Toast.LENGTH_SHORT).show()
        }

        switchScore.setOnCheckedChangeListener { _, isChecked ->
            prefs.setPersonalScoreEnabled(isChecked)
        }
        btnSaveScore.setOnClickListener {
            val text = etScoreValue.text?.toString()?.trim().orEmpty()
            val value = text.toIntOrNull()
            if (value == null || value < 0) {
                Toast.makeText(this, R.string.pc_score_hint, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.setPersonalScoreValue(value)
            if (!prefs.isPersonalScoreEnabled()) {
                prefs.setPersonalScoreEnabled(true)
                switchScore.isChecked = true
            }
            Toast.makeText(this, R.string.toast_saved_success, Toast.LENGTH_SHORT).show()
        }

        switchRealName.setOnCheckedChangeListener { _, isChecked ->
            prefs.setPersonalRealNameEnabled(isChecked)
        }
        btnSaveRealName.setOnClickListener {
            val text = etRealNameValue.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) {
                Toast.makeText(this, R.string.pc_realname_hint, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.setPersonalRealNameValue(text)
            if (!prefs.isPersonalRealNameEnabled()) {
                prefs.setPersonalRealNameEnabled(true)
                switchRealName.isChecked = true
            }
            Toast.makeText(this, R.string.toast_saved_success, Toast.LENGTH_SHORT).show()
        }

        switchFont.setOnCheckedChangeListener { _, isChecked ->
            prefs.setPersonalFontEnabled(isChecked)
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

        btnRestart.setOnClickListener {
            forceRestartPersonalCenter()
        }

        btnResetAll.setOnClickListener {
            confirmResetAllPersonal()
        }

        switchNameColor.setOnCheckedChangeListener { _, isChecked ->
            prefs.setPersonalNameColorEnabled(isChecked)
            refreshNameColorCard()
        }
        btnSaveNameColor.setOnClickListener {
            val hex = etNameColor.text?.toString()?.trim().orEmpty()
            if (hex.isEmpty()) {
                Toast.makeText(this, R.string.toast_save_failed, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            try {
                android.graphics.Color.parseColor(hex)
            } catch (_: Throwable) {
                Toast.makeText(this, R.string.toast_save_failed, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.setPersonalNameColorValue(hex)
            if (!prefs.isPersonalNameColorEnabled()) {
                prefs.setPersonalNameColorEnabled(true)
                switchNameColor.isChecked = true
            }
            refreshNameColorCard()
            Toast.makeText(this, R.string.toast_saved_success, Toast.LENGTH_SHORT).show()
        }

        switchBgBoy.setOnCheckedChangeListener { _, isChecked ->
            prefs.setPersonalBgBoyEnabled(isChecked)
            refreshBgCard(true)
        }
        btnSelectBgBoy.setOnClickListener { launchBgPicker(true) }
        btnClearBgBoy.setOnClickListener { clearBg(true) }
        switchBgGirl.setOnCheckedChangeListener { _, isChecked ->
            prefs.setPersonalBgGirlEnabled(isChecked)
            refreshBgCard(false)
        }
        btnSelectBgGirl.setOnClickListener { launchBgPicker(false) }
        btnClearBgGirl.setOnClickListener { clearBg(false) }
    }

    private fun refreshAll() {
        switchName.isChecked = prefs.isPersonalNameEnabled()
        etNameValue.setText(prefs.getPersonalNameValue().orEmpty())
        switchScore.isChecked = prefs.isPersonalScoreEnabled()
        val score = prefs.getPersonalScoreValue()
        etScoreValue.setText(if (score != null) score.toString() else "")
        switchRealName.isChecked = prefs.isPersonalRealNameEnabled()
        etRealNameValue.setText(prefs.getPersonalRealNameValue().orEmpty())
        buildNameColorSwatches()
        refreshNameColorCard()
        refreshBgCard(true)
        refreshBgCard(false)
        refreshFontCard()
    }

    // ---------- 昵称颜色 ----------

    private fun buildNameColorSwatches() {
        llNameColorSwatches.removeAllViews()
        val size = dp(36)
        for (hex in presetNameColors) {
            val swatch = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = dp(8)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(android.graphics.Color.parseColor(hex))
                }
            }
            swatch.setOnClickListener {
                prefs.setPersonalNameColorValue(hex)
                if (!prefs.isPersonalNameColorEnabled()) {
                    prefs.setPersonalNameColorEnabled(true)
                    switchNameColor.isChecked = true
                }
                refreshNameColorCard()
            }
            llNameColorSwatches.addView(swatch)
        }
    }

    private fun refreshNameColorCard() {
        val enabled = prefs.isPersonalNameColorEnabled()
        val value = prefs.getPersonalNameColorValue()
        switchNameColor.isChecked = enabled
        etNameColor.setText(value ?: "")
        if (value.isNullOrEmpty()) {
            tvNameColorState.text = getString(R.string.color_not_set)
            tvNameColorState.setTextColor(getColor(R.color.md_on_surface_variant))
        } else {
            tvNameColorState.text = getString(R.string.color_set, value)
            tvNameColorState.setTextColor(getColor(R.color.md_success))
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    // ---------- 个人中心背景 ----------

    private fun bgTargetName(boy: Boolean): String = if (boy) "pc_bg_boy" else "pc_bg_girl"

    private fun launchBgPicker(boy: Boolean) {
        pendingBgBoy = boy
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            putExtra("com.xtc.camera.LEFT_BUTTON_TEXT", getString(R.string.cancel))
            putExtra("com.xtc.camera.RIGHT_BUTTON_TEXT", getString(R.string.confirm))
        }
        try {
            pickBgLauncher.launch(intent)
        } catch (t: Throwable) {
            try {
                val fallback = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                pickBgLauncher.launch(Intent.createChooser(fallback, getString(R.string.select_image)))
            } catch (_: Throwable) {
                Toast.makeText(this, R.string.toast_no_permission, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handlePickedBg(uri: Uri) {
        val boy = pendingBgBoy
        Thread {
            val path = ImageManager.importUri(this, uri, bgTargetName(boy))
            runOnUiThread {
                if (path == null) {
                    Toast.makeText(this, R.string.toast_image_too_large, Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                applyBgPath(boy, path)
            }
        }.start()
    }

    private fun handlePickedBgPath(sourcePath: String) {
        val boy = pendingBgBoy
        Thread {
            val path = ImageManager.importPath(this, sourcePath, bgTargetName(boy))
            runOnUiThread {
                if (path == null) {
                    Toast.makeText(this, R.string.toast_image_too_large, Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                applyBgPath(boy, path)
            }
        }.start()
    }

    private fun applyBgPath(boy: Boolean, path: String) {
        if (boy) {
            prefs.setPersonalBgBoyPath(path)
            if (!prefs.isPersonalBgBoyEnabled()) {
                prefs.setPersonalBgBoyEnabled(true)
                switchBgBoy.isChecked = true
            }
        } else {
            prefs.setPersonalBgGirlPath(path)
            if (!prefs.isPersonalBgGirlEnabled()) {
                prefs.setPersonalBgGirlEnabled(true)
                switchBgGirl.isChecked = true
            }
        }
        refreshBgCard(boy)
        Toast.makeText(this, R.string.toast_saved_success, Toast.LENGTH_SHORT).show()
    }

    private fun clearBg(boy: Boolean) {
        ImageManager.clear(this, bgTargetName(boy))
        if (boy) {
            prefs.setPersonalBgBoyPath(null)
            prefs.setPersonalBgBoyEnabled(false)
        } else {
            prefs.setPersonalBgGirlPath(null)
            prefs.setPersonalBgGirlEnabled(false)
        }
        refreshBgCard(boy)
    }

    private fun refreshBgCard(boy: Boolean) {
        if (boy) {
            val enabled = prefs.isPersonalBgBoyEnabled()
            val path = prefs.getPersonalBgBoyPath()
            switchBgBoy.isChecked = enabled
            val exists = !path.isNullOrEmpty() && File(path).exists()
            if (exists) {
                tvBgBoyState.text = getString(R.string.image_set)
                tvBgBoyState.setTextColor(getColor(R.color.md_success))
            } else {
                tvBgBoyState.text = getString(R.string.image_not_set)
                tvBgBoyState.setTextColor(getColor(R.color.md_on_surface_variant))
            }
            btnClearBgBoy.isEnabled = exists
        } else {
            val enabled = prefs.isPersonalBgGirlEnabled()
            val path = prefs.getPersonalBgGirlPath()
            switchBgGirl.isChecked = enabled
            val exists = !path.isNullOrEmpty() && File(path).exists()
            if (exists) {
                tvBgGirlState.text = getString(R.string.image_set)
                tvBgGirlState.setTextColor(getColor(R.color.md_success))
            } else {
                tvBgGirlState.text = getString(R.string.image_not_set)
                tvBgGirlState.setTextColor(getColor(R.color.md_on_surface_variant))
            }
            btnClearBgGirl.isEnabled = exists
        }
    }

    // ---------- 自定义字体 ----------

    private fun refreshFontCard() {
        val enabled = prefs.isPersonalFontEnabled()
        val path = prefs.getPersonalFontPath()
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
                val target = pcFontTargetFile()
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
            val target = pcFontTargetFile()
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

    private fun pcFontTargetFile(): File {
        val dir = File(Environment.getExternalStorageDirectory(), "laoli_hooktools")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, Constants.PC_FONT_FILE_NAME)
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

    private fun applyFontPath(path: String) {
        prefs.setPersonalFontPath(path)
        if (!prefs.isPersonalFontEnabled()) {
            prefs.setPersonalFontEnabled(true)
        }
        refreshFontCard()
        Toast.makeText(this, R.string.toast_saved_success, Toast.LENGTH_SHORT).show()
    }

    private fun clearFont() {
        try {
            val path = prefs.getPersonalFontPath()
            if (!path.isNullOrEmpty()) File(path).delete()
        } catch (_: Throwable) {
        }
        prefs.setPersonalFontPath(null)
        prefs.setPersonalFontEnabled(false)
        refreshFontCard()
    }

    // ---------- 一键恢复 ----------

    private fun confirmResetAllPersonal() {
        AlertDialog.Builder(this)
            .setTitle(R.string.pc_reset_all)
            .setMessage(R.string.pc_reset_all_confirm)
            .setPositiveButton(R.string.confirm) { _, _ -> resetAllPersonal() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun resetAllPersonal() {
        // 昵称
        prefs.setPersonalNameEnabled(false)
        prefs.setPersonalNameValue(null)
        // 积分
        prefs.setPersonalScoreEnabled(false)
        prefs.setPersonalScoreValue(null)
        // 实名
        prefs.setPersonalRealNameEnabled(false)
        prefs.setPersonalRealNameValue(null)
        // 字体
        try {
            val fontPath = prefs.getPersonalFontPath()
            if (!fontPath.isNullOrEmpty()) File(fontPath).delete()
        } catch (_: Throwable) {
        }
        prefs.setPersonalFontPath(null)
        prefs.setPersonalFontEnabled(false)
        // 昵称颜色
        prefs.setPersonalNameColorEnabled(false)
        prefs.setPersonalNameColorValue(null)
        // 背景
        ImageManager.clear(this, "pc_bg_boy")
        prefs.setPersonalBgBoyPath(null)
        prefs.setPersonalBgBoyEnabled(false)
        ImageManager.clear(this, "pc_bg_girl")
        prefs.setPersonalBgGirlPath(null)
        prefs.setPersonalBgGirlEnabled(false)

        refreshAll()
        Toast.makeText(this, R.string.pc_reset_all_done, Toast.LENGTH_SHORT).show()
    }

    // ---------- 重启个人中心 ----------

    private fun forceRestartPersonalCenter() {
        Thread {
            if (!RootUtil.hasRootAccess()) {
                runOnUiThread {
                    Toast.makeText(this, R.string.toast_no_root, Toast.LENGTH_LONG).show()
                }
                return@Thread
            }

            val stopCode = RootUtil.execRoot("am force-stop ${Constants.PERSONAL_CENTER_PACKAGE}")

            try {
                Thread.sleep(500)
            } catch (_: InterruptedException) {
            }

            val startCode = RootUtil.execRoot(
                "monkey -p ${Constants.PERSONAL_CENTER_PACKAGE} -c android.intent.category.LAUNCHER 1"
            )

            runOnUiThread {
                if (stopCode == 0 && startCode == 0) {
                    Toast.makeText(this, R.string.toast_pc_restarted, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, R.string.toast_restart_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}
