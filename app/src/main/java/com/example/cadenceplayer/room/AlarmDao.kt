package com.example.cadenceplayer.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AlarmDao {

    @Query("SELECT * FROM alarm_table ORDER BY id DESC")
    suspend fun getAllRows(): List<Alarm>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(alarm: Alarm)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(alarms: List<Alarm>)

    @Query("DELETE FROM alarm_table")
    suspend fun deleteAll()

}