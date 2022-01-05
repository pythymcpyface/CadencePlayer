package com.example.cadenceplayer.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.adamratzman.spotify.auth.pkce.startSpotifyClientPkceLoginActivity
import com.example.cadenceplayer.databinding.ActivityInitializationBinding

class InitializationActivity: AppCompatActivity() {
    private lateinit var binding: ActivityInitializationBinding
    private val activityName = this.javaClass.simpleName

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("StateChangeMyPermServ", "$activityName created")
        binding = ActivityInitializationBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        startSpotifyClientPkceLoginActivity(SpotifyAuthActivity::class.java)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
    }
}