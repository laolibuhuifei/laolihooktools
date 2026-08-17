package com.laoli.hooktools.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.laoli.hooktools.R
import com.laoli.hooktools.util.UpdateManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 公告查看页。
 */
class AnnouncementActivity : AppCompatActivity() {

    private lateinit var btnBack: MaterialButton
    private lateinit var tvAnnState: TextView
    private lateinit var annContainer: LinearLayout

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_announcement)

        btnBack = findViewById(R.id.btnBack)
        tvAnnState = findViewById(R.id.tvAnnState)
        annContainer = findViewById(R.id.annContainer)

        btnBack.setOnClickListener { finish() }

        loadAnnouncements()
    }

    private fun loadAnnouncements() {
        tvAnnState.text = getString(R.string.announcement_loading)
        Thread {
            val list = UpdateManager.fetchAnnouncements()
            runOnUiThread {
                renderAnnouncements(list)
            }
        }.start()
    }

    private fun renderAnnouncements(list: List<UpdateManager.Announcement>) {
        annContainer.removeAllViews()

        if (list.isEmpty()) {
            tvAnnState.text = getString(R.string.announcement_empty)
            tvAnnState.visibility = TextView.VISIBLE
            return
        }

        tvAnnState.visibility = TextView.GONE
        val inflater = LayoutInflater.from(this)
        for (ann in list) {
            val card = inflater.inflate(R.layout.item_announcement, annContainer, false)
            card.findViewById<TextView>(R.id.tvAnnTitle).text = ann.title
            card.findViewById<TextView>(R.id.tvAnnContent).text = ann.content
            card.findViewById<TextView>(R.id.tvAnnTime).text =
                dateFormat.format(Date(ann.publishTime))
            annContainer.addView(card)
        }
    }
}
