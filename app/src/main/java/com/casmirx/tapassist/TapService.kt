package com.casmirx.tapassist

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams as LP
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView

class TapService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var wm: WindowManager
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private val all = mutableListOf<View>()
    private val markers = mutableListOf<View>()
    private var editBtn: TextView? = null
    private var firing = false
    private var editMode = false

    // drag state
    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0

    private val tapper = object : Runnable {
        override fun run() {
            if (!firing) return
            tapAll()
            val tps = prefs.getInt("tps", 10).coerceIn(1, 20)
            handler.postDelayed(this, 1000L / tps)
        }
    }

    override fun onServiceConnected() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences("tap", MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(this)
        build()
    }

    override fun onSharedPreferenceChanged(p: SharedPreferences, key: String?) {
        if (key == "count") handler.post { build() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun bubble(label: String, color: Int) = TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        textSize = 16f
        alpha = 0.6f
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
    }

    private fun add(v: View, key: String, defX: Int, defY: Int, size: Int, touchable: Boolean) {
        var flags = LP.FLAG_NOT_FOCUSABLE or LP.FLAG_LAYOUT_IN_SCREEN or LP.FLAG_LAYOUT_NO_LIMITS
        if (!touchable) flags = flags or LP.FLAG_NOT_TOUCHABLE
        val p = LP(size, size, LP.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt(key + "x", defX)
            y = prefs.getInt(key + "y", defY)
        }
        wm.addView(v, p)
        all += v
    }

    private fun drag(v: View, key: String, e: MotionEvent): Boolean {
        val p = v.layoutParams as LP
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = p.x; startY = p.y }
            MotionEvent.ACTION_MOVE -> {
                p.x = startX + (e.rawX - downX).toInt()
                p.y = startY + (e.rawY - downY).toInt()
                wm.updateViewLayout(v, p)
            }
            MotionEvent.ACTION_UP -> prefs.edit().putInt(key + "x", p.x).putInt(key + "y", p.y).apply()
        }
        return true
    }

    private fun build() {
        all.forEach { runCatching { wm.removeView(it) } }
        all.clear(); markers.clear()
        editMode = false; firing = false

        val dm = resources.displayMetrics
        val big = dp(64)
        val small = dp(44)
        val count = prefs.getInt("count", 1).coerceIn(1, 3)

        // blue tap-point markers (pass touches through unless editing)
        for (i in 0 until count) {
            val m = bubble("${i + 1}", 0xFF2196F3.toInt())
            m.setOnTouchListener { v, e -> if (editMode) drag(v, "m$i", e) else false }
            add(m, "m$i", dm.widthPixels * 3 / 4 - i * dp(70), dm.heightPixels / 2, small, false)
            markers += m
        }

        // red hold-to-fire button
        val ctrl = bubble("FIRE", 0xFFE53935.toInt())
        ctrl.setOnTouchListener { v, e ->
            if (editMode) return@setOnTouchListener drag(v, "c", e)
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { firing = true; v.alpha = 1f; handler.post(tapper) }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { firing = false; v.alpha = 0.6f }
            }
            true
        }
        add(ctrl, "c", dm.widthPixels / 8, dm.heightPixels / 2, big, true)

        // pencil / check toggle for moving things around
        val edit = bubble("✎", 0xFF616161.toInt())
        edit.setOnClickListener { setEdit(!editMode) }
        add(edit, "e", dm.widthPixels / 8, dm.heightPixels / 2 - dp(60), small, true)
        editBtn = edit
    }

    private fun setEdit(on: Boolean) {
        editMode = on
        firing = false
        editBtn?.text = if (on) "✓" else "✎"
        markers.forEach {
            val p = it.layoutParams as LP
            p.flags = if (on) p.flags and LP.FLAG_NOT_TOUCHABLE.inv() else p.flags or LP.FLAG_NOT_TOUCHABLE
            wm.updateViewLayout(it, p)
        }
    }

    private fun tapAll() {
        val g = GestureDescription.Builder()
        markers.forEach {
            val loc = IntArray(2)
            it.getLocationOnScreen(loc)
            val path = Path().apply { moveTo(loc[0] + it.width / 2f, loc[1] + it.height / 2f) }
            g.addStroke(GestureDescription.StrokeDescription(path, 0, 20))
        }
        dispatchGesture(g.build(), null, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { firing = false }

    override fun onDestroy() {
        firing = false
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        all.forEach { runCatching { wm.removeView(it) } }
        super.onDestroy()
    }
}
