package com.example.cadenceplayer.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.sql.Date

@Entity(tableName = "bpm_table")
data class BpmEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "timestamp") val timestamp: Date,
    @ColumnInfo(name = "bpm") val bpm: Float,
    @ColumnInfo(name = "latitude") val latitude: Double,
    @ColumnInfo(name = "longitude") val longitude: Double,
    @ColumnInfo(name = "track_title") val trackTitle: String,
    @ColumnInfo(name = "track_artist") val trackArtist: String,
    @ColumnInfo(name = "track_id") val trackId: String,
    @ColumnInfo(name = "track_tempo") val trackTempo: Float
)