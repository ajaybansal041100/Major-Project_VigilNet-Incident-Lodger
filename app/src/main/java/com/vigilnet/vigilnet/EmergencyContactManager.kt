package com.vigilnet.vigilnet

import android.content.Context
import android.util.Log
import android.net.Uri
import android.database.Cursor
import android.provider.ContactsContract
import org.json.JSONArray
import org.json.JSONObject

object EmergencyContactManager {

    private const val PREFS = "emergency_prefs"
    private const val KEY_CONTACTS = "contacts_v2"   // JSON storage

    data class Contact(
        val name: String,
        val phone: String
    )

    // return ALL contacts as model list
    fun getContacts(context: Context): List<Contact> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CONTACTS, "[]")  // FIX: never null

        val arr = JSONArray(raw)
        val output = mutableListOf<Contact>()

        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            output.add(Contact(obj.getString("name"), obj.getString("phone")))
        }
        Log.d("CONTACTS", "Returning ${output.size} saved contacts")
        return output
    }

    private fun saveContacts(context: Context, list: List<Contact>) {
        val arr = JSONArray()
        for (c in list) {
            val obj = JSONObject()
            obj.put("name", c.name)
            obj.put("phone", c.phone)
            arr.put(obj)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CONTACTS, arr.toString())
            .apply()

        Log.d("CONTACTS", "✔ Contacts Saved (JSON): $arr")
    }

    fun addContact(context: Context, name: String, phone: String): Boolean {
        val formatted = normalizePhone(phone)

        if (!isValidIndianPhone(formatted)) {
            Log.e("CONTACTS", "❗ Invalid number: $phone")
            return false
        }

        val list = getContacts(context).toMutableList()

        if (list.any { it.phone == formatted && it.name == name }) {
            Log.w("CONTACTS", "⚠ Already exists: $name ($formatted)")
            return false
        }

        list.add(Contact(name, formatted))
        saveContacts(context, list)
        Log.d("CONTACTS", "✔ Added: $name ($formatted)")
        return true
    }

    fun removeContact(context: Context, phone: String) {
        val list = getContacts(context).toMutableList()
        list.removeAll { it.phone == phone }
        saveContacts(context, list)
    }

    fun normalizePhone(input: String): String {
        val digits = input.filter { it.isDigit() }
        return when {
            digits.length == 10 -> "+91$digits"
            digits.length == 12 && digits.startsWith("91") -> "+$digits"
            input.startsWith("+91") && digits.length == 12 -> "+91${digits.substring(2)}"
            else -> "+91${digits.takeLast(10)}"
        }
    }

    fun isValidIndianPhone(phone: String): Boolean {
        return Regex("^\\+91[6-9][0-9]{9}$").matches(phone)
    }

    fun getContactName(context: Context, uri: Uri): String {
        var name = "Unknown"
        val cursor: Cursor? = context.contentResolver.query(
            uri, arrayOf(ContactsContract.Contacts.DISPLAY_NAME),
            null, null, null
        )
        cursor?.use { if (it.moveToFirst()) name = it.getString(0) }
        return name
    }

    fun getContactNumber(context: Context, uri: Uri): String? {
        // First extract REAL contact ID
        var contactId: String? = null
        val cursorId = context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.Contacts._ID),
            null, null, null
        )
        cursorId?.use {
            if (it.moveToFirst()) {
                contactId = it.getString(0)
            }
        }

        if (contactId == null) return null

        // Now query phone number using CONTACT_ID
        var phone: String? = null
        val cursorPhone = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
            arrayOf(contactId),
            null
        )

        cursorPhone?.use {
            if (it.moveToFirst()) {
                phone = normalizePhone(it.getString(0))
            }
        }

        return phone
    }
}
