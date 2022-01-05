package com.example.cadenceplayer.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.greenrobot.eventbus.EventBus

class SpotifyBroadcastReceiver : BroadcastReceiver() {
    internal object BroadcastTypes {
        const val SPOTIFY_PACKAGE = "com.spotify.music"
        const val PLAYBACK_STATE_CHANGED = SPOTIFY_PACKAGE + ".playbackstatechanged"
        const val QUEUE_CHANGED = SPOTIFY_PACKAGE + ".queuechanged"
        const val METADATA_CHANGED = SPOTIFY_PACKAGE + ".metadatachanged"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // This is sent with all broadcasts, regardless of type. The value is taken from
        // System.currentTimeMillis(), which you can compare to in order to determine how
        // old the event is.
        val timeSentInMs = intent.getLongExtra("timeSent", 0L)
        when (intent.action) {

            BroadcastTypes.METADATA_CHANGED -> {
                val trackId = intent.getStringExtra("id") ?: ""
                val artistName = intent.getStringExtra("artist") ?: ""
                val albumName = intent.getStringExtra("album") ?: ""
                val trackName = intent.getStringExtra("track") ?: ""
                val trackLengthInSec = intent.getIntExtra("length", 0)

                EventBus.getDefault().post(
                    OnReceiveMetadata(
                    timeSentInMs,
                    trackId,
                    artistName,
                    albumName,
                    trackName,
                    trackLengthInSec
                )
                )
            }

            BroadcastTypes.PLAYBACK_STATE_CHANGED -> {
                val playing = intent.getBooleanExtra("playing", false)
                val positionInMs = intent.getIntExtra("playbackPosition", 0)
                EventBus.getDefault().post(
                    OnReceivePlayback(
                    timeSentInMs,
                    playing,
                    positionInMs
                )
                )
            }

            BroadcastTypes.QUEUE_CHANGED -> {

            }
        }

    }
}
