package com.manikdev.spotify.nightly.update

import kotlinx.coroutines.flow.StateFlow

/**
 * One available release, as read from the GitHub release feed.
 *
 * [notes] is the release body split into the lines the sheet shows: the bold headline first,
 * then the "• " bullets and the "– " detail lines underneath them.
 */
data class UpdateInfo(
    val versionName: String,
    val notes: List<String>,
    val apkUrl: String = "",
    val sizeBytes: Long = 0L,
    /** The ABI this build was picked for, e.g. "arm64-v8a". Shown as "Recommended for you". */
    val abiLabel: String = "",
)

/** What the update sheet is showing right now. */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState

    /** Looking up the release and choosing the APK built for this device. */
    data object Checking : UpdateUiState

    /** A newer release exists and nothing has been downloaded yet. */
    data class Available(val info: UpdateInfo) : UpdateUiState

    /** An APK for [info] is on its way to local storage. */
    data class Downloading(
        val info: UpdateInfo,
        val progress: Float,
        val bytesDownloaded: Long = 0L,
        val totalBytes: Long = 0L,
        /** Smoothed, not the instantaneous rate - a raw per-chunk figure jitters far too much to read. */
        val bytesPerSecond: Long = 0L,
    ) : UpdateUiState

    /**
     * The APK is already on disk. This is what a user who downloaded but never installed sees on
     * the next launch — the sheet offers Install, not Download, until a newer release appears.
     */
    data class ReadyToInstall(val info: UpdateInfo) : UpdateUiState

    data class Failed(
        val info: UpdateInfo?,
        val message: String,
    ) : UpdateUiState
}

/**
 * The updater as the UI sees it. Implemented per platform; the sheet never touches Android types.
 */
interface UpdateHostController {
    val state: StateFlow<UpdateUiState>

    /** Look for a newer release (and pick up an APK that is already downloaded). */
    fun check()

    /** Start (or resume) the download for the current [UpdateInfo]. */
    fun download()

    /** Launch the system installer for the downloaded APK. */
    fun install()

    /** Hide the sheet until the next launch. */
    fun dismiss()

    /**
     * Bring the sheet back after it was dismissed, without another network round trip - the
     * controller still holds the update it found and the APK it downloaded.
     */
    fun show()
}

/**
 * The update sheet's controller, published where the UI can reach it. The controller is created in
 * MainActivity, and the download button lives deep inside Home; threading a reference through every
 * screen between them would be a dozen signatures for one tap.
 */
var updateHostController: UpdateHostController? = null
