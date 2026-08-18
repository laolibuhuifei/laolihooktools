package com.laoli.hooktools.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.laoli.hooktools.R
import com.laoli.hooktools.util.UpdateManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 适配应用下载页:从服务器拉取适配应用列表,支持下载并安装。
 */
class DownloadAppsActivity : AppCompatActivity() {

    private lateinit var btnBack: MaterialButton
    private lateinit var tvAppsState: TextView
    private lateinit var appsContainer: LinearLayout

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apps)

        btnBack = findViewById(R.id.btnBack)
        tvAppsState = findViewById(R.id.tvAppsState)
        appsContainer = findViewById(R.id.appsContainer)

        btnBack.setOnClickListener { finish() }

        loadApps()
    }

    private fun loadApps() {
        tvAppsState.text = getString(R.string.announcement_loading)
        Thread {
            val list = UpdateManager.fetchApps()
            runOnUiThread { renderApps(list) }
        }.start()
    }

    private fun renderApps(list: List<UpdateManager.AdaptApp>) {
        appsContainer.removeAllViews()

        if (list.isEmpty()) {
            tvAppsState.text = getString(R.string.app_empty)
            tvAppsState.visibility = TextView.VISIBLE
            return
        }

        tvAppsState.visibility = TextView.GONE
        val inflater = LayoutInflater.from(this)
        for (app in list) {
            val card = inflater.inflate(R.layout.item_app, appsContainer, false)
            val ivIcon = card.findViewById<ShapeableImageView>(R.id.ivAppIcon)
            val tvName = card.findViewById<TextView>(R.id.tvAppName)
            val tvMeta = card.findViewById<TextView>(R.id.tvAppMeta)
            val btnDownload = card.findViewById<MaterialButton>(R.id.btnDownload)

            tvName.text = app.name
            tvMeta.text = buildMeta(app)

            // 加载网络图标
            if (!app.iconUrl.isNullOrEmpty()) {
                Glide.with(this)
                    .load(app.iconUrl)
                    .placeholder(R.mipmap.ic_launcher)
                    .error(R.mipmap.ic_launcher)
                    .circleCrop()
                    .into(ivIcon)
            } else {
                ivIcon.setImageResource(R.mipmap.ic_launcher)
            }

            btnDownload.setOnClickListener {
                downloadAndInstall(app, btnDownload)
            }

            appsContainer.addView(card)
        }
    }

    private fun buildMeta(app: UpdateManager.AdaptApp): String {
        val sizeText = if (app.apkSize > 0) {
            String.format(Locale.getDefault(), "%.1f MB", app.apkSize / 1024.0 / 1024.0)
        } else {
            getString(R.string.app_size_unknown)
        }
        val timeText = if (app.publishTime > 0) dateFormat.format(Date(app.publishTime)) else ""
        return "$sizeText  $timeText"
    }

    /** 下载并安装适配应用 */
    private fun downloadAndInstall(app: UpdateManager.AdaptApp, btn: MaterialButton) {
        val apkUrl = app.apkUrl
        if (apkUrl.isNullOrEmpty()) {
            Toast.makeText(this, R.string.app_download_failed, Toast.LENGTH_SHORT).show()
            return
        }

        // 下载到外部 files 目录(与更新安装一致,FileProvider 的 external-files-path 才能覆盖)
        val apkFile = File(
            getExternalFilesDir(null) ?: filesDir,
            "adapt_app_${app.id}.apk"
        )

        // 下载进度对话框
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

        tvTitle.text = getString(R.string.app_downloading, app.name)
        pb.visibility = ProgressBar.VISIBLE
        tvProgress.visibility = TextView.VISIBLE
        tvProgress.text = "0%"
        btnAction.visibility = android.view.View.GONE

        var downloadedFile: File? = null

        btnCancel.setOnClickListener { dialog.dismiss() }

        Thread {
            val ok = UpdateManager.downloadApk(apkUrl, apkFile) { downloaded, total ->
                val percent = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                runOnUiThread {
                    pb.progress = percent
                    tvProgress.text = "$percent%"
                }
            }
            runOnUiThread {
                dialog.dismiss()
                if (ok) {
                    downloadedFile = apkFile
                    doInstall(apkFile)
                } else {
                    Toast.makeText(this, R.string.app_download_failed, Toast.LENGTH_SHORT).show()
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

    /** 执行 APK 安装(调起系统安装器) */
    private fun doInstall(apkFile: File) {
        val launched = UpdateManager.installApk(this, apkFile.absolutePath)
        if (!launched) {
            Toast.makeText(this, R.string.update_install_failed, Toast.LENGTH_LONG).show()
        }
    }
}
