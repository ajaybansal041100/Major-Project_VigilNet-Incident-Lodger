package com.vigilnet.vigilnet

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.ktx.storage
import java.io.File

class AudioRecordService : Service() {

    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var incidentId: String? = null
    private var isRecording = false
    private var alreadyUploaded = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        incidentId = intent?.getStringExtra("incidentId") ?: "unknown_incident"

        startForegroundNotification()
        startRecording()

        return START_STICKY
    }

    private fun startForegroundNotification() {
        val channelId = "sos_audio_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "SOS Audio Recording",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Recording Audio")
            .setContentText("VigilNet emergency mode active")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()

        startForeground(101, notification)
    }

    private fun startRecording() {

        // 🔥 Runtime permission check
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("AudioRecordService", "RECORD_AUDIO permission NOT granted — aborting")
            stopSelf()
            return
        }

        if (isRecording) return

        try {
            val filename = "sos_audio_${System.currentTimeMillis()}.3gp"
            currentFile = File(filesDir, filename)

            recorder = MediaRecorder().apply {

                // better compatibility for Android 13+
                try {
                    setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                } catch (e: Exception) {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                }

                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(currentFile!!.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            Log.d("AudioRecordService", "Audio recording started → ${currentFile!!.absolutePath}")

        } catch (e: Exception) {
            Log.e("AudioRecordService", "Audio start FAILED", e)
            stopSelf()
        }
    }

    fun stopRecording() {
        stopAndUpload()
    }

    private fun stopAndUpload() {
        if (!isRecording) return

        try {
            recorder?.stop()
        } catch (e: Exception) {
            Log.w("AudioRecordService", "Stop error", e)
        }

        recorder?.release()
        recorder = null
        isRecording = false

        if (!alreadyUploaded && currentFile != null) {
            alreadyUploaded = true
            uploadFile(currentFile!!)
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun uploadFile(file: File) {

        val id = incidentId ?: return
        val storage = Firebase.storage.reference
        val remotePath = "incidents/$id/${file.name}"
        val fileUri = Uri.fromFile(file)

        Log.d("AudioRecordService", "Uploading audio → $remotePath")

        val metadata = StorageMetadata.Builder()
            .setCustomMetadata("incidentId", id)
            .setCustomMetadata("type", "audio")
            .build()

        storage.child(remotePath).putFile(fileUri, metadata)
            .addOnSuccessListener {
                storage.child(remotePath).downloadUrl
                    .addOnSuccessListener { url ->
                        saveUrlToFirestore(remotePath, url.toString())
                    }
            }
            .addOnFailureListener {
                Log.e("AudioRecordService", "Audio upload failed", it)
            }
    }

    private fun saveUrlToFirestore(path: String, url: String) {
        val db = FirebaseFirestore.getInstance()
        val id = incidentId ?: return

        val entry = mapOf(
            "path" to path,
            "url" to url,
            "uploadedAt" to Timestamp.now(),
            "type" to "audio"
        )

        db.collection("incidents").document(id)
            .update("audioUrls", FieldValue.arrayUnion(entry))
            .addOnSuccessListener {
                Log.d("AudioRecordService", "Stored Firestore audio metadata successfully")
            }
            .addOnFailureListener {
                Log.e("AudioRecordService", "Firestore save error", it)
            }
    }

    override fun onDestroy() {
        stopAndUpload()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
