package com.example.cadenceplayer.room

import androidx.room.TypeConverter
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson

object LocationConverters {
    @TypeConverter
    fun toLocation(locationString: String?): LatLng? {
        return try {
            Gson().fromJson(locationString, LatLng::class.java)
        } catch (e: Exception) {
            null
        }
    }

    @TypeConverter
    fun toLocationString(location: LatLng?): String? {
        return Gson().toJson(location)
    }
}