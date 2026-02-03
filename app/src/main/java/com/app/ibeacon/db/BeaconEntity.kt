package com.app.ibeacon.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "beacons")
data class BeaconEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val uuid: String,
    val mac: String,
    val major: Int,
    val minor: Int,
    val txPower: Int,
    val rssi: Int,
    val timestamp: Long,
    val latitude: Double?,
    val longitude: Double?
)
