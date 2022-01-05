package com.example.cadenceplayer.model

import com.adamratzman.spotify.models.AudioFeatures
import com.adamratzman.spotify.models.Playable
import kotlinx.serialization.Serializable

@Serializable
data class TrackFeatures(
    val playable: Playable,
    val audioFeatures: AudioFeatures
) {
}