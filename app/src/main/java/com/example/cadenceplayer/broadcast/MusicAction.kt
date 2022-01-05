package com.example.cadenceplayer.broadcast

enum class MusicAction(val key: String) {
    ACTION_TOGGLE_PLAYBACK("com.aderevyanko.musicplayer.action.TOGGLE_PLAYBACK"),
    ACTION_PLAY("com.aderevyanko.musicplayer.action.PLAY"),
    ACTION_PAUSE("com.aderevyanko.musicplayer.action.PAUSE"),
    ACTION_STOP("com.aderevyanko.musicplayer.action.STOP"),
    ACTION_SKIP("com.aderevyanko.musicplayer.action.SKIP"),
    ACTION_REWIND("com.aderevyanko.musicplayer.action.REWIND"),
    ACTION_URL("com.aderevyanko.musicplayer.action.URL");

    companion object {
        fun from(key: String): MusicAction {
            for (action in values()) {
                if (action.key == key) {
                    return action
                }
            }

            throw IllegalArgumentException()
        }
    }
}