package com.vigilnet.vigilnet

import android.content.Context
import android.location.Location
import android.widget.Toast
import com.squareup.seismic.ShakeDetector

class ShakeListener(private val context: Context) : ShakeDetector.Listener {

    private var shakeCount = 0
    private var lastShakeTime = 0L

    override fun hearShake() {

        val now = System.currentTimeMillis()

        if (now - lastShakeTime > 2000) {
            shakeCount = 0
        }

        shakeCount++
        lastShakeTime = now

        // require 4 strong shakes
        if (shakeCount >= 5) {
            shakeCount = 0
            Toast.makeText(context, "Shake SOS activated!", Toast.LENGTH_SHORT).show()

            val lastLocation: Location? = LastLocationStore.getLastLocation(context)
            SosEngine.startSOS(context, null, lastLocation)
        }
    }
}
