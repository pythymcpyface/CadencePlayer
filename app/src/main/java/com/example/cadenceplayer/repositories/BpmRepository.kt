package com.example.cadenceplayer.repositories

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LiveData
import com.example.cadenceplayer.room.BpmEntry
import com.example.cadenceplayer.room.BpmEntryDao
import com.google.android.gms.maps.model.LatLng




class BpmRepository(
    private val bpmEntryDao: BpmEntryDao,
    sampleRate: Int,
) {

    val allBpmEntries: LiveData<List<BpmEntry>> = bpmEntryDao.getAllRows(sampleRate)

    suspend fun getTopRow(): BpmEntry {
        return bpmEntryDao.getTopRow()
    }

    suspend fun insert(bpmEntry: BpmEntry) {
        bpmEntryDao.insert(bpmEntry)
    }

    suspend fun insertAll(bpmEntries: List<BpmEntry>) {
        bpmEntryDao.insertAll(bpmEntries)
    }

    suspend fun deleteAll() {
        bpmEntryDao.deleteAll()
    }

    suspend fun deleteWhere(id: Int) {
        bpmEntryDao.deleteWhere(id)
    }

    fun requestLocationUpdates(context: Context, locationListener: LocationListener) {
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val fineLocationPermitted = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocationPermitted = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineLocationPermitted && coarseLocationPermitted) {
            Log.i("PermanentService", "Beginning location requests")
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                0,
                0F, locationListener)
        }
    }

    fun getLastKnownLocation(context: Context): LatLng? {
        Log.i("StateChangeMyPermServ", "getting location")
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val fineLocationPermitted = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocationPermitted = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        Log.i("StateChangeMyPermServ",
            "fine loc is permitted $fineLocationPermitted, course loc is permitted $coarseLocationPermitted")

        return if (fineLocationPermitted && coarseLocationPermitted) {
            Log.i("StateChangeMyPermServ", "Getting last location")
            if (locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) != null) {
                LatLng(locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)!!.latitude,
                    locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)!!.longitude)
            } else {
                null
            }
        } else {
            null
        }
    }
}