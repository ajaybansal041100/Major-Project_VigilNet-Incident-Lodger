package com.vigilnet.vigilnet

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.firebase.firestore.FirebaseFirestore

class LocationTrackingService : Service() {

    private lateinit var fused: FusedLocationProviderClient
    private lateinit var request: LocationRequest

    private var incidentId: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        incidentId = intent?.getStringExtra("incidentId")

        fused = LocationServices.getFusedLocationProviderClient(this)

        request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            4000L
        ).build()

        startForegroundNotification()
        startLocationUpdates()

        return START_STICKY
    }

    private fun startForegroundNotification() {
        val channelId = "location_tracking_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "SOS Live Location",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }

        val n: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("VigilNet — Live Location Sharing")
            .setContentText("Updating location in real time")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()

        startForeground(404, n)
    }

    @Suppress("MissingPermission")
    private fun startLocationUpdates() {

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("LocationTracking", "No location permission!")
            return
        }

        fused.requestLocationUpdates(request, callback, null)
    }

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc: Location = result.lastLocation ?: return
            updateBothDatabases(loc.latitude, loc.longitude)
        }
    }

    private fun updateBothDatabases(lat: Double, lon: Double) {
        val id = incidentId ?: return

        // 1) update Realtime DB
        LiveLocationManager.updateLocation(id, lat, lon)

        // 2) ALSO update Firestore
        FirebaseFirestore.getInstance()
            .collection("incidents")
            .document(id)
            .update(
                mapOf(
                    "latitude" to lat,
                    "longitude" to lon
                )
            )
    }

    override fun onDestroy() {
        fused.removeLocationUpdates(callback)
        incidentId?.let { LiveLocationManager.clearLocation(it) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
