package com.emon.lovelanguage

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0B2A5B"))
            setPadding(48, 48, 48, 48)
        }
        root.addView(TextView(this).apply {
            text = "Love Language"; textSize = 28f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "EMON ❤️ MASUMA"; textSize = 16f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; setPadding(0, 16, 0, 48)
        })
        root.addView(Button(this).apply {
            text = "বাবল চালু করুন"
            setOnClickListener { startBubble() }
        })
        setContentView(root)
    }

    private fun startBubble() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "\"Display over other apps\" পারমিশন অন করুন", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        val i = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        moveTaskToBack(true)
    }
}
