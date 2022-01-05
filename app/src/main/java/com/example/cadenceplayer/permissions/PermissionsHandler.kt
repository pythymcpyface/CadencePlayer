package com.example.cadenceplayer.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.LatLng

class PermissionsHandler(
    private val contextIn: Context
) {
    private val FINE_LOCATION_PERMISSION = Manifest.permission.ACCESS_FINE_LOCATION
    private val COARSE_LOCATION_PERMISSION = Manifest.permission.ACCESS_COARSE_LOCATION
    private val INITIAL_REQUEST = 1001
    var fineLocationPermitted = false
    var coarseLocationPermitted = false
    var locationPermitted = false
    var location = LatLng(0.0,0.0)

    fun requestPermission(activity: Activity) {
        // Check location permissions
        if (!canAccessLocation()) {
            Log.i("StateChangeMyPermServ", "can't access location so requesting")
            requestPermissions(activity, arrayOf(FINE_LOCATION_PERMISSION, COARSE_LOCATION_PERMISSION), INITIAL_REQUEST)
        }
    }

    private fun canAccessLocation(): Boolean {
        return hasPermission()
    }

    private fun hasPermission(): Boolean {
        fineLocationPermitted = PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(
            contextIn,
            FINE_LOCATION_PERMISSION
        )
        coarseLocationPermitted = PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(
            contextIn,
            COARSE_LOCATION_PERMISSION
        )
        locationPermitted = fineLocationPermitted && coarseLocationPermitted
        Log.i("StateChangeMyPermServ", "Fine loc is permitted $fineLocationPermitted, course loc is permitted $coarseLocationPermitted")
        return locationPermitted
    }
}