package com.app.ibeacon.services

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.RemoteException

import androidx.core.app.NotificationCompat
import com.app.ibeacon.LocationProvider
import com.app.ibeacon.MainActivity
import com.app.ibeacon.R
import com.app.ibeacon.business.BeaconRepository
import com.app.ibeacon.data.BeaconEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.altbeacon.beacon.*

class BeaconScanService : Service(), BeaconConsumer {

    private val IBEACON_LAYOUT = "m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"
    private val TARGET_UUID = Identifier.parse("f7826da6-4fa2-4e98-8024-bc5b71e0893e")

    private lateinit var beaconManager: BeaconManager
    private lateinit var repository: BeaconRepository
    private lateinit var locationProvider: LocationProvider

    override fun onCreate() {
        super.onCreate()

        repository = BeaconRepository(applicationContext)
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
        beaconManager.bind(this)
    }

    override fun onBeaconServiceConnect() {
        val region = Region(
            "uuid-region",
            TARGET_UUID,
            null,
            null
        )

        beaconManager.addRangeNotifier { beacons, _ ->
            beacons.forEach { beacon ->
                val loc = locationProvider.lastLocation
                val entity = BeaconEntity(
                    uuid = beacon.id1.toString(),
                    mac = beacon.bluetoothAddress,
                    major = beacon.id2.toInt(),
                    minor = beacon.id3.toInt(),
                    txPower = beacon.txPower,
                    rssi = beacon.rssi,
                    timestamp = System.currentTimeMillis(),
                    latitude = loc?.latitude,
                    longitude = loc?.longitude
                )

                CoroutineScope(Dispatchers.IO).launch {
                    repository.insert(entity)
                }
            }
        }

        try {
            beaconManager.startRangingBeaconsInRegion(region)
        } catch (e: RemoteException) {
            e.printStackTrace()
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
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
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
            .setContentText("Scanning iBeacons by UUID")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 101
    }
}
