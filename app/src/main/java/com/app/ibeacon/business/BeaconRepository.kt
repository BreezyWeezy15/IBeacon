package com.app.ibeacon.business

import android.content.Context
import com.app.ibeacon.data.BeaconEntity
import com.app.ibeacon.db.AppDatabase

class BeaconRepository(context: Context) {

    private val dao = AppDatabase.get(context).beaconDao()

    suspend fun insert(beacon: BeaconEntity) = dao.insert(beacon)

    suspend fun getAll(): List<BeaconEntity> = dao.getAll()
}