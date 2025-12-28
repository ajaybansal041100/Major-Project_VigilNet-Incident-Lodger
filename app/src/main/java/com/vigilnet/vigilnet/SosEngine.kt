package com.vigilnet.vigilnet

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

object SosEngine {

    private const val TAG = "SosEngine"

    fun startSOS(context: Context, emergencyPhone: String?, location: Location?) {

        val appContext = context.applicationContext

        val contacts = EmergencyContactManager.getContacts(appContext)
        if (contacts.isEmpty()) {
            Log.w(TAG, "❗ No emergency contacts set")
            return
        }

        // ALWAYS use last known REAL GPS location
        val realLocation = getLastKnownLocation()
        val lat = realLocation?.latitude ?: 0.0
        val lon = realLocation?.longitude ?: 0.0

        // incidentId created ONCE
        val incidentId = UUID.randomUUID().toString()

        Log.d(TAG, "🚨 SOS STARTED — IncidentID = $incidentId")

        FirebaseFirestore.getInstance()
            .collection("incidents")
            .document(incidentId)
            .set(
                mapOf(
                    "incidentId" to incidentId,
                    "timestamp" to Timestamp.now(),
                    "latitude" to lat,
                    "longitude" to lon,
                    "type" to "SOS",
                    "status" to "active"
                )
            )

        // SEND SMS TO ALL CONTACTS
        for (contact in contacts) {
            val serviceIntent = Intent(appContext, SosForegroundService::class.java).apply {
                putExtra("phone", contact.phone)
                putExtra("lat", lat)
                putExtra("lon", lon)
                putExtra("incidentId", incidentId)
            }
            ContextCompat.startForegroundService(appContext, serviceIntent)
        }

        // ALWAYS START AUDIO RECORDING (even from widget)
        ContextCompat.startForegroundService(
            appContext,
            Intent(appContext, AudioRecordService::class.java)
                .putExtra("incidentId", incidentId)
        )

        // ALWAYS START VIDEO RECORDING IF PERMISSION GRANTED
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {

            val intent = Intent(appContext, VideoRecordActivity::class.java)
                .putExtra("incidentId", incidentId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

            appContext.startActivity(intent)
            Log.d(TAG, "🎥 Video recording started (from widget or app)")
        } else {
            Log.e(TAG, "❗ CAMERA permission missing — can’t start recording")
        }

        // ALWAYS START LOCATION UPDATES
        ContextCompat.startForegroundService(
            appContext,
            Intent(appContext, LocationTrackingService::class.java)
                .putExtra("incidentId", incidentId)
        )
    }
}
