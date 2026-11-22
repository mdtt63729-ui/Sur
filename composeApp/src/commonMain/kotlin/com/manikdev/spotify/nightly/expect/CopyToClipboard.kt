package com.manikdev.spotify.nightly.expect

import androidx.compose.runtime.Composable

expect fun copyToClipboard(
    label: String,
    text: String,
)