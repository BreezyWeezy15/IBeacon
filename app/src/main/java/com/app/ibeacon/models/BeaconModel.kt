package com.app.ibeacon.models

data class BeaconModel(
    val uuid: String? = null,
    val mac: String? = null,
    val major: Int? = null,
    val minor: Int? = null,
    val txPower: Int? = null,
    val rssi: Int? = null,
    val timestamp: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)
