package com.example.cadenceplayer.service

import android.content.Context
import android.content.SharedPreferences

class ServiceTracker {

    private val name = "SPYSERVICE_KEY"
    private val key = "SPYSERVICE_STATE"

    fun setServiceState(context: Context, state: ServiceState) {
        val sharedPrefs = getPreferences(context)
        sharedPrefs.edit().let {
            it.putString(key, state.name)
            it.apply()
        }
    }

    fun getServiceState(context: Context): ServiceState? {
        val sharedPrefs = getPreferences(context)
        val value = sharedPrefs.getString(key, ServiceState.STOPPED.name)
        return value?.let { ServiceState.valueOf(it) }
    }

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(name, 0)
    }

}