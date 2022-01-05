package com.example.cadenceplayer.spotify

import android.os.Parcelable
import com.adamratzman.spotify.SpotifyClientApi
import com.adamratzman.spotify.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

/**
 * Represents an item with a spotify uri that can be played.
 * Usually either an album or a playlist.
 */
@Parcelize
class PlayableItem internal constructor(
    val type: Type,
    val name: String,
    val authors: List<String>,
    val uri: String,
    val href: String,
    val images: @RawValue List<SpotifyImage>? = null
): Parcelable {
    private var simpleTracks: PagingObject<SimpleTrack>? = null
    private var playlistTracks: PagingObject<PlaylistTrack>? = null
    var allTracks: MutableList<Playable?> = mutableListOf()

    enum class Type {
        SIMPLE_PLAYLIST,
        SIMPLE_ALBUM
    }

    fun canFetchMoreTracks(): Boolean {
        return when (type) {
            Type.SIMPLE_PLAYLIST -> {
                (simpleTracks == null) || (simpleTracks?.next != null)
            }
            Type.SIMPLE_ALBUM -> {
                (playlistTracks == null) || (playlistTracks?.next != null)
            }
        }
    }

    suspend fun fetchNextTracks(api: SpotifyClientApi, callback: (tracks: List<Playable?>) -> Unit) {
        when (type) {
            Type.SIMPLE_PLAYLIST -> {
                fetchPlaylistTracks(api, callback)
            }
            Type.SIMPLE_ALBUM -> {
                fetchAlbumTracks(api, callback)
            }
        }
    }

    suspend fun fetchAllTracks(api: SpotifyClientApi, callback: (tracks: List<Playable?>) -> Unit) {
        if (canFetchMoreTracks()) {
            fetchNextTracks(api) {
                GlobalScope.launch(Dispatchers.IO) {
                    fetchNextTracks(api, callback)
                }
            }
        } else {
            callback(allTracks)
        }
    }

    private suspend fun fetchAlbumTracks(api: SpotifyClientApi, callback: (tracks: List<Track?>) -> Unit) {
        if (simpleTracks == null) {
            simpleTracks = api.albums.getAlbumTracks(uri)
            // Call this function again now that we have a pager object to use to fetch tracks
            fetchAlbumTracks(api, callback)
            return
        }
        simpleTracks?.items?.convertToTracks(api) { tracks ->
            simpleTracks?.fetchNextPage {
                simpleTracks = it as PagingObject<SimpleTrack>?
                // Do callback after we've fetched the next paging object
                allTracks.addAll(tracks)
                callback(tracks)
            }
        }
    }

    private suspend fun fetchPlaylistTracks(api: SpotifyClientApi, callback: (tracks: List<Playable?>) -> Unit) {
        if (playlistTracks == null) {
            playlistTracks = api.playlists.getPlaylistTracks(uri)
            // Call this function again now that we have a pager object to use to fetch tracks
            fetchPlaylistTracks(api, callback)
            return
        }
        playlistTracks?.fetchNextPage {
            playlistTracks = it as PagingObject<PlaylistTrack>?
            // Do callback after we've fetched the next paging object
            playlistTracks?.items?.convertToTracks()?.let { tracks ->
                allTracks.addAll(tracks)
                callback(tracks)
            }
        }
    }

    companion object {
        private fun createFromPlaylist(playlist: SimplePlaylist, callback: (playableItem: PlayableItem) -> Unit) {
            callback(
                PlayableItem(
                name = playlist.name,
                type = Type.SIMPLE_PLAYLIST,
                authors = listOf(playlist.owner.displayName ?: "Unknown Author"),
                uri = playlist.uri.uri,
                href = playlist.href,
                images = playlist.images
            )
            )
        }

        private fun createFromAlbum(album: SimpleAlbum, callback: (playableItem: PlayableItem) -> Unit) {
            callback(
                PlayableItem(
                name = album.name,
                type = Type.SIMPLE_ALBUM,
                authors = album.artists.map { it -> it.name },
                uri = album.uri.uri,
                href = album.href,
                images = album.images
            )
            )
        }

        fun createFromCoreObject(album: SimpleAlbum, callback: (playableItem: PlayableItem) -> Unit) {
            createFromAlbum(album, callback)
        }

        fun createFromCoreObject(playlist: SimplePlaylist, callback: (playableItem: PlayableItem) -> Unit) {
            createFromPlaylist(playlist, callback)
        }

    }
}