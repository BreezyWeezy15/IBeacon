package com.app.ibeacon.services

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import androidx.core.app.NotificationCompat
import com.app.ibeacon.LocationProvider
import com.app.ibeacon.MainActivity
import com.app.ibeacon.R
import com.app.ibeacon.models.BeaconModel
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.altbeacon.beacon.*

class BeaconScanService : Service(), BeaconConsumer {

    private val IBEACON_LAYOUT = "m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"

    private lateinit var beaconManager: BeaconManager
    private lateinit var locationProvider: LocationProvider

    override fun onCreate() {
        super.onCreate()

        BeaconManager.setDebug(true)

        locationProvider = LocationProvider(this)
        locationProvider.startLocationUpdates()

        startForeground(NOTIFICATION_ID, createNotification())
        setupBeaconScanner()
    }

    private fun setupBeaconScanner() {
        beaconManager = BeaconManager.getInstanceForApplication(this)

        beaconManager.beaconParsers.clear()
        beaconManager.beaconParsers.add(
            BeaconParser().setBeaconLayout(IBEACON_LAYOUT)
        )

        beaconManager.foregroundScanPeriod = 1100L
        beaconManager.foregroundBetweenScanPeriod = 0L

        beaconManager.bind(this)
    }

    override fun onBeaconServiceConnect() {

        val region = Region("AllBeacons", null, null, null)
        val database = FirebaseDatabase.getInstance().reference.child("beacons")

        beaconManager.addRangeNotifier { beacons, _ ->

            val loc = locationProvider.lastLocation

            for (beacon in beacons) {

                val key =
                    "${beacon.id1}_${beacon.id2}_${beacon.id3}"

                val beaconData = BeaconModel(
                    uuid = beacon.id1.toString(),
                    mac = beacon.bluetoothAddress,
                    major = beacon.id2.toInt(),
                    minor = beacon.id3.toInt(),
                    txPower = beacon.txPower,
                    rssi = beacon.rssi,
                    distance = beacon.distance,
                    timestamp = System.currentTimeMillis(),
                    latitude = loc?.latitude,
                    longitude = loc?.longitude
                )

                CoroutineScope(Dispatchers.IO).launch {
                    database.child(key).setValue(beaconData)
                }
            }
        }

        try {
            beaconManager.startRangingBeaconsInRegion(region)
        } catch (e: RemoteException) {
            Log.e("BEACON_SCAN", "Ranging failed", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        beaconManager.unbind(this)
        locationProvider.stopLocationUpdates()
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        val channelId = "ibeacon_scan_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "iBeacon Scanner",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("iBeacon Scanner Running")
            .setContentText("Scanning iBeacons")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 101
    }
}
