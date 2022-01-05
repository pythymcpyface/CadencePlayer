package com.example.cadenceplayer.spotify

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import com.example.cadenceplayer.BuildConfig
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.android.appremote.api.error.SpotifyConnectionTerminatedException
import com.spotify.protocol.types.ImageUri
import com.spotify.protocol.types.PlayerState
import com.spotify.protocol.types.Track


class SpotifyAppRemoteApi private constructor(private val connectionParams: ConnectionParams) {

    enum class PlayingState {
        PAUSED, PLAYING, STOPPED
    }

    enum class ConnectionStatus {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    private var spotifyAppRemote: SpotifyAppRemote? = null

    val connectionState: MutableLiveData<ConnectionStatus> =
        MutableLiveData<ConnectionStatus>(ConnectionStatus.DISCONNECTED)

    val playerState = MutableLiveData<PlayerState>()

    fun connect(context: Context, handler: (connected: Boolean) -> Unit) {
        connectionState.postValue(ConnectionStatus.CONNECTING)
        // Check if already connected
        if (spotifyAppRemote?.isConnected == true) {
            handler(true)
            return
        } else {
//            disconnect()
            // Connect to the remote spotify app
            SpotifyAppRemote.connect(context, connectionParams,
                object : Connector.ConnectionListener {
                    override fun onConnected(spotifyAppRemote: SpotifyAppRemote) {
                        this@SpotifyAppRemoteApi.spotifyAppRemote = spotifyAppRemote
                        Log.d("Authorization", "Connected!")
                        // Update live connection state data
                        connectionState.postValue(ConnectionStatus.CONNECTED)
                        // Start listening for state changes
                        subscribeToPlayerChanges { state -> playerState.postValue(state) }
                        // Now we can start interacting with App Remote
                        handler(true)
                    }
                    override fun onFailure(throwable: Throwable) {
                        Log.e("Authorization", throwable.message, throwable)
                        // Update live connection state data
                        if(throwable is SpotifyConnectionTerminatedException){
                            Toast.makeText(context, "Disconnected due to lack of playback", Toast.LENGTH_SHORT).show()
                            SpotifyAppRemote.connect(context, connectionParams, this)
                        } else {
                            connectionState.postValue(ConnectionStatus.DISCONNECTED)
                            // Something went wrong when attempting to connect!
                            handler(false)
                        }
                    }
                })
        }
        // Update connection state to show that we are attempting to connect to remote spotify app
        connectionState.postValue(ConnectionStatus.CONNECTING)
    }

    fun disconnect() {
        SpotifyAppRemote.disconnect(spotifyAppRemote)
        connectionState.postValue(ConnectionStatus.DISCONNECTED)
    }

    fun play(uri: String) {
        assertAppRemoteConnected()?.playerApi?.play(uri)
    }

    fun resume() {
        assertAppRemoteConnected()?.playerApi?.resume()
    }

    fun pause() {
        assertAppRemoteConnected()?.playerApi?.pause()
    }

    fun queue(track: String) {
        assertAppRemoteConnected()?.playerApi?.queue(track)
    }

    fun skipNext() {
        assertAppRemoteConnected()?.playerApi?.skipNext()
    }

    fun skipPrevious() {
        assertAppRemoteConnected()?.playerApi?.skipPrevious()
    }

    fun getCurrentTrack(handler: (track: Track) -> Unit) {
        assertAppRemoteConnected()?.playerApi?.playerState?.setResultCallback { result ->
            handler(result.track)
        }
    }

    fun getCurrentTrackImage(handler: (Bitmap) -> Unit)  {
        getCurrentTrack { track ->
            fetchImage(track.imageUri) { bitmap ->
                handler(bitmap)
            }
        }
    }

    fun fetchImage(imageUri: ImageUri, handler: (Bitmap) -> Unit)  {
        assertAppRemoteConnected()?.imagesApi?.getImage(imageUri)?.setResultCallback {
            handler(it)
        }
    }

    fun playingState(handler: (PlayingState) -> Unit) {
        assertAppRemoteConnected()?.playerApi?.playerState?.setResultCallback { result ->
            when {
                result.track.uri == null -> {
                    handler(PlayingState.STOPPED)
                }
                result.isPaused -> {
                    handler(PlayingState.PAUSED)
                }
                else -> {
                    handler(PlayingState.PLAYING)
                }
            }
        }
    }

    fun subscribeToPlayerChanges(handler: (PlayerState) -> Unit) {
        assertAppRemoteConnected()?.playerApi?.subscribeToPlayerState()?.setEventCallback {
            handler(it)
        }
    }

    private fun assertAppRemoteConnected(): SpotifyAppRemote? {
        spotifyAppRemote?.let {
            if (it.isConnected) {
                connectionState.postValue(ConnectionStatus.CONNECTED)
                return it
            }
        }
        Log.e("SpotifyAppRemoteService", "Spotify Disconnected")
        connectionState.postValue(ConnectionStatus.DISCONNECTED)
        return null
    }

    fun getRemote(): SpotifyAppRemote? {
        return spotifyAppRemote
    }

    companion object {
        fun create(): SpotifyAppRemoteApi {
            val REDIRECT_URI = BuildConfig.SPOTIFY_REDIRECT_URI_PKCE
            val CLIENT_ID = BuildConfig.SPOTIFY_CLIENT_ID

            val connectionParams = ConnectionParams.Builder(CLIENT_ID)
                .setRedirectUri(REDIRECT_URI)
//                .showAuthView(true)
                .build()
            return SpotifyAppRemoteApi(connectionParams)
        }
    }
}