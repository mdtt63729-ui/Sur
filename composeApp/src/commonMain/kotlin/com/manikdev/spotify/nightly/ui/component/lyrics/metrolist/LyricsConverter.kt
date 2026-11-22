package com.manikdev.spotify.nightly.ui.component.lyrics.metrolist

import com.manikdev.domain.data.model.metadata.Line
import com.manikdev.spotify.nightly.extension.parseRichSyncWords

/**
 * The one bridge this port needs: this app's [Line] in, Metrolist's [LyricsEntry] out.
 *
 * This app stores a lyric line as two strings and one rich-sync string; their renderer wants
 * millisecond longs and a list of [WordTimestamp] with start AND end on every word. Everything
 * their `Lines.kt` needs is produced here, so their renderer can read their own model and never
 * learn that this app's model exists.
 *
 * Word timings are taken from the rich-sync markers when a line has them. When it does not, they
 * are estimated across the line's own duration - the same rule the Enhanced renderer already uses -
 * so their word-by-word movement runs on every line rather than only on fully timed songs.
 */
fun List<Line>.toMetrolistEntries(offsetMs: Long = 0L): List<LyricsEntry> =
    mapNotNull { line ->
        val start = line.startTimeMs.toLongOrNull() ?: return@mapNotNull null
        val end = line.endTimeMs.toLongOrNull() ?: Long.MAX_VALUE
        val text = line.words
        if (text.isBlank()) return@mapNotNull null

        val parsed = parseRichSyncWords(text, line.startTimeMs, line.endTimeMs)
        val words =
            if (parsed != null && parsed.words.size > 1) {
                parsed.words.mapIndexed { index, word ->
                    val wordStart = word.startTimeMs
                    val wordEnd =
                        parsed.words.getOrNull(index + 1)?.startTimeMs
                            ?: parsed.lineEndTimeMs.takeIf { it != Long.MAX_VALUE && it > wordStart }
                            ?: (wordStart + 500L)
                    WordTimestamp(
                        text = word.text,
                        startTime = wordStart.toDouble(),
                        endTime = wordEnd.toDouble(),
                        hasTrailingSpace = index != parsed.words.lastIndex,
                    )
                }
            } else {
                estimatedWords(text, start, end)
            }

        LyricsEntry(
            time = start + offsetMs,
            text = parsed?.words?.joinToString(" ") { it.text } ?: text,
            words = words.takeIf { it.isNotEmpty() },
            endTime = end.takeIf { it != Long.MAX_VALUE }?.plus(offsetMs),
        )
    }

/** Spreads the words evenly across the line, holding the last one to the line's own end. */
private fun estimatedWords(
    text: String,
    start: Long,
    end: Long,
): List<WordTimestamp> {
    val pieces = text.split(' ').filter { it.isNotBlank() }
    if (pieces.isEmpty()) return emptyList()
    val lineEnd = if (end > start) end else start + 2_500L
    val step = ((lineEnd - start) / pieces.size).coerceAtLeast(120L)
    return pieces.mapIndexed { index, piece ->
        val wordStart = start + step * index
        WordTimestamp(
            text = piece,
            startTime = wordStart.toDouble(),
            endTime = (if (index == pieces.lastIndex) lineEnd else wordStart + step).toDouble(),
            hasTrailingSpace = index != pieces.lastIndex,
        )
    }
}
