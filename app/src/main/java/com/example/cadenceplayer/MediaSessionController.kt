package com.example.cadenceplayer

import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.util.Log
import android.view.KeyEvent

class MediaSessionController(
    private val launcherContext: Context
) : MediaSession.Callback() {

    private val mediaSession: MediaSession = MediaSession(launcherContext, "MediaSessionControl")

    init {
        @Suppress("DEPRECATION") // media buttons do not work without flags despite what docs say
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
        mediaSession.setCallback(this)
        updatePlaybackState()
        mediaSession.isActive = true
//        mediaPlayer.addOnControlStateChangedListeners { updatePlaybackState() }
//        mediaPlayer.addOnTrackChangedListener { updatePlaybackState() }
    }

    override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
        val keyEvent = mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
        Log.d("MediaSessionControl", "onMediaButtonEvent [action = ${keyEvent?.action}, code = ${keyEvent?.keyCode}]")
        return super.onMediaButtonEvent(mediaButtonIntent)
    }

    override fun onPause() {
        Log.d("MediaSessionControl", "onPause")
//        mediaPlayer.pause()
    }

    override fun onPlay() {
        Log.d("MediaSessionControl", "onPlay")
//        mediaPlayer.play()
    }

    override fun onSkipToNext() {
        Log.d("MediaSessionControl", "onSkipToNext")
//        mediaPlayer.next()
    }

    override fun onSkipToPrevious() {
        Log.d("MediaSessionControl", "onSkipToPrevious")
//        mediaPlayer.previous()
    }

    private fun updatePlaybackState() {
//        val allowedActions = getAllowedActions()
//        val state = if (mediaPlayer.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
//        val playbackState = PlaybackState.Builder()
//            .setActions(allowedActions)
//            .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
//            .build()
//
//        Log.d(TAG, "updatePlaybackState [allowedActions = $allowedActions, state = $state]")
//        mediaSession.setPlaybackState(playbackState)
    }

//    private fun getAllowedActions(): Long {
//        var allowedActions = if (mediaPlayer.playing) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY
//
//        if (mediaPlayer.hasNext) {
//            allowedActions = allowedActions or PlaybackState.ACTION_SKIP_TO_NEXT
//        }
//
//        if (mediaPlayer.hasPrevious) {
//            allowedActions = allowedActions or PlaybackState.ACTION_SKIP_TO_PREVIOUS
//        }

//        return allowedActions
//    }

}