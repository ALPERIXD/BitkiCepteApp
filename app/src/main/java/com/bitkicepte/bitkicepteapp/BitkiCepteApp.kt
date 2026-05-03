package com.bitkicepte.bitkicepteapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.bitkicepte.bitkicepteapp.data.local.database.AppDatabase

class BitkiCepteApp : Application() {
    companion object { const val NOTIF_CHANNEL_ID = "bitkicepte_service" }

    lateinit var database: AppDatabase private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(NOTIF_CHANNEL_ID, "BitkiCepte Servis", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }
}
