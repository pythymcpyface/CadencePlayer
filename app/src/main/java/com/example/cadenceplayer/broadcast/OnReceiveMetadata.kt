package com.example.cadenceplayer.broadcast

class OnReceiveMetadata(
    val timeSentInMs: Long,
    val trackId: String,
    val artistName: String,
    val albumName: String,
    val trackName: String,
    val trackLengthInSec: Int
    )