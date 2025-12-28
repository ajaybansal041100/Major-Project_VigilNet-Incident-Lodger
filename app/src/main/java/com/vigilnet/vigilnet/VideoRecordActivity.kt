package com.vigilnet.vigilnet

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.io.File

class VideoRecordActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recordingHandle: Recording? = null

    private var incidentId: String = ""

    private var hasAudio = false

    private val segmentHandler = Handler(Looper.getMainLooper())
    private var isActive = true

    private val SEGMENT_DURATION_MS = 60_000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_record)

        previewView = findViewById(R.id.previewView)

        // ✅ Always read and require valid incidentId
        incidentId = intent.getStringExtra("incidentId") ?: ""

        if (incidentId.isEmpty() || incidentId == "unknown" || incidentId == "unknown_incident") {
            Log.e("VideoRecord", "❗ INVALID incidentId passed — FIX REQUIRED")
            finish()
            return
        }

        Log.d("VideoRecord", "🎥 Video recorder started for IncidentID = $incidentId")

        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        enableImmersiveMode()

        requestPermissions.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            )
        )
    }

    private fun enableImmersiveMode() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->

            val camOK = perms[Manifest.permission.CAMERA] ?: false
            hasAudio = perms[Manifest.permission.RECORD_AUDIO] ?: false

            if (!camOK) {
                Log.e("VideoRecord", "❗ Camera permission denied — cannot continue")
                finish()
                return@registerForActivityResult
            }
            startCamera()
        }

    private fun startCamera() {

        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({

            val provider = providerFuture.get()

            val recorder = Recorder.Builder()
                .setExecutor(ContextCompat.getMainExecutor(this))
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()

            videoCapture = VideoCapture.withOutput(recorder)

            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)

            provider.unbindAll()
            provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                videoCapture
            )

            startNewSegment()

        }, ContextCompat.getMainExecutor(this))
    }

    private fun startNewSegment() {

        val file = File(filesDir, "sos_video_${System.currentTimeMillis()}.mp4")
        val outputOps = FileOutputOptions.Builder(file).build()

        Log.d("VideoRecord", "▶ Starting new video segment: ${file.name}")

        recordingHandle = videoCapture!!.output
            .prepareRecording(this, outputOps)
            .apply { if (hasAudio) withAudioEnabled() }
            .start(ContextCompat.getMainExecutor(this)) { event ->

                when (event) {
                    is VideoRecordEvent.Start -> {
                        scheduleSegmentStop()
                    }

                    is VideoRecordEvent.Finalize -> {

                        segmentHandler.removeCallbacksAndMessages(null)
                        recordingHandle = null

                        if (event.hasError()) {
                            startNewSegment()
                        } else {
                            uploadToFirebase(file) {
                                if (isActive) startNewSegment()
                            }
                        }
                    }
                }
            }
    }

    private fun scheduleSegmentStop() {
        segmentHandler.postDelayed({
            stopCurrentRecording()
        }, SEGMENT_DURATION_MS)
    }

    private fun stopCurrentRecording() {
        try {
            recordingHandle?.stop()
        } catch (_: Exception) {}
    }

    private fun uploadToFirebase(file: File, onComplete: () -> Unit) {

        // 🔥 Always nest folder by incident ID!!
        val path = "incidents/$incidentId/videos/${file.name}"
        val ref = Firebase.storage.reference.child(path)

        ref.putFile(Uri.fromFile(file))
            .addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { downloadUrl ->
                    saveMetadataToFirestore(downloadUrl.toString(), path)
                    onComplete()
                }
            }
    }

    private fun saveMetadataToFirestore(url: String, path: String) {

        FirebaseFirestore.getInstance()
            .collection("incidents")
            .document(incidentId)
            .update(
                "videoUrls", FieldValue.arrayUnion(
                    mapOf(
                        "incidentId" to incidentId,   // 🔥 add incidentId INSIDE ARRAY
                        "url" to url,
                        "path" to path,
                        "uploadedAt" to Timestamp.now(),
                        "type" to "video"
                    )
                )
            )
            .addOnSuccessListener {
                Log.d("VideoRecord", "✔ Firestore updated — Video linked")
            }
            .addOnFailureListener { e ->
                Log.e("VideoRecord", "❗ Firestore save FAILED", e)
            }
    }

    override fun onDestroy() {
        isActive = false
        segmentHandler.removeCallbacksAndMessages(null)
        stopCurrentRecording()
        super.onDestroy()
    }
}
