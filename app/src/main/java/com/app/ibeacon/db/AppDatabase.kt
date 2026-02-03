package com.app.ibeacon.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.app.ibeacon.data.BeaconEntity

@Database(entities = [BeaconEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {

    abstract fun beaconDao(): BeaconDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "beacon_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
