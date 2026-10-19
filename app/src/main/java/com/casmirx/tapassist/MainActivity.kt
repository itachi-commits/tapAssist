package com.casmirx.tapassist

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var showSwitch: Switch
    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("tap", MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }

        fun text(t: String, size: Float = 16f) = TextView(this).apply {
            text = t; textSize = size; setPadding(0, 32, 0, 8)
        }

        fun slider(title: String, key: String, min: Int, max: Int, def: Int, unit: String) {
            val label = text("")
            fun refresh(v: Int) { label.text = "$title: $v $unit" }
            val cur = prefs.getInt(key, def).coerceIn(min, max)
            refresh(cur)
            val bar = SeekBar(this)
            bar.max = max - min
            bar.progress = cur - min
            bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                    refresh(p + min)
                    prefs.edit().putInt(key, p + min).apply()
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
            root.addView(label)
            root.addView(bar)
        }

        root.addView(text("Tap Assist", 28f))
        root.addView(text(
            "1. Tap the button below and turn on Tap Assist in Accessibility.\n" +
            "2. Open your game. Tap the pencil button to move the blue dots onto your shoot button, then tap the check.\n" +
            "3. Tap FIRE once to start auto-tapping, tap STOP to stop.\n" +
            "4. Use the switch below or the notification button to show or hide the floating buttons."
        ))
        root.addView(Button(this).apply {
            text = "Open Accessibility settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })

        showSwitch = Switch(this).apply {
            text = "Show floating buttons"
            textSize = 16f
            setPadding(0, 32, 0, 8)
            isChecked = prefs.getBoolean("show", true)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("show", on).apply() }
        }
        root.addView(showSwitch)

        slider("Speed", "tps", 1, 20, 10, "taps/sec")
        slider("Tap points", "count", 1, 3, 1, "point(s)")

        root.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))
        root.addView(TextView(this).apply {
            text = "Made by Casmir X"
            textSize = 11f
            setTextColor(Color.GRAY)
            alpha = 0.6f
            gravity = Gravity.END
        }, LinearLayout.LayoutParams(-1, -2))

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        showSwitch.isChecked = prefs.getBoolean("show", true)
    }
}
