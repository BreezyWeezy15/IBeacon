package com.app.ibeacon.db


import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.app.ibeacon.data.BeaconEntity

@Dao
interface BeaconDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(beacon: BeaconEntity)

    @Query("SELECT * FROM beacons")
    suspend fun getAll(): List<BeaconEntity>
}
