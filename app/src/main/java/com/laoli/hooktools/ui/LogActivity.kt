package com.laoli.hooktools.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.laoli.hooktools.R
import com.laoli.hooktools.util.Logger

/**
 * 日志查看页。
 * 读取 /sdcard/laoli_hooktools/laoli_log.txt 显示在屏幕上。
 */
class LogActivity : AppCompatActivity() {

    private lateinit var tvLog: TextView
    private lateinit var btnRefresh: MaterialButton
    private lateinit var btnClear: MaterialButton
    private lateinit var btnBack: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        tvLog = findViewById(R.id.tvLogContent)
        btnRefresh = findViewById(R.id.btnRefreshLog)
        btnClear = findViewById(R.id.btnClearLog)
        btnBack = findViewById(R.id.btnBack)

        btnRefresh.setOnClickListener { loadLog() }
        btnClear.setOnClickListener {
            Logger.clearLog()
            loadLog()
        }
        btnBack.setOnClickListener { finish() }

        loadLog()
    }

    override fun onResume() {
        super.onResume()
        loadLog()
    }

    private fun loadLog() {
        // 后台读取(文件可能较大)
        Thread {
            val content = Logger.readLog()
            runOnUiThread {
                tvLog.text = if (content.isBlank()) "日志为空" else content
            }
        }.start()
    }
}
