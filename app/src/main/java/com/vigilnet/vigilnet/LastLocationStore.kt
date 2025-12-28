package com.vigilnet.vigilnet

import android.content.Context
import android.location.Location

/**
 * Simple helper to store & retrieve last known location.
 * Used by:
 *  - Panic Widget
 *  - Shake Detector
 *  - Background SOS triggers
 */

object LastLocationStore {

    private const val PREF_NAME = "last_location"
    private const val KEY_LAT = "lat"
    private const val KEY_LON = "lon"
    private const val KEY_TIME = "time"

    /** Save last known location */
    fun saveLocation(context: Context, location: Location) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat(KEY_LAT, location.latitude.toFloat())
            .putFloat(KEY_LON, location.longitude.toFloat())
            .putLong(KEY_TIME, location.time)
            .apply()
    }

    /** Retrieve last known location */
    fun getLastLocation(context: Context): Location? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        if (!prefs.contains(KEY_LAT) || !prefs.contains(KEY_LON)) return null

        val lat = prefs.getFloat(KEY_LAT, 0f).toDouble()
        val lon = prefs.getFloat(KEY_LON, 0f).toDouble()
        val time = prefs.getLong(KEY_TIME, System.currentTimeMillis())

        val location = Location("LastLocationStore")
        location.latitude = lat
        location.longitude = lon
        location.time = time

        return location
    }

    /** Clear stored location (optional) */
    fun clear(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
