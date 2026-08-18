package com.laoli.hooktools.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.laoli.hooktools.R
import com.laoli.hooktools.util.FavoriteStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 收藏详情:长按收藏夹列表项后全屏查看该动态的完整文字、图片/视频、点赞与评论信息。
 */
class FavoriteDetailActivity : AppCompatActivity() {

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorite_detail)

        val momentId = intent.getStringExtra("momentId")
        val fav = momentId?.let { FavoriteStore.get(it) }
        if (fav == null) {
            Toast.makeText(this, "未找到该收藏", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val ivIcon = findViewById<ImageView>(R.id.ivDetailIcon)
        val tvName = findViewById<TextView>(R.id.tvDetailName)
        val tvTime = findViewById<TextView>(R.id.tvDetailTime)
        val tvContent = findViewById<TextView>(R.id.tvDetailContent)
        val llMedia = findViewById<LinearLayout>(R.id.llDetailMedia)
        val tvLike = findViewById<TextView>(R.id.tvDetailLikeCount)
        val tvComment = findViewById<TextView>(R.id.tvDetailCommentCount)
        val tvBack = findViewById<TextView>(R.id.tvDetailBack)
        val tvDelete = findViewById<TextView>(R.id.tvDetailDelete)

        tvName.text = fav.name
        tvTime.text = timeFormat.format(Date(fav.createTime))
        tvContent.text = fav.content
        tvContent.visibility = if (fav.content.isBlank()) View.GONE else View.VISIBLE
        tvLike.text = fav.likeCount.toString()
        tvComment.text = fav.commentCount.toString()

        Glide.with(ivIcon.context)
            .load(fav.iconUrl)
            .placeholder(R.drawable.default_custom_default)
            .error(R.drawable.default_custom_default)
            .centerCrop()
            .into(ivIcon)

        // 动态插入媒体(图片直接显示,视频用占位提示)
        val dir = FavoriteStore.dirOf(fav.momentId)
        for (name in fav.mediaFiles) {
            val file = File(dir, name)
            if (!file.exists()) continue
            if (name.endsWith(".jpg", ignoreCase = true) ||
                name.endsWith(".jpeg", ignoreCase = true) ||
                name.endsWith(".png", ignoreCase = true)
            ) {
                val iv = ImageView(this)
                iv.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4) }
                iv.adjustViewBounds = true
                iv.maxHeight = dp(360)
                Glide.with(this).load(file).into(iv)
                llMedia.addView(iv)
            } else {
                val tv = TextView(this)
                tv.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4) }
                tv.setBackgroundColor(0xFF333333.toInt())
                tv.setTextColor(0xFFFFFFFF.toInt())
                tv.textSize = 13f
                tv.gravity = android.view.Gravity.CENTER
                tv.setPadding(dp(12), dp(20), dp(12), dp(20))
                tv.text = "视频 · $name"
                llMedia.addView(tv)
            }
        }
        llMedia.visibility = if (fav.mediaFiles.isEmpty()) View.GONE else View.VISIBLE

        tvBack.setOnClickListener { finish() }
        tvDelete.setOnClickListener { confirmDelete(fav) }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun confirmDelete(fav: FavoriteStore.FavoriteMoment) {
        AlertDialog.Builder(this)
            .setTitle("删除收藏")
            .setMessage("确定删除这条收藏吗？本地保存的文字、图片或视频也会一并删除。")
            .setPositiveButton("删除") { _, _ ->
                FavoriteStore.remove(fav.momentId)
                FavoriteStore.dirOf(fav.momentId).deleteRecursively()
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
