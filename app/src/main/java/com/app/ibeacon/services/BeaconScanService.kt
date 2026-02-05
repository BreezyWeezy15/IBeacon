package com.app.ibeacon.services

import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.app.ibeacon.MainActivity
import com.app.ibeacon.R
import com.app.ibeacon.business.BeaconRepository
import com.app.ibeacon.data.BeaconEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.altbeacon.beacon.*
import java.nio.ByteBuffer
import java.util.*

class BeaconScanService : Service(), BeaconConsumer {

    private lateinit var beaconManager: BeaconManager
    private lateinit var repository: BeaconRepository

    // Location should be updated via FusedLocationProvider (left as placeholder)
    private var lastKnownLatitude = 0.0
    private var lastKnownLongitude = 0.0

    // BLE advertiser
    private var advertiser: BluetoothLeAdvertiser? = null

    override fun onCreate() {
        super.onCreate()

        repository = BeaconRepository(applicationContext)

        startForeground(NOTIFICATION_ID, createNotification())

        setupBeaconScanner()
        startAdvertising()
    }

    // ---------------- SCANNING ----------------

    private fun setupBeaconScanner() {
        beaconManager = BeaconManager.getInstanceForApplication(this)
        beaconManager.beaconParsers.clear()
        beaconManager.beaconParsers.add(
            BeaconParser().setBeaconLayout(BeaconParser.ALTBEACON_LAYOUT)
        )
        beaconManager.bind(this)
    }

    override fun onBeaconServiceConnect() {
        beaconManager.addRangeNotifier { beacons, _ ->
            beacons.forEach { beacon ->

                Log.d(
                    "BeaconScan",
                    "UUID=${beacon.id1} Major=${beacon.id2} Minor=${beacon.id3} " +
                            "MAC=${beacon.bluetoothAddress} RSSI=${beacon.rssi}"
                )

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

                CoroutineScope(Dispatchers.IO).launch {
                    repository.insert(entity)
                }
            }
        }

        try {
            beaconManager.startRangingBeacons(
                Region("all-beacons", null, null, null)
            )
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    // ---------------- ADVERTISING ----------------

    private fun startAdvertising() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Bluetooth is OFF", Toast.LENGTH_LONG).show()
            return
        }

        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            Toast.makeText(this, "BLE Advertising not supported", Toast.LENGTH_LONG).show()
            return
        }

        val uuid = UUID.fromString("f7826da6-4fa2-4e98-8024-bc5b71e0893e")
        val major = 1
        val minor = 1
        val txPower = (-59).toByte()

        val manufacturerData = ByteBuffer.allocate(25).apply {
            put(0x02)
            put(0x15)
            putLong(uuid.mostSignificantBits)
            putLong(uuid.leastSignificantBits)
            putShort(major.toShort())
            putShort(minor.toShort())
            put(txPower)
        }.array()

        val advertiseData = AdvertiseData.Builder()
            .addManufacturerData(0x004C, manufacturerData) // Apple ID
            .setIncludeDeviceName(false)
            .build()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        advertiser?.startAdvertising(settings, advertiseData, advertiseCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Toast.makeText(
                this@BeaconScanService,
                "iBeacon advertising started",
                Toast.LENGTH_SHORT
            ).show()
        }

        override fun onStartFailure(errorCode: Int) {
            Toast.makeText(
                this@BeaconScanService,
                "Advertising failed: $errorCode",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ---------------- SERVICE ----------------

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        advertiser?.stopAdvertising(advertiseCallback)
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

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("iBeacon Scanner Running")
            .setContentText("Scanning + Advertising")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 101
    }
}
