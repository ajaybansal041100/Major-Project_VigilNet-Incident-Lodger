package com.vigilnet.vigilnet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.widget.Toast
import androidx.core.content.ContextCompat

class SosWidgetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {

        if (intent.action == "SOS_TRIGGER") {

            Toast.makeText(context, "🚨 SOS Activated!", Toast.LENGTH_SHORT).show()

            val lastLocation: Location? =
                LastLocationStore.getLastLocation(context)

            // SMS + backend event
            SosEngine.startSOS(context, null, lastLocation)

            // START AUDIO RECORDING SERVICE (background)
            val audioIntent = Intent(context, AudioRecordService::class.java)
            ContextCompat.startForegroundService(context, audioIntent)

            // START VIDEO RECORD ACTIVITY (visible UI)
            val videoIntent = Intent(context, VideoRecordActivity::class.java)
            videoIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            videoIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            videoIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(videoIntent)
        }
    }
}
