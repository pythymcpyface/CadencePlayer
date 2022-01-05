package com.example.cadenceplayer.spotify

import com.adamratzman.spotify.SpotifyClientApi
import com.adamratzman.spotify.models.*
import com.example.cadenceplayer.model.TrackFeatures
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

fun List<SimpleTrack>.convertToTracks(api: SpotifyClientApi, callback: (tracks: List<Track?>) -> Unit) {
    convertSimpleTracksToFullTracks(api, this, callback)
}

fun List<PlaylistTrack>.convertToTracks(): List<Playable?> {
    return convertPlaylistTracksToFullTracks(this)
}

fun List<SimpleTrack?>.fetchAlbums(api: SpotifyClientApi, callback: (tracks: List<SimpleAlbum?>) -> Unit) {
    fetchAlbumsForTracks(api, this, callback)
}

fun<T : Any, Z : AbstractPagingObject<T, Z>> AbstractPagingObject<T, Z>.fetchNextPage(callback: (AbstractPagingObject<T, Z>?) -> Unit) {
    GlobalScope.launch(Dispatchers.IO) {
        val nextPage = this@fetchNextPage.getNext()
        callback(nextPage)
    }
}

fun List<CoreObject?>.toPlayables(api: SpotifyClientApi, callback: (playables: List<PlayableItem>) -> Unit) {
    GlobalScope.launch(Dispatchers.IO) {
        val result = this@toPlayables.map { coreObject ->
            suspendCoroutine<PlayableItem> { cont ->
                when(coreObject) {
                    is SimplePlaylist -> {
                        PlayableItem.createFromCoreObject(
                            coreObject
                        ) {
                            cont.resume(it)
                        }
                    }
                    is SimpleAlbum -> {
                        PlayableItem.createFromCoreObject(
                            coreObject
                        ) {
                            cont.resume(it)
                        }
                    }
                }
            }
        }
        callback(result)
    }
}

fun List<SavedTrack?>.toAudioFeatures(api: SpotifyClientApi, callback: (audioFeatures: List<AudioFeatures>) -> Unit) {
    GlobalScope.launch(Dispatchers.IO) {
            val result = this@toAudioFeatures.map { savedTrack ->
            api.tracks.getAudioFeatures(savedTrack?.track?.uri?.id.toString())
        }
        callback(result)
    }
}

suspend fun SavedTrack.toTrackFeatures(api: SpotifyClientApi, savedTrack: SavedTrack): TrackFeatures {
    return TrackFeatures(
        savedTrack.track,
        api.tracks.getAudioFeatures(savedTrack.track.uri.id)
    )
}

suspend fun Track.toTrackFeatures(api: SpotifyClientApi): TrackFeatures {
    return TrackFeatures(
            this,
        api.tracks.getAudioFeatures(this.id)
    )
}

suspend fun Playable.toTrackFeatures(api: SpotifyClientApi, playable: Playable): TrackFeatures? {
    return if (playable.asTrack != null) {
        TrackFeatures(
            playable,
            api.tracks.getAudioFeatures(playable.asTrack?.id!!)
        )
    } else {
        null
    }
}

fun com.spotify.protocol.types.Track.toTrack(): Track {
    return Track(
        mapOf(),
        mapOf(),
        listOf(),
        "",
        this.uri,
        this.uri.toPlayableUri(),
        SimpleAlbum(
            "",
            listOf(),
            mapOf(),
            "",
            "",
            this.album.uri.toSpotifyUri(),
            this.artists.map { artist ->
                SimpleArtist(
                    mapOf(),
                    "",
                    "",
                    artist.uri.toSpotifyUri(),
                    artist.name,
                    "Artist"
                )
            },
            listOf(),
            this.album.name,
            "Album",
            Restrictions(""),
            "",
            "",
            0,
            ""
        ),
        this.artists.map { artist ->
            SimpleArtist(
                mapOf(),
                "",
                "",
                artist.uri.toSpotifyUri(),
                artist.name,
                "Artist"
            )
        },
        true,
        0,
        this.duration.toInt(),
        false,
        LinkedTrack(mapOf(), "", "", this.uri.toPlayableUri(), ""),
        this.name,
        0,
        "",
        0,
        "Track"
    )
}

fun List<Playlist>.toAudioFeaturesPlaylistTrackInfo(api: SpotifyClientApi, callback: (audioFeatures: List<AudioFeatures>) -> Unit) {
    GlobalScope.launch(Dispatchers.IO) {
        val result = this@toAudioFeaturesPlaylistTrackInfo.map { track ->
            api.tracks.getAudioFeatures(track.uri.toString())
        }
        callback(result)
    }
}

private fun fetchAlbumsForTracks(api: SpotifyClientApi, simpleTracks: List<SimpleTrack?>, callback: (simpleAlbums: List<SimpleAlbum?>) -> Unit) {
    GlobalScope.launch(Dispatchers.IO) {
        val result = simpleTracks.map { simpleTrack ->
            simpleTrack?.uri?.uri?.let { api.tracks.getTrack(it)?.album }
        }
        callback(result)
    }
}

private fun convertSimpleTracksToFullTracks(api: SpotifyClientApi, simpleTracks: List<SimpleTrack>, callback: (simpleAlbums: List<Track?>) -> Unit) {
    GlobalScope.launch(Dispatchers.IO) {
        val result = simpleTracks.map { simpleTrack ->
            api.tracks.getTrack(simpleTrack.uri.uri)
        }
        callback(result)
    }
}

private fun convertPlaylistTracksToFullTracks(simpleTracks: List<PlaylistTrack>): List<Playable?> {
    return simpleTracks.map { playlist -> playlist.track }
}

