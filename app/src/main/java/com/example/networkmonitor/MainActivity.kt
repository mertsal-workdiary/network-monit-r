package com.example.networkmonitor

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : Activity() {
    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private val handler = Handler(Looper.getMainLooper())

    private val refreshTask = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 2_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshTask)
    }

    override fun onPause() {
        handler.removeCallbacks(refreshTask)
        super.onPause()
    }

    @Deprecated("Deprecated in Android API; kept for broad compatibility with ACTION_CREATE_DOCUMENT")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == EXPORT_REQUEST && resultCode == RESULT_OK) {
            data?.data?.let { exportTo(it) }
        }
    }

    private fun buildUi(): ScrollView {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }

        root.addView(TextView(this).apply {
            text = "Ağ İzleyici"
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(13, 71, 161))
        })

        root.addView(TextView(this).apply {
            text = "Gateway ve 1.1.1.1 bağlantısını arka planda her 10 saniyede kontrol eder."
            textSize = 16f
            setPadding(0, dp(8), 0, dp(18))
        })

        statusText = TextView(this).apply {
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            gravity = Gravity.CENTER
        }
        root.addView(statusText, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val startButton = Button(this).apply {
            text = "İzlemeyi Başlat"
            setOnClickListener { startMonitoring() }
        }
        root.addView(startButton, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val stopButton = Button(this).apply {
            text = "İzlemeyi Durdur"
            setOnClickListener {
                stopService(Intent(this@MainActivity, MonitorService::class.java))
                getSharedPreferences(MonitorService.PREFERENCES, MODE_PRIVATE)
                    .edit().putBoolean(MonitorService.KEY_RUNNING, false).apply()
                refresh()
            }
        }
        root.addView(stopButton, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val exportButton = Button(this).apply {
            text = "CSV Dışa Aktar"
            setOnClickListener { chooseExportLocation() }
        }
        root.addView(exportButton, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        root.addView(TextView(this).apply {
            text = "Son ölçümler"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(22), 0, dp(8))
        })

        logText = TextView(this).apply {
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setBackgroundColor(Color.rgb(245, 245, 245))
        }
        root.addView(logText, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        return ScrollView(this).apply { addView(root) }
    }

    private fun startMonitoring() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_REQUEST)
        }
        startForegroundService(Intent(this, MonitorService::class.java))
        Toast.makeText(this, "Ağ izleme başladı", Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_REQUEST)
        }
    }

    private fun refresh() {
        val running = MonitorService.isRunning ||
            getSharedPreferences(MonitorService.PREFERENCES, MODE_PRIVATE)
                .getBoolean(MonitorService.KEY_RUNNING, false)
        statusText.text = if (running) "● İZLEME AKTİF" else "○ İZLEME DURDU"
        statusText.setTextColor(if (running) Color.rgb(27, 94, 32) else Color.rgb(183, 28, 28))
        logText.text = LogStore.latest(this)
    }

    private fun chooseExportLocation() {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, "network_monitor_$stamp.csv")
        }
        startActivityForResult(intent, EXPORT_REQUEST)
    }

    private fun exportTo(uri: Uri) {
        try {
            val source = LogStore.ensureFile(this)
            contentResolver.openOutputStream(uri, "w")?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Dosya açılamadı")
            Toast.makeText(this, "CSV kaydedildi", Toast.LENGTH_LONG).show()
        } catch (exception: Exception) {
            Toast.makeText(this, "Dışa aktarma başarısız: ${exception.message}", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val EXPORT_REQUEST = 2001
        private const val NOTIFICATION_REQUEST = 2002
    }
}
