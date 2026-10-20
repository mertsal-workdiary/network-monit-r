package com.example.networkmonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class MonitorService : Service() {
    private lateinit var executor: ScheduledExecutorService

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().putBoolean(KEY_RUNNING, true).apply()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, notification("İzleme başlatılıyor…"))

        executor = Executors.newSingleThreadScheduledExecutor()
        executor.scheduleWithFixedDelay({ monitorOnce() }, 0, INTERVAL_SECONDS, TimeUnit.SECONDS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        if (::executor.isInitialized) executor.shutdownNow()
        isRunning = false
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().putBoolean(KEY_RUNNING, false).apply()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun monitorOnce() {
        try {
            val network = NetworkProbe.networkType(this)
            val gateway = NetworkProbe.gatewayAddress(this)
            val gatewayResult = if (gateway == null) {
                ProbeResult(false, null, "Varsayılan ağ geçidi bulunamadı")
            } else {
                NetworkProbe.run(gateway, publicTarget = false)
            }
            LogStore.append(
                this, "Gateway", gateway ?: "-", gatewayResult.success,
                gatewayResult.latencyMs, network, gatewayResult.error
            )

            val internetResult = NetworkProbe.run(CLOUDFLARE_IP, publicTarget = true)
            LogStore.append(
                this, "Cloudflare", CLOUDFLARE_IP, internetResult.success,
                internetResult.latencyMs, network, internetResult.error
            )

            val gatewayText = if (gatewayResult.success) "Gateway ✓" else "Gateway ✗"
            val internetText = if (internetResult.success) "1.1.1.1 ✓" else "1.1.1.1 ✗"
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, notification("$gatewayText  •  $internetText  •  $network"))
        } catch (exception: Exception) {
            try {
                LogStore.append(
                    this, "Sistem", "-", false, null,
                    runCatching { NetworkProbe.networkType(this) }.getOrDefault("Bilinmiyor"),
                    exception.message ?: exception.javaClass.simpleName
                )
            } catch (_: Exception) {
                // Bir kayıt hatasının zamanlanmış izleme döngüsünü kalıcı olarak durdurmasını önle.
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Ağ izleme",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Sürekli ağ ölçümü durumu"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun notification(text: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Ağ İzleyici çalışıyor")
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        const val PREFERENCES = "monitor_state"
        const val KEY_RUNNING = "running"
        private const val CHANNEL_ID = "network_monitor"
        private const val NOTIFICATION_ID = 1001
        private const val INTERVAL_SECONDS = 10L
        private const val CLOUDFLARE_IP = "1.1.1.1"

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
