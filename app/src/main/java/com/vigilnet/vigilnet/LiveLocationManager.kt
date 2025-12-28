package com.vigilnet.vigilnet

import com.google.firebase.database.FirebaseDatabase

object LiveLocationManager {
    private val dbRef = FirebaseDatabase.getInstance().getReference("live_locations")

    fun updateLocation(incidentId: String, lat: Double, lon: Double) {
        val payload = mapOf(
            "lat" to lat,
            "lon" to lon,
            "timestamp" to System.currentTimeMillis()
        )
        dbRef.child(incidentId).setValue(payload)
    }

    fun clearLocation(incidentId: String) {
        dbRef.child(incidentId).removeValue()
    }
}
