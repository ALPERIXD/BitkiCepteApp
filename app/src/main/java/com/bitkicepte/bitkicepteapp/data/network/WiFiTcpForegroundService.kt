package com.bitkicepte.bitkicepteapp.data.network

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.bitkicepte.bitkicepteapp.BitkiCepteApp
import com.bitkicepte.bitkicepteapp.R
import com.bitkicepte.bitkicepteapp.ui.MainActivity

/**
 * TCP bağlantısını arka planda sürdüren ForegroundService.
 *
 * Başlatmak:
 *   WiFiTcpForegroundService.start(context, ip, port)
 * Durdurmak:
 *   WiFiTcpForegroundService.stop(context)
 */
class WiFiTcpForegroundService : Service() {

    companion object {
        const val ACTION_START  = "bitkicepte.START_TCP"
        const val ACTION_STOP   = "bitkicepte.STOP_TCP"
        const val EXTRA_HOST    = "host"
        const val EXTRA_PORT    = "port"
        private const val NOTIF_ID = 1001

        fun start(context: android.content.Context, host: String, port: Int) {
            val intent = Intent(context, WiFiTcpForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_HOST, host)
                putExtra(EXTRA_PORT, port)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: android.content.Context) {
            context.startService(
                Intent(context, WiFiTcpForegroundService::class.java).apply {
                    action = ACTION_STOP
                }
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIF_ID, buildNotification())
                // Bağlantı yönetimi SharedViewModel/Repository'de zaten yapılıyor.
                // Bu servis sadece sistemin işlemi öldürmesini engeller.
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, BitkiCepteApp.NOTIF_CHANNEL_ID)
            .setContentTitle("BitkiCepte")
            .setContentText("ESP32 bağlantısı aktif")
            .setSmallIcon(R.drawable.ic_nav_eco)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }
}
