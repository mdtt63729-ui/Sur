package com.manikdev.domain.manager

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the song playing now should be shown as video instead of just sound.
 *
 * Deliberately NOT the stored `watchVideoInsteadOfPlayingAudio` preference: that one is a global
 * switch, changed in settings, and it decides for every song at once. The player's capsule flips
 * this per song, and the media source builder reads it - so choosing Video re-resolves this song
 * with the video track merged, and choosing Audio builds an audio-only source, meaning no video is
 * fetched at all. It starts false: every song opens on audio.
 */
object VideoModeState {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    val isVideo: Boolean get() = _enabled.value

    fun set(video: Boolean) {
        _enabled.value = video
    }

    fun reset() {
        _enabled.value = false
    }
}
