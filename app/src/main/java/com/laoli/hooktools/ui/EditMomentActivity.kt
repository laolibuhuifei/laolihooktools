package com.laoli.hooktools.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.laoli.hooktools.R
import com.laoli.hooktools.util.EditedMomentStore

/**
 * 动态编辑页。
 *
 * 由好友圈 Hook 注入的"编辑"按钮通过显式 Intent 启动(跨进程)。
 * 支持编辑文字内容、设置文字颜色、开关下划线,保存后写入
 * EditedMomentStore(文件),好友圈进程读取并覆盖显示。
 */
class EditMomentActivity : AppCompatActivity() {

    private lateinit var etText: EditText
    private lateinit var etLikeCount: EditText
    private lateinit var etName: EditText
    private lateinit var switchUnderline: MaterialSwitch
    private lateinit var btnSave: MaterialButton
    private lateinit var btnCancel: MaterialButton
    private lateinit var colorContainer: LinearLayout
    private lateinit var textEditContainer: LinearLayout

    private var momentId: String = ""
    private var selectedColor: Int = Color.parseColor("#CCFFFFFF")
    private var isMedia: Boolean = false

    /** 预设颜色(第一个为原色 #CCFFFFFF) */
    private val presetColors = intArrayOf(
        Color.parseColor("#CCFFFFFF"),
        Color.WHITE,
        Color.BLACK,
        Color.RED,
        Color.parseColor("#FFA500"),
        Color.YELLOW,
        Color.GREEN,
        Color.parseColor("#3399FF"),
        Color.parseColor("#FF69B4")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_moment)

        etText = findViewById(R.id.etEditText)
        etLikeCount = findViewById(R.id.etLikeCount)
        etName = findViewById(R.id.etName)
        switchUnderline = findViewById(R.id.switchEditUnderline)
        btnSave = findViewById(R.id.btnEditSave)
        btnCancel = findViewById(R.id.btnEditCancel)
        colorContainer = findViewById(R.id.colorContainer)
        textEditContainer = findViewById(R.id.textEditContainer)

        momentId = intent.getStringExtra("momentId") ?: ""
        val text = intent.getStringExtra("text") ?: ""
        val color = intent.getIntExtra("color", selectedColor)
        val underline = intent.getBooleanExtra("underline", false)
        val likeCount = intent.getIntExtra("likeCount", -1)
        val name = intent.getStringExtra("name") ?: ""
        isMedia = intent.getBooleanExtra("isMedia", false)

        selectedColor = color
        etText.setText(text)
        etText.setSelection(etText.text.length)
        etText.setTextColor(selectedColor)
        switchUnderline.isChecked = underline
        if (likeCount >= 0) {
            etLikeCount.setText(likeCount.toString())
        }
        etName.setText(name)
        if (isMedia) {
            textEditContainer.visibility = View.GONE
        }

        buildColorSwatches()

        btnSave.setOnClickListener { save() }
        btnCancel.setOnClickListener { finish() }
    }

    /** 构建颜色色块(圆形,选中带高亮边框) */
    private fun buildColorSwatches() {
        colorContainer.removeAllViews()
        val size = dp(40)
        val margin = dp(8)
        for (c in presetColors) {
            val swatch = View(this)
            val lp = LinearLayout.LayoutParams(size, size)
            lp.marginEnd = margin
            swatch.layoutParams = lp
            swatch.tag = c
            swatch.setOnClickListener {
                selectedColor = c
                etText.setTextColor(c)
                updateSwatchSelection()
            }
            colorContainer.addView(swatch)
        }
        updateSwatchSelection()
    }

    private fun updateSwatchSelection() {
        for (i in 0 until colorContainer.childCount) {
            val v = colorContainer.getChildAt(i)
            val c = v.tag as? Int ?: continue
            v.background = makeSwatchDrawable(c, c == selectedColor)
        }
    }

    private fun makeSwatchDrawable(color: Int, selected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            if (selected) {
                setStroke(dp(3), Color.parseColor("#FFD54F"))
            } else {
                setStroke(dp(1), Color.parseColor("#44FFFFFF"))
            }
        }
    }

    private fun save() {
        if (momentId.isEmpty()) {
            Toast.makeText(this, "动态信息缺失，无法保存", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        // 媒体动态(纯图片/视频)不修改文字、颜色、下划线
        val newText: String?
        val colorToSave: Int?
        val underlineToSave: Boolean
        if (isMedia) {
            newText = null
            colorToSave = null
            underlineToSave = false
        } else {
            val t = etText.text.toString().trim()
            if (t.isEmpty()) {
                Toast.makeText(this, "内容不能为空", Toast.LENGTH_SHORT).show()
                return
            }
            newText = t
            colorToSave = selectedColor
            underlineToSave = switchUnderline.isChecked
        }
        val likeStr = etLikeCount.text.toString().trim()
        val likeCount = if (likeStr.isEmpty()) {
            null
        } else {
            val parsed = likeStr.toIntOrNull()
            if (parsed == null || parsed < 0) {
                Toast.makeText(this, "点赞数量格式错误", Toast.LENGTH_SHORT).show()
                return
            }
            parsed
        }
        val nameStr = etName.text.toString().trim()
        val name = if (nameStr.isEmpty()) null else nameStr
        EditedMomentStore.save(momentId, newText, colorToSave, underlineToSave, likeCount, name)
        Toast.makeText(this, "已保存，返回好友圈刷新后生效", Toast.LENGTH_LONG).show()
        finish()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
