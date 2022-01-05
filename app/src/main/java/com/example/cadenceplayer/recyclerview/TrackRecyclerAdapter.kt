package com.example.cadenceplayer.recyclerview

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView
import com.example.cadenceplayer.R
import com.example.cadenceplayer.databinding.TrackViewBinding
import com.example.cadenceplayer.model.TrackFeatures
import com.example.cadenceplayer.spotify.PlayableItem
import com.squareup.picasso.Picasso

class TrackRecyclerAdapter(
    private val listSelector: RecyclerType,
    private val trackList: ArrayList<TrackFeatures>,
    private val playlistList: ArrayList<PlayableItem>,
    private val onClickTrackListener: (View, TrackFeatures) -> Unit,
    private val onClickPlaylistListener: (View, PlayableItem) -> Unit
) : RecyclerView.Adapter<TrackRecyclerAdapter.MyViewHolder>() {

    class MyViewHolder(private val binding: TrackViewBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bindTracks(
            trackFeatures: TrackFeatures,
            onClickTrackListener: (View, TrackFeatures) -> Unit
        ) {
            val track = trackFeatures.playable.asTrack?.name
            val artists = mutableListOf<String>()
            for (artist in trackFeatures.playable.asTrack?.artists!!) {
                artists.add(artist.name)
            }
            val artist = artists.joinToString()
            val album = trackFeatures.playable.asTrack?.album?.name
            val tempo = "${trackFeatures.audioFeatures.tempo} bpm"

            val albumImageUrl = trackFeatures.playable.asTrack?.album!!.images[0].url

            binding.trackName.text = track
            binding.artistName.text = artist
            binding.albumName.text = album
            binding.tempo.text = tempo

            val albumIcon = binding.albumCover

            Picasso.get().load(albumImageUrl).into(albumIcon)

            binding.albumCover.setOnClickListener {
                Log.i("Spotify", "View clicked")
                onClickTrackListener(it, trackFeatures)
            }
        }

        fun bindPlaylists(playlist: PlayableItem, onClickPlaylistListener: (View, PlayableItem) -> Unit) {
            val playlistName = playlist.name

            val playlistIcon = binding.albumCover

            binding.trackName.text = playlistName
            binding.artistName.text = ""
            binding.albumName.text = ""
            binding.tempo.text = ""

            binding.albumCover.setOnClickListener {
                Log.i("Spotify", "View clicked on playlist ${playlist.uri}")
                onClickPlaylistListener(it, playlist)
            }

            // If there are no images use default
            try {
                if (playlist.name == "Saved Tracks") {
                    playlistIcon.setImageResource(R.drawable.heart_64)
                } else {
                    val playlistImageUrl = playlist.images?.get(0)?.url
                    Picasso.get().load(playlistImageUrl).into(playlistIcon)
                }
            } catch (e: IndexOutOfBoundsException) {
                playlistIcon.setImageResource(R.drawable.ic_launcher_foreground)
            }
        }
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): MyViewHolder {
        val binding = TrackViewBinding.inflate(LayoutInflater.from(parent.context), parent, false)

        return MyViewHolder((binding))
    }

    // Replace the contents of a view (invoked by the layout manager)
    @RequiresApi(Build.VERSION_CODES.M)
    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        // Get each item at each position and apply to layout
        if (listSelector == RecyclerType.Track) {
            val trackFeatures = trackList[position]
            holder.bindTracks(trackFeatures, onClickTrackListener)
        } else if (listSelector == RecyclerType.Playlist) {
            val playlist = playlistList[position]
            holder.bindPlaylists(playlist, onClickPlaylistListener)
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount(): Int {
        return if (listSelector == RecyclerType.Track) {
            trackList.size
        } else {
            playlistList.size
        }
    }
}