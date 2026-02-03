package com.app.ibeacon.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import androidx.core.app.NotificationCompat
import com.app.ibeacon.MainActivity
import com.app.ibeacon.R
import com.app.ibeacon.business.BeaconRepository
import com.app.ibeacon.data.BeaconEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.altbeacon.beacon.*

class BeaconScanService : Service(), BeaconConsumer {

    private lateinit var beaconManager: BeaconManager
    private lateinit var repository: BeaconRepository

    // Replace these with your real location values (update dynamically in your code)
    private var lastKnownLatitude: Double = 0.0
    private var lastKnownLongitude: Double = 0.0

    override fun onCreate() {
        super.onCreate()

        repository = BeaconRepository(applicationContext)

        // 🔥 MUST be first
        startForeground(NOTIFICATION_ID, createNotification())

        beaconManager = BeaconManager.getInstanceForApplication(this)
        beaconManager.beaconParsers.clear()
        beaconManager.beaconParsers.add(
            BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24")
        )
        beaconManager.bind(this)
    }

    override fun onBeaconServiceConnect() {
        beaconManager.addRangeNotifier { beacons, _ ->
            beacons.forEach { beacon ->

                // Log all beacon values for debugging
                Log.d(
                    "BeaconScan",
                    "UUID: ${beacon.id1}, Major: ${beacon.id2}, Minor: ${beacon.id3}, " +
                            "MAC: ${beacon.bluetoothAddress}, TxPower: ${beacon.txPower}, RSSI: ${beacon.rssi}, " +
                            "Timestamp: ${System.currentTimeMillis()}, " +
                            "Lat: $lastKnownLatitude, Lon: $lastKnownLongitude"
                )

                // Build BeaconEntity from scanned beacon
                val entity = BeaconEntity(
                    uuid = beacon.id1.toString(),
                    mac = beacon.bluetoothAddress,
                    major = beacon.id2.toInt(),
                    minor = beacon.id3.toInt(),
                    txPower = beacon.txPower,
                    rssi = beacon.rssi,
                    timestamp = System.currentTimeMillis(),
                    latitude = lastKnownLatitude,
                    longitude = lastKnownLongitude
                )

                // Save to Room database using coroutine
                CoroutineScope(Dispatchers.IO).launch {
                    repository.insert(entity)
                }
            }
        }

        try {
            beaconManager.startRangingBeacons(Region("all-beacons", null, null, null))
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }


    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        beaconManager.unbind(this)
        super.onDestroy()
    }

    // ---------------- NOTIFICATION ----------------

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

        // Optional: tap notification to open MapActivity
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("iBeacon Scanner Running")
            .setContentText("Scanning nearby iBeacons")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 101
        var isRunning = false
    }
}
