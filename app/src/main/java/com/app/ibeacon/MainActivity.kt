package com.app.ibeacon

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.app.ibeacon.business.BeaconRepository
import com.app.ibeacon.db.AppDatabase
import com.app.ibeacon.services.BeaconScanService
import kotlinx.coroutines.*
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MainActivity : AppCompatActivity() {

    private val repository by lazy { BeaconRepository(this) }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    private lateinit var mapView: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        Configuration.getInstance().userAgentValue = packageName
        setContentView(R.layout.activity_main)

        // --- Apply window insets ---
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )
            insets
        }

        // --- Initialize OSM Map ---
        mapView = findViewById(R.id.map) // Make sure activity_main.xml has MapView with id "map"
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(17.0)


        // --- Permissions + start service ---
        if (hasAllPermissions()) {
            startBeaconService()
            loadBeaconsOnMap()
        } else {
            requestPermissions()
        }
    }

    // --- Permissions helpers ---
    private fun hasAllPermissions(): Boolean {
        val permissions = requiredPermissions()
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            requiredPermissions().toTypedArray(),
            PERMISSION_REQUEST_CODE
        )
    }

    private fun requiredPermissions(): MutableList<String> {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        return permissions
    }

    private fun startBeaconService() {
        val intent = Intent(this, BeaconScanService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE &&
            grantResults.isNotEmpty() &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        ) {
            startBeaconService()
            loadBeaconsOnMap()
        }
    }

    private fun loadBeaconsOnMap() = CoroutineScope(Dispatchers.IO).launch {
        val beacons = repository.getAll()  // use repository instead of DAO directly
        withContext(Dispatchers.Main) {
            mapView.overlays.clear()
            beacons.forEach { beacon ->
                if (beacon.latitude != null && beacon.longitude != null) {
                    val point = GeoPoint(beacon.latitude, beacon.longitude)
                    val marker = Marker(mapView)
                    marker.position = point
                    marker.title = "UUID: ${beacon.uuid}"
                    marker.subDescription =
                        "MAC: ${beacon.mac}\nMajor: ${beacon.major} Minor: ${beacon.minor}\nRSSI: ${beacon.rssi}"
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    mapView.overlays.add(marker)
                }
            }
            if (beacons.isNotEmpty()) {
                val first = beacons.first()
                mapView.controller.setCenter(first.latitude?.let { first.longitude?.let { it1 ->
                    GeoPoint(it, it1)
                }})
            }
            mapView.invalidate()
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}
