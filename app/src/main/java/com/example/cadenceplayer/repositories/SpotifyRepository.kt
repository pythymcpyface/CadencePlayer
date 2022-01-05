package com.example.cadenceplayer.repositories

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.adamratzman.spotify.SpotifyClientApi
import com.adamratzman.spotify.models.PlayableUri
import com.adamratzman.spotify.models.Track
import com.example.cadenceplayer.CadencePlayer
import com.example.cadenceplayer.R
import com.example.cadenceplayer.model.TrackFeatures
import com.example.cadenceplayer.spotify.*
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.types.PlayerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.SocketTimeoutException

class SpotifyRepository(application: Application) {

    /**
     * Initialises the web api
     */

    @RequiresApi(Build.VERSION_CODES.M)
    var api: SpotifyClientApi? = (application as CadencePlayer).model.
            credentialStore.getSpotifyClientPkceApi()

    var player: SpotifyAppRemoteApi = SpotifyAppRemoteApi.create()

    val isLoadingLiveData: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>()
    }

    fun getPlayerState(): MutableLiveData<PlayerState> = player.playerState

    fun getPlayerConnectionState(): MutableLiveData<SpotifyAppRemoteApi.ConnectionStatus> =
        player.connectionState

    suspend fun togglePause() = player.playingState { state ->
        when (state) {
            SpotifyAppRemoteApi.PlayingState.PAUSED -> player.resume()
            SpotifyAppRemoteApi.PlayingState.PLAYING -> player.pause()
            SpotifyAppRemoteApi.PlayingState.STOPPED -> {
                // Try to play the last
                GlobalScope.launch(Dispatchers.IO) {
                    api?.player?.getRecentlyPlayed()
                        ?.forEachIndexed { i, history ->
                            if (i == 0) {
                                history?.track?.uri?.uri?.let { player.play(it) }
                            } else {
                                history?.track?.uri?.uri?.let { player.queue(it) }
                            }
                        }
                        }
                }
            }
        }


    fun playPlayableItem(uri: PlayableUri) = player.play(uri.uri)

    fun skipNext() = player.skipNext()

    fun skipPrevious() = player.skipPrevious()

    fun resume() = player.resume()

    fun pause() = player.pause()

    fun getPlaylists(): LiveData<List<PlayableItem?>> {
        val playlistList = MutableLiveData<List<PlayableItem?>>()
        GlobalScope.launch(Dispatchers.IO) {
            api?.personalization?.api?.playlists?.getUserPlaylists(getUserId())?.toPlayables(api!!) {
                playlistList.postValue(it)
            }
        }
        return playlistList
    }

//    suspend fun getSavedTracksFeatures(): LiveData<List<TrackFeatures?>> {
//        val savedTracksFeatures = MutableLiveData<List<TrackFeatures?>>()
//        api.service.library.getSavedTracks() .map { track ->
//            savedTracksFeatures.postValue(tracks.map { track ->
//                track.toTrackFeatures(api, track)
//            })
//        }
//        return savedTracksFeatures
//    }
//
//    fun getPlayListAudioFeatures(context: Context, playlistUri: PlaylistUri, tokenExpiredCallback: OnTokenExpired = this): LiveData<List<AudioFeatures?>> {
//        val audioFeatures = MutableLiveData<List<AudioFeatures?>>()
//        api.assertApiNotExpired(context){tokenExpiredCallback.tokenExpired()}?.playlists?.getClientPlaylist(playlistUri.toString())?.queue {
//            it?.tracks
//        }
//        return audioFeatures
//    }

    fun connectPlayer(context: Context) {
        Log.i("Authorization", "connecting player")
        player.connect(context, handler = { })
    }

    fun getRemote(): SpotifyAppRemote? {
        return player.getRemote()
    }

    suspend fun getUserId(): String {
        Log.i("StateChangeMyPermServ", "getting userId")
        return api?.getUserId() ?: ""
    }

    fun disconnectPlayer() {
        player.disconnect()
    }

    suspend fun getSavedTracks(): List<TrackFeatures?> {
        return api?.also { it.getApiBuilder().requestTimeoutMillis(300000) }?.library?.getSavedTracks()?.getAllItems()?.map { track ->
            try {
                api.also { it?.getApiBuilder()?.requestTimeoutMillis(300000) }?.let { track?.toTrackFeatures(it, track) }
            } catch (e: SocketTimeoutException) {
                null
            }
        } ?: mutableListOf()
    }

    fun getAllSavedTracks(pages: Int = 1): LiveData<List<TrackFeatures?>> {
        val savedTracksFeatures = MutableLiveData<List<TrackFeatures?>>()
        GlobalScope.launch(Dispatchers.IO) {
            Log.i("StateChangeMyPermServ", "getting saved tracks")
            val savedTracksFeaturesList = mutableListOf<TrackFeatures?>()
                for (i in 1 until pages.plus(1)) {
                    api?.library?.getSavedTracks(
                        offset = i.times(
                            50
                        )
                    )?.map { track ->
                        track?.toTrackFeatures(api!!, track)
                    }?.let { savedTracksFeaturesList.addAll(it) }
                }
            savedTracksFeatures.postValue(savedTracksFeaturesList)
        }
        return savedTracksFeatures
    }

//    TODO: Add database objects to roomdb to make things quicker as a local cache

    fun getFilteredRandomTrack(
        context: Context,
        trackFeatures: List<TrackFeatures?>?,
        tempo: Float
    ): TrackFeatures? {

        val bound = context.resources.getInteger(R.integer.bound)

        val min = tempo.times(1.minus(bound.div(100.0)))
        val max = tempo.times(1.plus(bound.div(100.0)))

        return with(trackFeatures?.mapNotNull { it ->
            if (it?.audioFeatures?.tempo!! in min..max || it.audioFeatures.tempo.times(2) in min..max || it.audioFeatures.tempo.div(
                    2) in min..max) {
                it
            } else {
                null
            }
        }) {
            if (this.isNullOrEmpty()) {
                Log.i("StateChangeMyPermServ", "no tracks match $tempo")
                null
            } else {
                Log.i("StateChangeMyPermServ", "filtered tracks $this")
                this.random()
            }
        }
    }

    suspend fun getPlaylistTracks(playlist: String): List<TrackFeatures?>? {
        return withContext(Dispatchers.IO) {
            api.also { it?.getApiBuilder()?.requestTimeoutMillis(300000) }?.library?.api?.playlists?.getPlaylistTracks(playlist)?.getAllItemsNotNull()?.convertToTracks()?.mapNotNull { track ->
                api.also { it?.getApiBuilder()?.requestTimeoutMillis(300000) }?.let { track?.toTrackFeatures(it, track) }
            }
        }
    }

    suspend fun getCurrentTrackFeatures(): TrackFeatures? {
        return api?.player?.getCurrentlyPlaying()?.track?.let {
            api?.player?.getCurrentlyPlaying()?.track?.toTrackFeatures(api!!,
                it)
        }
    }

    suspend fun getTrackFeatures(track: Track): TrackFeatures {
        return track.toTrackFeatures(api!!)
    }
}