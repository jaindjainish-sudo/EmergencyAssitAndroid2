package com.example.emergencyassist

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

data class TrustedContact(val id: String, val name: String, val phone: String)

class MainActivity : Activity() {
    private val bg = 0xFF0B1220.toInt()
    private val panel = 0xFF172235.toInt()
    private val text = 0xFFF1F5F9.toInt()
    private val muted = 0xFF94A3B8.toInt()
    private val blue = 0xFF0284C7.toInt()
    private val green = 0xFF16A34A.toInt()
    private val red = 0xFFDC2626.toInt()

    private lateinit var root: LinearLayout
    private lateinit var locationStatus: TextView
    private lateinit var locationManager: LocationManager
    private var latestLocation: Location? = null
    private val selectedIds = mutableSetOf<String>()
    private val contacts = mutableListOf<TrustedContact>()
    private val locationPermissionRequest = 410

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        loadContacts()
        buildUi()
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply { setBackgroundColor(bg) }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(28))
        }
        scroll.addView(root)
        setContentView(scroll)

        val title = TextView(this).apply {
            text = "🛡️ Emergency Assist"
            textSize = 27f
            setTextColor(0xFF38BDF8.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(6))
        }
        root.addView(title)
        val subtitle = TextView(this).apply {
            text = "Your safety companion, wherever you go."
            textSize = 14f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(20))
        }
        root.addView(subtitle)

        section("🚨 Emergency Help")
        addButton("CALL 112", red) {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")))
        }
        addNote("Opens the dialer. Confirm the call yourself.")

        section("📍 My Location")
        locationStatus = addNote("Location not requested yet. Tap Get Current Location.")
        addButton("Get Current Location", blue) { requestLocation() }
        addButton("Open Location in Maps", 0xFF334155.toInt()) {
            val loc = latestLocation
            if (loc == null) toast("Get your location first.")
            else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(mapUrl(loc))))
        }

        section("👥 Add Trusted Contact")
        val nameInput = EditText(this).apply {
            hint = "Contact name (e.g. Mom)"
            setHintTextColor(muted)
            setTextColor(text)
            setSingleLine(true)
        }
        val phoneInput = EditText(this).apply {
            hint = "WhatsApp number with country code"
            setHintTextColor(muted)
            setTextColor(text)
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setSingleLine(true)
        }
        root.addView(nameInput, matchWrap())
        root.addView(phoneInput, matchWrap())
        addButton("＋ Add Contact", blue) {
            val name = nameInput.text.toString().trim()
            val phone = phoneInput.text.toString().filter { it.isDigit() }
            if (name.isBlank()) {
                toast("Enter a contact name.")
            } else if (phone.length !in 8..15) {
                toast("Enter a valid number including country code, e.g. 919876543210.")
            } else if (contacts.any { it.phone == phone }) {
                toast("That number is already saved.")
            } else {
                contacts.add(TrustedContact(java.util.UUID.randomUUID().toString(), name, phone))
                saveContacts()
                nameInput.text.clear()
                phoneInput.text.clear()
                renderContacts()
                toast("Contact added.")
            }
        }

        section("✅ Select Contacts")
        addNote("Select the contacts you want to share with.")
        val contactContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tag = "contacts"
        }
        root.addView(contactContainer)
        renderContactsInto(contactContainer)

        addButton("Select All", 0xFF334155.toInt()) {
            selectedIds.clear()
            selectedIds.addAll(contacts.map { it.id })
            renderContacts()
        }
        addButton("Deselect All", 0xFF334155.toInt()) {
            selectedIds.clear()
            renderContacts()
        }
        addButton("Share Location on WhatsApp", green) { shareLocation() }
        addNote("WhatsApp requires you to review and send the message. Multiple recipients must be selected in WhatsApp manually.")
    }

    private fun section(label: String) {
        val t = TextView(this).apply {
            text = label
            textSize = 19f
            setTextColor(text)
            setPadding(0, dp(18), 0, dp(8))
        }
        root.addView(t)
    }

    private fun addNote(message: String): TextView {
        val t = TextView(this).apply {
            text = message
            textSize = 13f
            setTextColor(muted)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(panel)
        }
        root.addView(t, matchWrap())
        return t
    }

    private fun addButton(label: String, color: Int, action: () -> Unit) {
        val b = Button(this).apply {
            text = label
            textSize = 14f
            setTextColor(-1)
            setBackgroundColor(color)
            isAllCaps = false
            setOnClickListener { action() }
        }
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = dp(8)
        root.addView(b, lp)
    }

    private fun renderContacts() {
        val container = root.findViewWithTag<LinearLayout>("contacts") ?: return
        renderContactsInto(container)
    }

    private fun renderContactsInto(container: LinearLayout) {
        container.removeAllViews()
        if (contacts.isEmpty()) {
            val empty = TextView(this).apply {
                text = "No contacts saved yet."
                setTextColor(muted)
                setPadding(dp(8), dp(12), dp(8), dp(12))
            }
            container.addView(empty)
            return
        }

        contacts.forEach { contact ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(6), dp(4), dp(6), dp(4))
                setBackgroundColor(panel)
            }
            val check = CheckBox(this).apply {
                text = "${contact.name}\n+${contact.phone}"
                textSize = 14f
                setTextColor(text)
                isChecked = selectedIds.contains(contact.id)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedIds.add(contact.id)
                    else selectedIds.remove(contact.id)
                }
            }
            row.addView(check, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val remove = Button(this).apply {
                text = "Remove"
                textSize = 11f
                isAllCaps = false
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Remove contact?")
                        .setMessage("Remove ${contact.name} from Emergency Assist?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Remove") { _, _ ->
                            contacts.removeAll { it.id == contact.id }
                            selectedIds.remove(contact.id)
                            saveContacts()
                            renderContacts()
                        }.show()
                }
            }
            row.addView(remove)
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(6)
            container.addView(row, lp)
        }
    }

    private fun requestLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                locationPermissionRequest
            )
            return
        }

        try {
            val providers = locationManager.getProviders(true)
            var best: Location? = null
            for (provider in providers) {
                val loc = locationManager.getLastKnownLocation(provider) ?: continue
                if (best == null || loc.accuracy < best.accuracy) best = loc
            }
            if (best != null) {
                latestLocation = best
                updateLocationText(best)
            } else {
                locationStatus.text = "Waiting for a fresh GPS fix..."
            }

            val provider = when {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if (provider == null) {
                toast("Turn on Location in Android settings.")
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                return
            }

            @Suppress("DEPRECATION")
            locationManager.requestSingleUpdate(provider, object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    latestLocation = location
                    updateLocationText(location)
                }
                override fun onProviderDisabled(provider: String) {
                    locationStatus.text = "Location provider disabled."
                }
                override fun onProviderEnabled(provider: String) {}
                @Deprecated("Deprecated in Android")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            }, mainLooper)
        } catch (e: SecurityException) {
            toast("Location permission is required.")
        } catch (e: Exception) {
            toast("Couldn't get location. Please try again.")
        }
    }

    private fun updateLocationText(location: Location) {
        locationStatus.text =
            "Location ready\nLat: ${"%.6f".format(location.latitude)}\n" +
            "Lng: ${"%.6f".format(location.longitude)}\n" +
            "Accuracy: about ${location.accuracy.toInt()} metres"
    }

    private fun mapUrl(location: Location) =
        "https://www.google.com/maps?q=${location.latitude},${location.longitude}"

    private fun shareLocation() {
        val loc = latestLocation
        if (loc == null) {
            toast("Get your current location first.")
            return
        }
        val selected = contacts.filter { selectedIds.contains(it.id) }
        if (selected.isEmpty()) {
            toast("Select at least one contact.")
            return
        }

        val message = "🚨 EMERGENCY LOCATION SHARING\n\nMy current location:\n${mapUrl(loc)}\n\nPlease check on me and contact me if needed."

        if (selected.size == 1) {
            // Opens a single WhatsApp chat with the message filled in.
            val phone = selected.first().phone
            val uri = Uri.parse("https://wa.me/$phone?text=${Uri.encode(message)}")
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (_: Exception) {
                openGenericShare(message)
            }
        } else {
            // WhatsApp does not expose a supported API for preselecting multiple recipients.
            openGenericShare(message)
        }
    }

    private fun openGenericShare(message: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        startActivity(Intent.createChooser(send, "Share emergency location"))
    }

    private fun saveContacts() {
        val arr = JSONArray()
        contacts.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("phone", it.phone))
        }
        getSharedPreferences("emergency_assist", Context.MODE_PRIVATE)
            .edit().putString("contacts", arr.toString()).apply()
    }

    private fun loadContacts() {
        val raw = getSharedPreferences("emergency_assist", Context.MODE_PRIVATE)
            .getString("contacts", "[]") ?: "[]"
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                contacts.add(
                    TrustedContact(
                        obj.optString("id"),
                        obj.optString("name"),
                        obj.optString("phone")
                    )
                )
            }
        } catch (_: Exception) {
            contacts.clear()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationPermissionRequest) {
            if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
                requestLocation()
            } else {
                locationStatus.text = "Location permission denied. Enable it in Settings to share GPS location."
            }
        }
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
