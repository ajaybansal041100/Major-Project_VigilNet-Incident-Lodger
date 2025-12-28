package com.vigilnet.vigilnet

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Telephony
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.vigilnet.vigilnet.ui.theme.VigilNetTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestDefaultSmsApp()
        requestAllPermissions()
        startShakeService()

        setContent {
            VigilNetTheme {
                Scaffold { inner ->
                    SosScreen(Modifier.padding(inner))
                }
            }
        }
    }

    private fun requestDefaultSmsApp() {
        val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this)
        if (defaultSmsPackage != packageName) {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
            startActivity(intent)
            Toast.makeText(this, "📩 Set VigilNet as default SMS app", Toast.LENGTH_LONG).show()
        }
    }

    private fun startShakeService() {
        val serviceIntent = Intent(this, ShakeService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    private fun requestAllPermissions() {
        requestPermissions.launch(
            arrayOf(
                Manifest.permission.SEND_SMS,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_CONTACTS
            )
        )
    }
}

// ------------------- UI SECTION ---------------------

@Composable
fun SosScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var contacts by remember {
        mutableStateOf(EmergencyContactManager.getContacts(context))
    }
    var newName by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }

    val pickContactLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri: Uri? ->
            if (uri != null) {
                val name = EmergencyContactManager.getContactName(context, uri)
                val phone = EmergencyContactManager.getContactNumber(context, uri)

                if (phone != null) {
                    EmergencyContactManager.addContact(context, name, phone)
                    contacts = EmergencyContactManager.getContacts(context)
                    Toast.makeText(context, "Added: $name ($phone)", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Error: could not get phone number", Toast.LENGTH_LONG).show()
                }
            }
        }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Emergency Contacts",
            fontSize = 24.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (contacts.isEmpty()) {
            Text("No emergency contacts saved.")
        } else {
            contacts.forEach { contact ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${contact.name} (${contact.phone})", modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            EmergencyContactManager.removeContact(context, contact.phone)
                            contacts = EmergencyContactManager.getContacts(context)
                        }) {
                            Text("Remove", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = newName,
            onValueChange = { newName = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = newPhone,
            onValueChange = { newPhone = it },
            label = { Text("Phone (+91XXXXXXXXXX)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- Save Contact (Rectangular FULL WIDTH) ---
        Button(
            onClick = {
                val ok = EmergencyContactManager.addContact(context, newName, newPhone)
                if (ok) {
                    contacts = EmergencyContactManager.getContacts(context)
                    newName = ""
                    newPhone = ""
                    Toast.makeText(context, "Contact added", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Invalid number", Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Contact")
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- Add From Phone (MATCHES Save Contact) ---
        Button(
            onClick = { pickContactLauncher.launch(null) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Add From Phone",
                fontSize = 16.sp,
                color = Color.White
            )
        }

    }
}




// ----------------- LOCATION UTILITY -------------------

fun getLastKnownLocation(): Location? {
    val context = App.instance
    val lm = context.getSystemService(LocationManager::class.java) ?: return null

    val fineOk = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    if (!fineOk) return null

    val gps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
    val net = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

    return gps ?: net
}
