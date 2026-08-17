package com.laoli.hooktools.ui

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.laoli.hooktools.R
import com.laoli.hooktools.prefs.PrefsManager
import com.laoli.hooktools.util.Constants

/**
 * 颜色修改页:集中管理首页 banner / 发布按钮 / 名字 三个文字颜色。
 */
class ColorActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager

    private data class ColorCardHolder(
        val target: Constants.TargetColor,
        val root: View,
        val tvTitle: TextView,
        val tvDesc: TextView,
        val switchColor: MaterialSwitch,
        val tvState: TextView,
        val llSwatches: LinearLayout,
        val etColor: EditText,
        val btnSave: MaterialButton
    )

    private val colorCardHolders = mutableListOf<ColorCardHolder>()

    private val presetColors = listOf(
        "#f4a21d", "#ff9800", "#ff5722", "#f44336", "#e91e63",
        "#9c27b0", "#673ab7", "#3f51b5", "#2196f3", "#03a9f4",
        "#00bcd4", "#009688", "#4caf50", "#8bc34a", "#ffeb3b",
        "#ffffff", "#000000", "#607d8b"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_color)

        prefs = PrefsManager.get(this)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        setupColorCards()
        colorCardHolders.forEach { refreshColorCard(it) }
    }

    private fun setupColorCards() {
        bindColorCard(
            Constants.TargetColor.BANNER_TEXT,
            findViewById(R.id.cardColorBanner),
            getString(R.string.color_banner_title),
            getString(R.string.color_banner_desc)
        )
        bindColorCard(
            Constants.TargetColor.PUBLISH_TEXT,
            findViewById(R.id.cardColorPublish),
            getString(R.string.color_publish_title),
            getString(R.string.color_publish_desc)
        )
        bindColorCard(
            Constants.TargetColor.NAME_TEXT,
            findViewById(R.id.cardColorName),
            getString(R.string.color_name_title),
            getString(R.string.color_name_desc)
        )
    }

    private fun bindColorCard(
        target: Constants.TargetColor,
        cardRoot: View,
        title: String,
        desc: String
    ) {
        val holder = ColorCardHolder(
            target = target,
            root = cardRoot,
            tvTitle = cardRoot.findViewById(R.id.tvColorTitle),
            tvDesc = cardRoot.findViewById(R.id.tvColorDesc),
            switchColor = cardRoot.findViewById(R.id.switchColor),
            tvState = cardRoot.findViewById(R.id.tvColorState),
            llSwatches = cardRoot.findViewById(R.id.llColorSwatches),
            etColor = cardRoot.findViewById(R.id.etColor),
            btnSave = cardRoot.findViewById(R.id.btnSaveColor)
        )
        holder.tvTitle.text = title
        holder.tvDesc.text = desc
        buildColorSwatches(holder)

        holder.switchColor.setOnCheckedChangeListener { _, isChecked ->
            prefs.setColorEnabled(target, isChecked)
            updateColorStateVisual(holder)
        }
        holder.btnSave.setOnClickListener {
            val hex = holder.etColor.text?.toString()?.trim().orEmpty()
            if (hex.isEmpty()) {
                Toast.makeText(this, R.string.toast_save_failed, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.setColorValue(target, hex)
            if (!prefs.isColorEnabled(target)) {
                prefs.setColorEnabled(target, true)
                holder.switchColor.isChecked = true
            }
            refreshColorCard(holder)
            Toast.makeText(this, R.string.toast_saved_success, Toast.LENGTH_SHORT).show()
        }

        colorCardHolders.add(holder)
    }

    private fun buildColorSwatches(holder: ColorCardHolder) {
        val size = dp(36)
        for (hex in presetColors) {
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
                prefs.setColorValue(holder.target, hex)
                if (!prefs.isColorEnabled(holder.target)) {
                    prefs.setColorEnabled(holder.target, true)
                    holder.switchColor.isChecked = true
                }
                refreshColorCard(holder)
            }
            holder.llSwatches.addView(swatch)
        }
    }

    private fun refreshColorCard(holder: ColorCardHolder) {
        val enabled = prefs.isColorEnabled(holder.target)
        val value = prefs.getColorValue(holder.target)
        holder.switchColor.isChecked = enabled
        holder.etColor.setText(value ?: "")
        updateColorStateVisual(holder)
    }

    private fun updateColorStateVisual(holder: ColorCardHolder) {
        val value = prefs.getColorValue(holder.target)
        if (value.isNullOrEmpty()) {
            holder.tvState.text = getString(R.string.color_not_set)
            holder.tvState.setTextColor(getColor(R.color.md_on_surface_variant))
        } else {
            holder.tvState.text = getString(R.string.color_set, value)
            holder.tvState.setTextColor(getColor(R.color.md_success))
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
