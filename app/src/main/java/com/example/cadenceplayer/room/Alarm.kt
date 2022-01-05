package com.example.cadenceplayer.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.android.gms.maps.model.LatLng
import java.sql.Date

@Entity(tableName = "alarm_table")
data class Alarm(

    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "request_code") val requestCode: Int,
    @ColumnInfo(name = "timestamp") val timestamp: Date,
    @ColumnInfo(name = "status") val status: AlarmStatus,
    @ColumnInfo(name = "location") val location: LatLng,
    @ColumnInfo(name = "user_id") val userId: String

    ) {
    enum class AlarmStatus {
        QUEUED, COMPLETE
    }
}