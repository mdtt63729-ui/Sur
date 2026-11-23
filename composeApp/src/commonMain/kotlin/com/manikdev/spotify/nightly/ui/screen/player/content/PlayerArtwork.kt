package com.manikdev.spotify.nightly.ui.screen.player.content

/**
 * The full-screen cover asks for this many pixels, whatever player style is drawing it.
 *
 * The Apple Music style has always requested its artwork at this size and rewritten the size hint
 * the url carries to match; the other styles asked for whatever the url happened to say, so their
 * covers were softer on the same song. Every style now asks the same way, which also means they
 * share one cache entry - switching style reuses the decoded bitmap instead of fetching it again.
 */
internal const val PLAYER_ART_PX = 1024

/**
 * Rewrites the size hint a YouTube image url already carries, and adds one when it carries none.
 *
 * Both forms are handled: the `w120-h120` form used by i.ytimg.com and the `=s512` form used by
 * googleusercontent. i.ytimg.com names its sizes in the path, so the small defaults are upgraded
 * to maxresdefault instead.
 */
internal fun String.playerArtworkAt(px: Int = PLAYER_ART_PX): String {
    val host = substringAfter("://", "").substringBefore('/').substringBefore(':').lowercase()

    if (host.endsWith("ytimg.com")) {
        return replace("hqdefault", "maxresdefault")
            .replace("sddefault", "maxresdefault")
            .replace("mqdefault", "maxresdefault")
    }

    val isGoogle = host == "ggpht.com" || host.endsWith(".ggpht.com") ||
        host == "googleusercontent.com" || host.endsWith(".googleusercontent.com")
    val sized = replace(Regex("""w\d+-h\d+"""), "w$px-h$px")
    val withS = sized.replace(Regex("""=s\d+"""), "=s$px")
    if (isGoogle && withS == this) return "$this=w$px-h$px"
    return if (isGoogle) withS else sized
}
