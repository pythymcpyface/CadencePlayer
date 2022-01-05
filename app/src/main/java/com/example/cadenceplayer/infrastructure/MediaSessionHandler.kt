package com.example.cadenceplayer.infrastructure

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.ResultReceiver
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.RatingCompat
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import com.example.cadenceplayer.MainActivity

private const val REPEAT_ACTION_ID = "repeat_action_id"
private const val SHUFFLE_ACTION_ID = "shuffle_action_id"
private const val FAST_FORWARD_ACTION_ID = "fast_forward_action_id"
private const val REWIND_ACTION_ID = "rewind_action_id"

class MediaSessionHandler(private val context: Context
) {

    private var mediaSession: MediaSessionCompat? = null
    private var activeServicesCount = 0

    fun getMediaSession(): MediaSessionCompat {
        if (mediaSession == null) {
            mediaSession = MediaSessionCompat(context, MainActivity::javaClass.name).apply {
                setCallback(AppMediaSessionCallback())

                val activityIntent = Intent(context, MainActivity::class.java)
                val pActivityIntent = PendingIntent.getActivity(context, 0, activityIntent, 0)
                setSessionActivity(pActivityIntent)

                val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON, null, context, MainActivity::class.java)
                val pMediaButtonIntent = PendingIntent.getBroadcast(context, 0, mediaButtonIntent, 0)
                setMediaButtonReceiver(pMediaButtonIntent)
            }
            subscribeOnPlayQueue()
            subscribeOnPlaybackStateActions()
        }
        return mediaSession!!
    }

    private fun release() {
        mediaSession?.run {
            isActive = false
            release()
        }
        mediaSession = null
    }

    private fun subscribeOnPlaybackStateActions() {
    }

    private fun subscribeOnPlayQueue() {
    }

    private inner class AppMediaSessionCallback : MediaSessionCompat.Callback() {

        override fun onPlay() {
            Log.i("MediaButtonController", "onPlay")
            Toast.makeText(context, "onplay", Toast.LENGTH_LONG).show()
        }

        override fun onPause() {
            Log.i("MediaButtonController", "onPlay")
            Toast.makeText(context, "onpause", Toast.LENGTH_LONG).show()
        }

        override fun onStop() {
        }

        override fun onSkipToNext() {
        }

        override fun onSkipToPrevious() {
        }

        override fun onSeekTo(pos: Long) {
        }

        override fun onSetRepeatMode(repeatMode: Int) {
        }

        override fun onSetShuffleMode(shuffleMode: Int) {
        }

        override fun onFastForward() {
        }

        override fun onRewind() {
        }

        override fun onSetPlaybackSpeed(speed: Float) {
        }

        override fun onPlayFromMediaId(mediaId: String, extras: Bundle) {
        }

        override fun onSkipToQueueItem(id: Long) {
        }

        override fun onCustomAction(action: String, extras: Bundle) {
        }

        override fun onPlayFromSearch(query: String, extras: Bundle) {
        }

        override fun onCommand(command: String, extras: Bundle, cb: ResultReceiver) {
            super.onCommand(command, extras, cb)
        }

        override fun onPrepareFromMediaId(mediaId: String, extras: Bundle) {
            super.onPrepareFromMediaId(mediaId, extras)
        }

        override fun onPrepareFromSearch(query: String, extras: Bundle) {
            super.onPrepareFromSearch(query, extras)
        }

        override fun onPrepareFromUri(uri: Uri, extras: Bundle) {
            super.onPrepareFromUri(uri, extras)
        }

        override fun onPlayFromUri(uri: Uri, extras: Bundle) {
            super.onPlayFromUri(uri, extras)
        }

        override fun onSetRating(rating: RatingCompat) {
            super.onSetRating(rating)
        }

        override fun onSetRating(rating: RatingCompat, extras: Bundle) {
            super.onSetRating(rating, extras)
        }

        override fun onAddQueueItem(description: MediaDescriptionCompat) {
            super.onAddQueueItem(description)
        }

        override fun onAddQueueItem(description: MediaDescriptionCompat, index: Int) {
            super.onAddQueueItem(description, index)
        }

        override fun onRemoveQueueItem(description: MediaDescriptionCompat) {
            super.onRemoveQueueItem(description)
        }

        //must call super
        override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
            val keyEvent = mediaButtonEvent?.extras?.get(Intent.EXTRA_KEY_EVENT) as KeyEvent
            Log.i("MediaButtonController",
                "onMediaButtonEvent() action:${mediaButtonEvent.action} keyEvent: ${keyEvent}")
            Toast.makeText(context, keyEvent.toString(), Toast.LENGTH_LONG).show()
            return super.onMediaButtonEvent(mediaButtonEvent)
        }
    }

}