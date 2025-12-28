package com.vigilnet.vigilnet

import android.net.Uri
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.*

object FirebaseUploader {

    private val storage = FirebaseStorage.getInstance().reference
    private val firestore = FirebaseFirestore.getInstance()

    fun uploadFile(
        fileUri: Uri,
        type: String,            // "audio" or "video"
        latitude: Double?,
        longitude: Double?,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val fileName = "${type}_${System.currentTimeMillis()}.mp4"

        val uploadRef = storage.child("recordings/$fileName")

        uploadRef.putFile(fileUri)
            .addOnSuccessListener {
                uploadRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                    saveMetadata(downloadUrl.toString(), type, latitude, longitude)
                    onComplete(true, downloadUrl.toString())
                }
            }
            .addOnFailureListener {
                onComplete(false, null)
            }
    }

    private fun saveMetadata(url: String, type: String, lat: Double?, lon: Double?) {
        val data = hashMapOf(
            "url" to url,
            "type" to type,
            "timestamp" to Date(),
            "latitude" to lat,
            "longitude" to lon,
            "device" to android.os.Build.MODEL
        )

        firestore.collection("Recordings").add(data)
    }

    fun logSOS(lat: Double?, lon: Double?) {
        val data = hashMapOf(
            "event" to "SOS Triggered",
            "timestamp" to Date(),
            "latitude" to lat,
            "longitude" to lon,
            "device" to android.os.Build.MODEL
        )
        firestore.collection("Events").add(data)
    }
}
