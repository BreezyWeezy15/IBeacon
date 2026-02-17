package com.app.ibeacon.models

data class BeaconModel(
    val uuid: String = "",
    val mac: String = "",
    val major: Int = 0,
    val minor: Int = 0,
    val txPower: Int = 0,
    val rssi: Int = 0,
    val distance: Double = 0.0,
    val timestamp: Long = 0L,
    val latitude: Double? = null,
    val longitude: Double? = null
)
