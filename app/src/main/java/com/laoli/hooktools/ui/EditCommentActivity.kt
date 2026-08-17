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
import com.laoli.hooktools.util.EditedCommentStore

/**
 * 评论编辑页。
 *
 * 由好友圈 Hook 注入的"修改评论"通过显式 Intent 启动(跨进程)。
 * 支持修改评论颜色、开关下划线、修改评论者名称,保存后写入
 * EditedCommentStore(文件),好友圈进程读取并覆盖显示。
 */
class EditCommentActivity : AppCompatActivity() {

    private lateinit var etText: EditText
    private lateinit var etName: EditText
    private lateinit var switchUnderline: MaterialSwitch
    private lateinit var btnSave: MaterialButton
    private lateinit var btnCancel: MaterialButton
    private lateinit var colorContainer: LinearLayout

    private var commentId: String = ""
    private var selectedColor: Int = Color.parseColor("#D9D9D9")

    /** 预设颜色(第一个为原色 #D9D9D9) */
    private val presetColors = intArrayOf(
        Color.parseColor("#D9D9D9"),
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
        setContentView(R.layout.activity_edit_comment)

        etText = findViewById(R.id.etCommentText)
        etName = findViewById(R.id.etCommentName)
        switchUnderline = findViewById(R.id.switchCommentUnderline)
        btnSave = findViewById(R.id.btnCommentSave)
        btnCancel = findViewById(R.id.btnCommentCancel)
        colorContainer = findViewById(R.id.commentColorContainer)

        commentId = intent.getStringExtra("commentId") ?: ""
        val text = intent.getStringExtra("text") ?: ""
        val name = intent.getStringExtra("name") ?: ""
        val color = intent.getIntExtra("color", selectedColor)
        val underline = intent.getBooleanExtra("underline", false)

        selectedColor = color
        etText.setText(text)
        etText.setSelection(etText.text.length)
        etName.setText(name)
        etName.setSelection(etName.text.length)
        switchUnderline.isChecked = underline

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
        if (commentId.isEmpty()) {
            Toast.makeText(this, "评论信息缺失，无法保存", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val textStr = etText.text.toString().trim()
        val text = if (textStr.isEmpty()) null else textStr
        val nameStr = etName.text.toString().trim()
        val name = if (nameStr.isEmpty()) null else nameStr
        EditedCommentStore.save(commentId, text, selectedColor, switchUnderline.isChecked, name)
        Toast.makeText(this, "已保存，返回好友圈刷新后生效", Toast.LENGTH_LONG).show()
        finish()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
