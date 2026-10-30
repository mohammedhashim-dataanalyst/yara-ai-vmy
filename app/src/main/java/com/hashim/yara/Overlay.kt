package com.hashim.yara

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

object Overlay {
    private var view: TextView? = null
    private val h = Handler(Looper.getMainLooper())

    fun show(c: Context, text: String) {
        val ctx = c.applicationContext
        if (!Store.flag("overlay", false) || !Settings.canDrawOverlays(ctx)) return
        h.post {
            hide(ctx)
            val tv = TextView(ctx).apply {
                this.text = "💗 " + text.replace(Regex("\\[\\[MEM:.*?\\]\\]"), "")
                setTextColor(0xFFFFFFFF.toInt()); textSize = 15f; setPadding(36, 22, 36, 22)
                background = GradientDrawable().apply { setColor(0xE6C2185B.toInt()); cornerRadius = 44f }
            }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP; y = 140 }
            try {
                (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).addView(tv, lp)
                view = tv
                h.postDelayed({ hide(ctx) }, 7000)
            } catch (e: Exception) {}
        }
    }

    fun hide(c: Context) {
        view?.let { try { (c.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(it) } catch (e: Exception) {} }
        view = null
    }
}
