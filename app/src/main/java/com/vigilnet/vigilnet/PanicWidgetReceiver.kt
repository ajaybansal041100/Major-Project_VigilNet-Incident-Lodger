package com.vigilnet.vigilnet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.widget.Toast

class PanicWidgetReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        Toast.makeText(context, "SOS Triggered!", Toast.LENGTH_SHORT).show()

        // Get last known location
        val lastLocation: Location? = LastLocationStore.getLastLocation(context)

        // Trigger SOS for all saved contacts
        SosEngine.startSOS(context, null, lastLocation)
    }
}
