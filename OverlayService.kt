package com.emon.lovelanguage

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var bubble: TextView? = null
    private var panel: View? = null
    private var encode = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundCompat()
        showBubble()
    }

    override fun onDestroy() {
        bubble?.let { runCatching { wm.removeView(it) } }
        panel?.let { runCatching { wm.removeView(it) } }
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val chId = "ll_bubble"
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(chId, "Bubble", NotificationManager.IMPORTANCE_MIN))
        }
        val n = (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, chId) else Notification.Builder(this))
            .setContentTitle("Love Language চালু আছে")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(1, n)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun overlayType() =
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun round(color: Int, r: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(r).toFloat()
    }

    // ---------- floating bubble ----------
    private fun showBubble() {
        val size = dp(62)
        val tv = TextView(this).apply {
            text = "💬"; textSize = 28f; gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(Color.parseColor("#1D6BFF")); setStroke(dp(3), Color.WHITE)
            }
        }
        val lp = WindowManager.LayoutParams(
            size, size, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = resources.displayMetrics.widthPixels - size - dp(8); y = dp(240) }

        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
        tv.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - sx; val dy = e.rawY - sy
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                    if (moved) { lp.x = (ox + dx).toInt(); lp.y = (oy + dy).toInt(); wm.updateViewLayout(tv, lp) }
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        val sw = resources.displayMetrics.widthPixels
                        lp.x = if (lp.x + size / 2 < sw / 2) dp(8) else sw - size - dp(8)
                        wm.updateViewLayout(tv, lp)
                    } else showPanel()
                }
            }
            true
        }
        wm.addView(tv, lp)
        bubble = tv
    }

    // ---------- chat panel ----------
    private fun showPanel() {
        bubble?.visibility = View.GONE
        val dm = resources.displayMetrics
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = round(Color.parseColor("#0B2A5B"), 20)
            setPadding(dp(12), dp(10), dp(12), dp(8))
        }

        // top bar
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this).apply {
            text = "Love Language"; textSize = 18f; setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val modeBtn = chip(if (encode) "এনকোড" else "ডিকোড")
        modeBtn.setOnClickListener { encode = !encode; modeBtn.text = if (encode) "এনকোড" else "ডিকোড" }
        val minBtn = chip("–").apply { setOnClickListener { hidePanel() } }
        val closeBtn = chip("✕").apply { setOnClickListener { stopSelf() } }
        top.addView(modeBtn); top.addView(minBtn); top.addView(closeBtn)
        root.addView(top)

        // chat area
        val chatBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(8)) }
        val scroll = ScrollView(this).apply { addView(chatBox) }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // input row
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val input = EditText(this).apply {
            hint = "মেসেজ লিখুন…"; setTextColor(Color.parseColor("#0A1F44")); setHintTextColor(Color.GRAY)
            background = round(Color.WHITE, 22); setPadding(dp(14), dp(8), dp(14), dp(8)); maxLines = 4
        }
        val sendBtn = TextView(this).apply {
            text = "➤"; textSize = 20f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#1D6BFF")) }
        }
        row.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(sendBtn, LinearLayout.LayoutParams(dp(42), dp(42)).apply { leftMargin = dp(8) })
        root.addView(row)

        root.addView(TextView(this).apply {
            text = "EMON ❤️ MASUMA"; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD); setPadding(0, dp(8), 0, 0)
        })

        val maxBubble = (dm.widthPixels * 0.62).toInt()
        fun addBubble(text: String, mine: Boolean) {
            val t = TextView(this).apply {
                this.text = text; textSize = 16f; maxWidth = maxBubble
                setTextColor(if (mine) Color.WHITE else Color.parseColor("#0A1F44"))
                background = round(if (mine) Color.parseColor("#1D6BFF") else Color.parseColor("#E4ECFB"), 18)
                setPadding(dp(13), dp(8), dp(13), dp(8))
                if (!mine) setOnClickListener {
                    (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("love", text))
                    Toast.makeText(this@OverlayService, "কপি হয়েছে ✓", Toast.LENGTH_SHORT).show()
                }
            }
            chatBox.addView(t, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = if (mine) Gravity.END else Gravity.START
                topMargin = dp(3); bottomMargin = dp(3)
            })
        }

        sendBtn.setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            addBubble(text, true)
            addBubble(convert(text), false)
            input.setText("")
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }

        val lp = WindowManager.LayoutParams(
            dm.widthPixels - dp(20), (dm.heightPixels * 0.55).toInt(), overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = dp(40)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }
        wm.addView(root, lp)
        panel = root
    }

    private fun chip(label: String) = TextView(this).apply {
        text = label; setTextColor(Color.WHITE); textSize = 14f; gravity = Gravity.CENTER
        background = round(Color.parseColor("#2A58A8"), 16); setPadding(dp(12), dp(6), dp(12), dp(6))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { leftMargin = dp(6) }
    }

    private fun hidePanel() {
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null
        bubble?.visibility = View.VISIBLE
    }

    // ---------- converter (same rule as the web app) ----------
    private fun convert(text: String): String {
        val dir = if (encode) 1 else -1
        return text.map { c ->
            val base = when (c) { in 'A'..'Z' -> 'A'; in 'a'..'z' -> 'a'; else -> return@map c }
            val n = c - base + dir
            if (n < 0 || n > 25) c else base + n
        }.joinToString("")
    }
}
