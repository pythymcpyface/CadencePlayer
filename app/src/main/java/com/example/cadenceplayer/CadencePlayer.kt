package com.example.cadenceplayer

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.example.cadenceplayer.model.TrackFeatures
import com.example.cadenceplayer.objects.Model

class CadencePlayer: Application() {
    lateinit var model: Model

    override fun onCreate() {
        super.onCreate()
        model = Model
        context = applicationContext
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    companion object {
        lateinit var context: Context
    }

    lateinit var playlistTracks: MutableList<TrackFeatures?>

    fun setMyData(playlistTracks: MutableList<TrackFeatures?>) {
        this.playlistTracks = playlistTracks
    }

    fun getMyData(): MutableList<TrackFeatures?> {
        return playlistTracks
    }
}