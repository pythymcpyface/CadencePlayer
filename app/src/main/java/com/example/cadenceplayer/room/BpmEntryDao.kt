package com.example.cadenceplayer.room

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BpmEntryDao {
    @Query("SELECT * FROM bpm_table ORDER BY id DESC LIMIT :LIMIT")
    fun getAllRows(LIMIT: Int): LiveData<List<BpmEntry>>

    @Query("SELECT * FROM bpm_table ORDER BY id DESC LIMIT 1")
    suspend fun getTopRow(): BpmEntry

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(bpm: BpmEntry)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(bpms: List<BpmEntry>)

    @Query("DELETE FROM bpm_table")
    suspend fun deleteAll()

    @Query("DELETE FROM bpm_table WHERE id < :ID")
    suspend fun deleteWhere(ID: Int)
}