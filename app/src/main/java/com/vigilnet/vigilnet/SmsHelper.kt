package com.vigilnet.vigilnet

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat

object SmsHelper {

    fun sendSOSMessage(
        ctx: Context,
        phone: String,
        lat: Double?,
        lon: Double?
    ) {
        Log.d("SOS_SMS", "sendSOSMessage() called for $phone with lat=$lat lon=$lon")

        val appContext = App.instance

        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("SOS_SMS", "❗ Missing SEND_SMS permission — cannot send SMS")
            return
        }

        val mapsLink =
            if (lat != null && lon != null && lat != 0.0 && lon != 0.0)
                "https://maps.google.com/?q=$lat,$lon"
            else
                "Location unavailable"

        val message =
            "🚨 EMERGENCY — SOS ALERT\n" +
                    "I am in danger and need urgent help.\n" +
                    "📍 My location: $mapsLink"

        try {
            val sms = SmsManager.getDefault()
            sms.sendMultipartTextMessage(phone, null, sms.divideMessage(message), null, null)
            Log.d("SOS_SMS", "✔ SMS sent to $phone — INCLUDING LOCATION")

        } catch (e: Exception) {
            Log.e("SOS_SMS", "❗ SMS sending FAILED to $phone", e)
        }
    }
}
