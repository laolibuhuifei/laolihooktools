package com.laoli.hooktools.ui

import android.app.Dialog
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.laoli.hooktools.R
import com.laoli.hooktools.util.FontStore
import com.laoli.hooktools.util.UpdateManager
import java.io.File

/**
 * 字体中心:从服务器拉取字体列表,支持预览与下载/删除。
 * 已下载的字体,下载按钮会变成删除按钮。
 */
class FontCenterActivity : AppCompatActivity() {

    private lateinit var btnBack: MaterialButton
    private lateinit var tvFontsState: TextView
    private lateinit var fontsContainer: LinearLayout

    /** 最近一次拉取的字体列表(下载/删除后用于刷新按钮状态) */
    private var cachedFonts: List<UpdateManager.FontItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_font_center)

        btnBack = findViewById(R.id.btnBack)
        tvFontsState = findViewById(R.id.tvFontsState)
        fontsContainer = findViewById(R.id.fontsContainer)

        btnBack.setOnClickListener { finish() }

        loadFonts()
    }

    private fun loadFonts() {
        tvFontsState.text = getString(R.string.announcement_loading)
        Thread {
            val list = UpdateManager.fetchFonts()
            runOnUiThread { renderFonts(list) }
        }.start()
    }

    private fun renderFonts(list: List<UpdateManager.FontItem>) {
        cachedFonts = list
        fontsContainer.removeAllViews()

        if (list.isEmpty()) {
            tvFontsState.text = getString(R.string.font_empty)
            tvFontsState.visibility = TextView.VISIBLE
            return
        }

        tvFontsState.visibility = TextView.GONE
        val inflater = LayoutInflater.from(this)
        for (font in list) {
            val card = inflater.inflate(R.layout.item_font, fontsContainer, false)
            val tvName = card.findViewById<TextView>(R.id.tvFontName)
            val tvPreview = card.findViewById<TextView>(R.id.tvFontPreview)
            val btnPreview = card.findViewById<MaterialButton>(R.id.btnPreview)
            val btnDownload = card.findViewById<MaterialButton>(R.id.btnDownload)

            tvName.text = font.name
            btnPreview.setOnClickListener { previewFont(font, tvPreview) }

            updateDownloadButton(btnDownload, font)

            fontsContainer.addView(card)
        }
    }

    /** 根据是否已下载,设置按钮文字与点击行为(下载/删除切换) */
    private fun updateDownloadButton(btn: MaterialButton, font: UpdateManager.FontItem) {
        val downloaded = isFontDownloaded(font)
        if (downloaded) {
            btn.text = getString(R.string.font_delete)
            btn.setOnClickListener { deleteFont(font) }
        } else {
            btn.text = getString(R.string.app_download)
            btn.setOnClickListener { downloadFont(font) }
        }
    }

    /** 判断字体是否已下载到专门文件夹 */
    private fun isFontDownloaded(font: UpdateManager.FontItem): Boolean {
        val target = targetFileFor(font)
        return target.exists()
    }

    /** 该字体对应的下载目标文件 */
    private fun targetFileFor(font: UpdateManager.FontItem): File {
        val ext = FontStore.extFromUrl(font.fontUrl)
        return FontStore.targetFile(font.name, ext)
    }

    /** 预览:下载字体到临时目录,用其渲染预览文字 */
    private fun previewFont(font: UpdateManager.FontItem, tvPreview: TextView) {
        val url = font.fontUrl
        if (url.isNullOrEmpty()) {
            Toast.makeText(this, R.string.font_download_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val ext = FontStore.extFromUrl(url)
        val tmp = File(cacheDir, "preview_${font.id}$ext")

        Thread {
            val ok = UpdateManager.downloadFile(url, tmp) { _, _ -> }
            runOnUiThread {
                if (!ok) {
                    Toast.makeText(this, R.string.font_download_failed, Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                try {
                    val tf = Typeface.createFromFile(tmp)
                    tvPreview.typeface = tf
                } catch (t: Throwable) {
                    Toast.makeText(this, R.string.font_download_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    /** 下载字体到专门文件夹(带进度条) */
    private fun downloadFont(font: UpdateManager.FontItem) {
        val url = font.fontUrl
        if (url.isNullOrEmpty()) {
            Toast.makeText(this, R.string.font_download_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val target = targetFileFor(font)

        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_update, null)
        dialog.setContentView(view)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)

        val tvTitle = view.findViewById<TextView>(R.id.tvUpdateTitle)
        val pb = view.findViewById<ProgressBar>(R.id.pbDownload)
        val tvProgress = view.findViewById<TextView>(R.id.tvProgress)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btnCancel)
        val btnAction = view.findViewById<MaterialButton>(R.id.btnAction)

        tvTitle.text = getString(R.string.font_downloading_title, font.name)
        pb.visibility = ProgressBar.VISIBLE
        tvProgress.visibility = TextView.VISIBLE
        tvProgress.text = "0%"
        btnAction.visibility = android.view.View.GONE

        btnCancel.setOnClickListener { dialog.dismiss() }

        Thread {
            val ok = UpdateManager.downloadFile(url, target) { downloaded, total ->
                val percent = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                runOnUiThread {
                    pb.progress = percent
                    tvProgress.text = "$percent%"
                }
            }
            runOnUiThread {
                dialog.dismiss()
                if (ok) {
                    try {
                        target.setReadable(true, false)
                    } catch (_: Throwable) {
                    }
                    Toast.makeText(this, R.string.font_download_done, Toast.LENGTH_SHORT).show()
                    renderFonts(cachedFonts)
                } else {
                    Toast.makeText(this, R.string.font_download_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()

        dialog.show()
        dialog.window?.apply {
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            setBackgroundDrawableResource(R.color.md_background)
        }
    }

    /** 删除已下载的字体 */
    private fun deleteFont(font: UpdateManager.FontItem) {
        val target = targetFileFor(font)
        try {
            if (target.exists()) target.delete()
        } catch (_: Throwable) {
        }
        Toast.makeText(this, R.string.font_deleted, Toast.LENGTH_SHORT).show()
        renderFonts(cachedFonts)
    }
}
