package com.example.cadenceplayer.broadcast

class OnReceivePlayback(
    val timeSentInMs: Long,
    val playing: Boolean,
    val positionInMs: Int
    )