package com.example.cadenceplayer.activities

import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.adamratzman.spotify.SpotifyClientApi
import com.adamratzman.spotify.SpotifyScope
import com.adamratzman.spotify.auth.pkce.AbstractSpotifyPkceLoginActivity
import com.example.cadenceplayer.BuildConfig
import com.example.cadenceplayer.MainActivity
import com.example.cadenceplayer.CadencePlayer

@RequiresApi(Build.VERSION_CODES.M)
class SpotifyAuthActivity: AbstractSpotifyPkceLoginActivity() {
    private val activityName = this.javaClass.simpleName
    override val clientId: String
        get() = BuildConfig.SPOTIFY_CLIENT_ID
    override val redirectUri: String
        get() = BuildConfig.SPOTIFY_REDIRECT_URI_PKCE
    override val scopes: List<SpotifyScope>
        get() = listOf(
        SpotifyScope.USER_LIBRARY_READ,
        SpotifyScope.PLAYLIST_READ_PRIVATE,
        SpotifyScope.PLAYLIST_READ_COLLABORATIVE,
        SpotifyScope.USER_READ_EMAIL,
        SpotifyScope.USER_READ_PRIVATE,
        SpotifyScope.USER_READ_PLAYBACK_STATE,
        SpotifyScope.USER_READ_RECENTLY_PLAYED
    )
    override val pkceCodeVerifier: String
        get() = BuildConfig.SPOTIFY_CODE_VERIFIER

    override fun onFailure(exception: Exception) {
        Log.i("StateChangeMyPermServ", "$activityName api returned failure $exception")
    }

    override fun onSuccess(api: SpotifyClientApi) {
        Log.i("StateChangeMyPermServ", "$activityName api returned success")

        val model = (application as CadencePlayer).model
        model.credentialStore.setSpotifyApi(api)
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
    }
}