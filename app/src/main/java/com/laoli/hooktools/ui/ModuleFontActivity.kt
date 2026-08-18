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
import com.laoli.hooktools.util.ModuleFont
import com.laoli.hooktools.util.RootUtil
import com.laoli.hooktools.util.addPressScale
import java.io.File

/**
 * 模块字体设置页:设置模块自身界面的显示字体。
 */
class ModuleFontActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager

    private lateinit var btnBack: View
    private lateinit var switchModuleFont: MaterialSwitch
    private lateinit var tvModuleFontState: TextView
    private lateinit var btnSelectDownloaded: MaterialButton
    private lateinit var btnSelectFile: MaterialButton
    private lateinit var btnClear: MaterialButton

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_module_font)

        prefs = PrefsManager.get(this)

        bindViews()
        setupListeners()
        refreshFontCard()
    }

    private fun bindViews() {
        btnBack = findViewById(R.id.btnBack)
        switchModuleFont = findViewById(R.id.switchModuleFont)
        tvModuleFontState = findViewById(R.id.tvModuleFontState)
        btnSelectDownloaded = findViewById(R.id.btnSelectDownloaded)
        btnSelectFile = findViewById(R.id.btnSelectFile)
        btnClear = findViewById(R.id.btnClear)
    }

    private fun setupListeners() {
        btnBack.addPressScale()
        btnSelectDownloaded.addPressScale()
        btnSelectFile.addPressScale()
        btnClear.addPressScale()

        btnBack.setOnClickListener { finish() }

        switchModuleFont.setOnCheckedChangeListener { _, isChecked ->
            prefs.setModuleFontEnabled(isChecked)
            ModuleFont.invalidate()
            ModuleFont.applyToActivity(this)
            refreshFontCard()
        }

        btnSelectDownloaded.setOnClickListener {
            showDownloadedFontPicker()
        }
        btnSelectFile.setOnClickListener {
            launchFontPicker()
        }
        btnClear.setOnClickListener {
            clearFont()
        }
    }

    private fun refreshFontCard() {
        val enabled = prefs.isModuleFontEnabled()
        val path = prefs.getModuleFontPath()
        switchModuleFont.isChecked = enabled
        updateFontVisual(path)
    }

    private fun updateFontVisual(path: String?) {
        val exists = !path.isNullOrEmpty() && File(path).exists()
        if (exists) {
            tvModuleFontState.text = getString(R.string.module_font_set)
            tvModuleFontState.setTextColor(getColor(R.color.md_success))
        } else {
            tvModuleFontState.text = getString(R.string.module_font_not_set)
            tvModuleFontState.setTextColor(getColor(R.color.md_on_surface_variant))
        }
        btnClear.isEnabled = exists
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
            val path = try {
                val target = moduleFontTargetFile()
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
            val target = moduleFontTargetFile()
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

    private fun moduleFontTargetFile(): File {
        val dir = File(Environment.getExternalStorageDirectory(), "laoli_hooktools")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "module_font.ttf")
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
        prefs.setModuleFontPath(path)
        if (!prefs.isModuleFontEnabled()) {
            prefs.setModuleFontEnabled(true)
        }
        ModuleFont.invalidate()
        ModuleFont.applyToActivity(this)
        refreshFontCard()
        Toast.makeText(this, R.string.toast_saved_success, Toast.LENGTH_SHORT).show()
    }

    private fun clearFont() {
        try {
            val path = prefs.getModuleFontPath()
            if (!path.isNullOrEmpty()) File(path).delete()
        } catch (_: Throwable) {
        }
        prefs.setModuleFontPath(null)
        prefs.setModuleFontEnabled(false)
        ModuleFont.invalidate()
        refreshFontCard()
    }
}
