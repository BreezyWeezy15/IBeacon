package com.app.ibeacon

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.app.ibeacon.services.BeaconScanService
import com.google.firebase.database.FirebaseDatabase

class MainActivity : AppCompatActivity() {

    private lateinit var locationProvider: LocationProvider

    private val database =
        FirebaseDatabase.getInstance().reference.child("beacons")

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val REQUEST_ENABLE_BT = 2001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        locationProvider = LocationProvider(this)

        checkPermissionsAndBluetooth()
    }



    private fun checkPermissionsAndBluetooth() {
        if (!hasAllPermissions()) {
            requestPermissions()
            return
        }

        if (!isLocationEnabled()) {
            startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }

        ensureBluetoothEnabled()
        startBeaconService()
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
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        return list
    }

    private fun isLocationEnabled(): Boolean {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    private fun ensureBluetoothEnabled() {
        val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter

        if (!adapter.isEnabled) {
            startActivityForResult(
                Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                REQUEST_ENABLE_BT
            )
        }
    }

    private fun startBeaconService() {
        val intent = Intent(this, BeaconScanService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startForegroundService(intent)
        else
            startService(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        ) {
            checkPermissionsAndBluetooth()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_ENABLE_BT) {
            checkPermissionsAndBluetooth()
        }
    }

    override fun onPause() {
        super.onPause()
        locationProvider.stopLocationUpdates()
    }
}
