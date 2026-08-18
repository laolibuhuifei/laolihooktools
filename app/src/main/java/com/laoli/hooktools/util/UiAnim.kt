package com.laoli.hooktools.util

import android.view.MotionEvent
import android.view.View

/**
 * 给可点击控件添加按压缩放动画(不消费事件,不影响原有点击逻辑)。
 */
fun View.addPressScale(scale: Float = 0.94f) {
    setOnTouchListener { _, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animate().cancel()
                animate().scaleX(scale).scaleY(scale).setDuration(90L).start()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                animate().cancel()
                animate().scaleX(1f).scaleY(1f).setDuration(120L).start()
            }
        }
        false
    }
}
