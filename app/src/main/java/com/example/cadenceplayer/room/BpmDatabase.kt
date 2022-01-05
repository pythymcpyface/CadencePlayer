package com.example.cadenceplayer.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Database(entities = [BpmEntry::class], version = 1, exportSchema = false)
@TypeConverters(DateConverters::class, LocationConverters::class)
abstract class BpmDatabase : RoomDatabase() {

    abstract fun bpmEntryDao(): BpmEntryDao

    private class BpmDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            INSTANCE?.let { scope.launch { populateDatabase(it.bpmEntryDao()) } }
        }

        fun populateDatabase(accelerationDao: BpmEntryDao) {
//            accelerationDao.deleteAll()
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: BpmDatabase? = null

        fun getDatabase(
            context: Context,
            scope: CoroutineScope
        ): BpmDatabase {
            val tempInstance = INSTANCE
            if (tempInstance != null) {
                return tempInstance
            }
            synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BpmDatabase::class.java,
                    "bpm_table"
                )
                    .addCallback(BpmDatabaseCallback(scope))
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                return instance
            }
        }
    }

}