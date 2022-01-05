package com.example.cadenceplayer.viewmodel

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.*
import com.adamratzman.spotify.models.PlayableUri
import com.example.cadenceplayer.model.TrackFeatures
import com.example.cadenceplayer.repositories.SpotifyRepository
import com.example.cadenceplayer.spotify.PlayableItem
import com.example.cadenceplayer.spotify.SpotifyAppRemoteApi
import kotlinx.coroutines.*
import java.sql.Date


@RequiresApi(Build.VERSION_CODES.M)
class SpotifyViewModel(application: Application): AndroidViewModel(application) {
    var appStartTime: Long = Date(System.currentTimeMillis()).time.minus(60000)
    var tracks: MutableList<TrackFeatures?> = mutableListOf()
    var recentTracks: MutableList<PlayableUri> = mutableListOf()
    var spotifyRepository: SpotifyRepository = SpotifyRepository(getApplication())
    var playlistList: MutableList<PlayableItem> = mutableListOf()
    var isLoadingLiveData: MutableLiveData<Boolean> = spotifyRepository.isLoadingLiveData
    var playlist: PlayableItem? = null

    val connectionState: LiveData<SpotifyAppRemoteApi.ConnectionStatus> =
        Transformations.map(spotifyRepository.getPlayerConnectionState()) { it }

    val playerState = spotifyRepository.getPlayerState()


    fun togglePause() = viewModelScope.launch {spotifyRepository.togglePause()}
    fun skipPrevious() = spotifyRepository.skipPrevious()
    fun skipNext() = spotifyRepository.skipNext()
    fun play(track: PlayableUri) = spotifyRepository.playPlayableItem(track)
    fun pause() = spotifyRepository.pause()

    fun openSpotifyRemote() = spotifyRepository.connectPlayer(getApplication())
    fun closeSpotifyRemote() = spotifyRepository.disconnectPlayer()

    fun getAllSavedTracks(pages: Int = 1) = spotifyRepository.getAllSavedTracks(pages)
    suspend fun getSavedTracks() = spotifyRepository.getSavedTracks()
    fun getPlaylists() = spotifyRepository.getPlaylists()

    suspend fun getPlaylistTracks(playlist: String): List<TrackFeatures?> {
        return spotifyRepository.getPlaylistTracks(playlist)!!
    }

    fun getUserId(): String {
        return runBlocking {
            return@runBlocking withContext(Dispatchers.IO) {
                spotifyRepository.getUserId()
            }
        }
    }

    fun getFilteredRandomTrack(context: Context, tracks: MutableList<TrackFeatures?>, tempo: Float) =
        spotifyRepository.getFilteredRandomTrack(context, tracks, tempo)
}