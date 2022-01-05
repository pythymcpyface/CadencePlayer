package com.example.cadenceplayer.objects

import android.os.Build
import androidx.annotation.RequiresApi
import com.adamratzman.spotify.auth.SpotifyDefaultCredentialStore
import com.example.cadenceplayer.BuildConfig
import com.example.cadenceplayer.CadencePlayer

@RequiresApi(Build.VERSION_CODES.M)
object Model {
    val credentialStore by lazy {
        SpotifyDefaultCredentialStore(
            clientId = BuildConfig.SPOTIFY_CLIENT_ID,
            redirectUri = BuildConfig.SPOTIFY_REDIRECT_URI_PKCE,
            applicationContext = CadencePlayer.context
        )
    }
}