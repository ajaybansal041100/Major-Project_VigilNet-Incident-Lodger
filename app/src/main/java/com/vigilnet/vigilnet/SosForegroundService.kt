package com.vigilnet.vigilnet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import android.app.Service
import android.os.IBinder
import android.os.PowerManager
import android.util.Log

class SosForegroundService : Service() {

    private val CHANNEL_ID = "sos_channel"
    private val NOTIFICATION_ID = 999
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        acquireWakeLock()

        val notification: Notification =
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Sending SOS…")
                .setContentText("Contacting emergency contacts")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        val phone = intent?.getStringExtra("phone")
        val lat = intent?.getDoubleExtra("lat", 0.0)
        val lon = intent?.getDoubleExtra("lon", 0.0)
        val incidentId = intent?.getStringExtra("incidentId")

        Log.d("SosForegroundService", "📩 onStartCommand: phone=$phone  incidentId=$incidentId")

        if (phone == null) {
            Log.e("SosForegroundService", "❗ No phone number provided")
            stopSelf()
            return START_NOT_STICKY
        }

        Thread {
            try {
                SmsHelper.sendSOSMessage(applicationContext, phone, lat, lon)
                Log.d("SosForegroundService", "✔ SMS sent to $phone")

            } catch (e: Exception) {
                Log.e("SosForegroundService", "❗ SMS sending FAILED to $phone", e)
            }

            stopSelf()
        }.start()

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        Log.d("SosForegroundService", "🛑 SERVICE STOPPED")
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val chan = NotificationChannel(
                CHANNEL_ID,
                "SOS Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(chan)
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "VigilNet::SosWakeLock"
        )
        wakeLock?.acquire(5 * 60 * 1000L) // 5 min max
    }

    private fun releaseWakeLock() {
        wakeLock?.release()
        wakeLock = null
    }
}
