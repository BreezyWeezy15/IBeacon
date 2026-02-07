package com.app.ibeacon

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.app.ibeacon.business.BeaconRepository
import com.app.ibeacon.services.BeaconScanService
import kotlinx.coroutines.*
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MainActivity : AppCompatActivity() {

    private val repository by lazy { BeaconRepository(this) }
    private lateinit var mapView: MapView
    private lateinit var locationProvider: LocationProvider

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val REQUEST_ENABLE_BT = 2001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        Configuration.getInstance().userAgentValue = packageName
        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.map)
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = filesDir
            osmdroidTileCache = filesDir
        }
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(14.0)

        locationProvider = LocationProvider(this)

        checkPermissionsAndBluetooth()
    }

    private fun startLocationUpdates() {
        // Start receiving location updates and center map on user's location
        locationProvider.startLocationUpdates { loc ->
            val userPoint = GeoPoint(loc.latitude, loc.longitude)
            mapView.controller.setCenter(userPoint)
        }
    }

    private fun checkPermissionsAndBluetooth() {
        if (!hasAllPermissions()) {
            requestPermissions()
        } else {
            startLocationUpdates()
            ensureBluetoothEnabled()
        }
    }

    @SuppressLint("MissingPermission")
    private fun ensureBluetoothEnabled() {
        val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                PERMISSION_REQUEST_CODE
            )
            return
        }

        if (!adapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        } else {
            startBeaconService()
            loadBeaconsOnMap()
        }
    }

    private fun hasAllPermissions(): Boolean =
        requiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            requiredPermissions().toTypedArray(),
            PERMISSION_REQUEST_CODE
        )
    }

    private fun requiredPermissions(): MutableList<String> {
        val list = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_SCAN)
            list.add(Manifest.permission.BLUETOOTH_CONNECT)
            list.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return list
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
            startLocationUpdates()
            ensureBluetoothEnabled()
        } else {
            android.widget.Toast.makeText(
                this,
                "All permissions are required for proper functionality",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT) {
            val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = manager.adapter
            if (adapter.isEnabled) {
                startBeaconService()
                loadBeaconsOnMap()
            } else {
                android.widget.Toast.makeText(this, "Bluetooth is required", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startBeaconService() {
        val intent = Intent(this, BeaconScanService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun loadBeaconsOnMap() = CoroutineScope(Dispatchers.IO).launch {
        val beacons = repository.getAll()
        withContext(Dispatchers.Main) {
            mapView.overlays.clear()
            beacons.forEach {
                if (it.latitude != null && it.longitude != null) {
                    val point = GeoPoint(it.latitude!!, it.longitude!!)
                    val marker = Marker(mapView).apply {
                        position = point
                        title = "UUID: ${it.uuid}"
                        subDescription =
                            "MAC: ${it.mac}\nMajor: ${it.major} Minor: ${it.minor}\nRSSI: ${it.rssi}"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    mapView.overlays.add(marker)
                }
            }
            if (beacons.isNotEmpty()) {
                val first = beacons.first()
                mapView.controller.setCenter(
                    GeoPoint(first.latitude!!, first.longitude!!)
                )
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
        locationProvider.stopLocationUpdates()
    }
}
