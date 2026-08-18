package com.laoli.hooktools.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.laoli.hooktools.R
import com.laoli.hooktools.util.FavoriteStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 收藏夹:以好友圈样式展示收藏的动态。
 *
 * 收藏动态由好友圈 Hook 写入(长按动态弹窗里的"取消"按钮收藏),
 * 这里读取 FavoriteStore 展示;长按某条收藏可全屏查看详细信息(含删除)。
 */
class FavoriteActivity : AppCompatActivity() {

    private lateinit var rvFavorites: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: FavoriteAdapter

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorite)

        rvFavorites = findViewById(R.id.rvFavorites)
        tvEmpty = findViewById(R.id.tvFavEmpty)

        adapter = FavoriteAdapter()
        rvFavorites.layoutManager = LinearLayoutManager(this)
        rvFavorites.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val list = FavoriteStore.list()
        adapter.submit(list)
        tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        rvFavorites.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun openDetail(fav: FavoriteStore.FavoriteMoment) {
        startActivity(
            Intent(this, FavoriteDetailActivity::class.java)
                .putExtra("momentId", fav.momentId)
        )
    }

    private inner class FavoriteAdapter :
        RecyclerView.Adapter<FavoriteAdapter.Holder>() {

        private val items = mutableListOf<FavoriteStore.FavoriteMoment>()

        fun submit(list: List<FavoriteStore.FavoriteMoment>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_favorite, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            private val ivIcon: ImageView = view.findViewById(R.id.ivFavIcon)
            private val tvName: TextView = view.findViewById(R.id.tvFavName)
            private val tvContent: TextView = view.findViewById(R.id.tvFavContent)
            private val tvMeta: TextView = view.findViewById(R.id.tvFavMeta)
            private val tvLikeCount: TextView = view.findViewById(R.id.tvFavLikeCount)

            fun bind(fav: FavoriteStore.FavoriteMoment) {
                tvName.text = fav.name
                tvContent.text = fav.content
                tvContent.visibility = if (fav.content.isBlank()) View.GONE else View.VISIBLE
                tvMeta.text = timeFormat.format(Date(fav.createTime))
                tvLikeCount.text = if (fav.likeCount > 0) fav.likeCount.toString() else ""

                Glide.with(ivIcon.context)
                    .load(fav.iconUrl)
                    .placeholder(R.drawable.default_custom_default)
                    .error(R.drawable.default_custom_default)
                    .centerCrop()
                    .into(ivIcon)

                itemView.setOnClickListener { openDetail(fav) }
                itemView.setOnLongClickListener {
                    openDetail(fav)
                    true
                }
            }
        }
    }
}
