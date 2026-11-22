package com.manikdev.spotify.nightly.expect

actual fun getDownloadFolderPath(): String = System.getProperty("user.home") + "/Downloads"