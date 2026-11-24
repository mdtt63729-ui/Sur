package com.manikdev.spotify.nightly.ui.screen.player.content.applemusic

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.manikdev.domain.data.model.metadata.Line
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import com.manikdev.spotify.nightly.extension.WordTiming
import com.manikdev.spotify.nightly.extension.ParsedRichSyncLine
import com.manikdev.spotify.nightly.extension.parseRichSyncWords
import com.manikdev.spotify.nightly.ui.component.rememberLyricLayoutDirection
import com.manikdev.spotify.nightly.ui.screen.player.content.NowPlayingContentState
import com.manikdev.spotify.nightly.ui.screen.player.content.stripRichSyncTimestamps
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * The line being sung, directly over the progress bar. Tapping it opens the Lyrics tab.
 *
 * Word-synced lyrics are lit as they are sung: the line is drawn twice, dim and bright, and the
 * bright copy is clipped to where the singer has got to. Clipping in the draw phase, off a playhead
 * carried forward on the frame clock, is what lets the light travel smoothly INSIDE a word — coloured
 * spans can only change a whole word at a time. Line-synced lyrics light the whole line at once.
 *
 * Before the first line starts the slot holds three dots rather than nothing, so an intro does not
 * leave a gap between the title and the bar.
 *
 * Always one line tall, empty or not, so the controls below it never move when a line ends or a
 * track has no synced lyrics.
 */
@Composable
internal fun AppleMusicLyricStrip(
    state: NowPlayingContentState,
    typography: AppleMusicTypography,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lyrics = state.screenData.lyricsData?.lyrics
    val syncType = lyrics?.syncType
    val synced = lyrics?.lines != null && syncType != null && syncType != "UNSYNCED"
    val richSynced = synced && syncType == "RICH_SYNCED"
    // The index is -1 only until the first line starts: once a line has been reached it is held
    // until the next one, and the last is held to the end of the track.
    val line = if (synced) lyrics.lines?.getOrNull(state.currentLyricLineIndex) else null

    val playhead =
        rememberSweepPlayhead(
            rawMs = state.timelineState.current - state.lyricsOffsetMs,
            // Line-synced lines are swept too now (their word timings are derived below), so the
            // playhead has to run for them as well - it used to stop whenever the sheet was not
            // rich-synced, which is why only rich-synced songs animated word by word.
            running = line != null && state.controllerState.isPlaying,
        )

    AnimatedContent(
        // Keyed on the line itself: the index alone would miss a lyrics swap that lands on the same
        // index, and the text alone would merge two identical lines sung back to back.
        targetState =
            when {
                line != null -> StripContent.Sung(line)
                synced -> StripContent.Waiting
                else -> null
            },
        transitionSpec = {
            // The new line rises into place as the old one rises away, a third of a line each way —
            // the direction lyrics travel in the Lyrics tab, so the two read as the same motion.
            // Springs rather than tweens. A tween has a fixed duration and stops dead at its end;
            // a spring arrives when the motion is spent and settles, so a line change reads as
            // movement that finishes rather than movement that runs out. Damping is high enough
            // that it settles without bouncing - the bouncy feel belongs to the capsule, not here.
            val enter =
                fadeIn(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) +
                    slideInVertically(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) { (it * 0.35f).toInt() }
            val exit =
                fadeOut(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)) +
                    slideOutVertically(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)) { -(it * 0.35f).toInt() }
            (enter togetherWith exit).using(SizeTransform(clip = false))
        },
        label = "appleMusicLyricStrip",
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(
                    enabled = synced,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
    ) { shown ->
        val lineText = (shown as? StripContent.Sung)?.line?.displayText().orEmpty()
        val lineDirection = rememberLyricLayoutDirection(lineText)
        CompositionLocalProvider(LocalLayoutDirection provides lineDirection) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
                // Every branch starts from a one-line Text, which is what keeps the slot the same height.
                when (shown) {
                    is StripContent.Sung -> {
                        val parsed =
                            remember(shown.line, richSynced) {
                                if (richSynced) {
                                    parseRichSyncWords(shown.line.words, shown.line.startTimeMs, shown.line.endTimeMs)
                                } else {
                                    // A line-synced sheet carries one time for the whole line. Splitting
                                    // its text into words and giving each a share of the line's own
                                    // duration - by length, because a long word is sung for longer than
                                    // a short one - lets the same sweep run over it. Nothing is guessed
                                    // about when the singer starts; only how the line's span divides.
                                    lineSyncedWordTimings(shown.line)
                                }
                            }
                        if (parsed != null) {
                            SweptLine(parsed = parsed, playhead = playhead, typography = typography)
                        } else {
                            StripText(text = shown.line.displayText(), typography = typography)
                        }
                    }

                    StripContent.Waiting -> {
                        StripText(text = "", typography = typography)
                        WaitingDots()
                    }

                    null -> StripText(text = "", typography = typography)
                }
            }
        }
    }
}

// What the strip is showing: a line being sung, or the wait before the first one.
private sealed interface StripContent {
    data class Sung(
        val line: Line,
    ) : StripContent

    data object Waiting : StripContent
}

@Composable
private fun StripText(
    text: String,
    typography: AppleMusicTypography,
) {
    Text(
        text = text,
        style = typography.lyricStrip.copy(textDirection = TextDirection.ContentOrLtr),
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, animationMode = MarqueeAnimationMode.Immediately),
    )
}

// Three dots in a row, centred on the line's height by the Box around them.
@Composable
private fun WaitingDots() {
    Row(horizontalArrangement = Arrangement.spacedBy(WAITING_DOT_GAP)) {
        repeat(3) {
            Box(modifier = Modifier.size(WAITING_DOT_SIZE).clip(CircleShape).background(UNSUNG))
        }
    }
}

@Composable
private fun SweptLine(
    parsed: ParsedRichSyncLine,
    playhead: State<Long>,
    typography: AppleMusicTypography,
) {
    val text = remember(parsed) { parsed.words.joinToString(" ") { it.text } }
    val lineStart = parsed.lineStartTimeMs
    val lineEnd = parsed.lineEndTimeMs

    // Letter by letter, from the line's own span: how many characters the song has reached.
    //
    // This used to be drawn as two copies of the line - one dim, one bright - with the bright one
    // clipped at an x position. That is what tore Devanagari and Bengali apart: the clip lands
    // mid-cluster, so half of a letter and its matra came from the bright layer and half from the
    // dim one, at different subpixel positions. One Text with per-character colour cannot do that -
    // the whole line is shaped once, and only the ink colour changes. Latin lines look the same as
    // before; complex scripts stop breaking.
    //
    // Recomposition happens once per character, not once per frame, because the derived state
    // notifies only when the count changes.
    val lit by remember(parsed, text) {
        derivedStateOf {
            if (lineEnd <= lineStart || text.isEmpty()) {
                0
            } else {
                val progress = ((playhead.value - lineStart).toFloat() / (lineEnd - lineStart)).coerceIn(0f, 1f)
                (progress * text.length).toInt().coerceIn(0, text.length)
            }
        }
    }
    val sung = typography.lyricStrip.color
    val annotated =
        remember(text, lit, sung) {
            buildAnnotatedString {
                append(text)
                if (lit > 0) addStyle(SpanStyle(color = sung), 0, lit)
                if (lit < text.length) addStyle(SpanStyle(color = UNSUNG), lit, text.length)
            }
        }
    Text(
        text = annotated,
        // Poppins has no Bengali or Devanagari glyphs, so Android filled each missing one from a
        // fallback face a glyph at a time and the marks ended up shaped by a different font from
        // their letters. A line in a complex script is drawn in the system family, which shapes the
        // whole word with one face. Latin lines keep Poppins.
        style =
            typography.lyricStrip.copy(
                textDirection = TextDirection.ContentOrLtr,
                fontFamily = if (text.any { it.code > 0x024F }) FontFamily.Default else typography.lyricStrip.fontFamily,
            ),
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, animationMode = MarqueeAnimationMode.Immediately),
    )
}

/**
 * Word timings for a line that only knows when the LINE starts.
 *
 * The line's own span is divided between its words in proportion to their length, which follows
 * speech far more closely than an even split: "extraordinary" occupies several times the time of
 * "a". When the sheet gives no usable end time, the line is given a span from its word count so the
 * sweep still moves instead of stalling at the first word.
 */
private fun lineSyncedWordTimings(line: Line): ParsedRichSyncLine? {
    val text = line.displayText().trim()
    if (text.isEmpty()) return null
    val start = line.startTimeMs.toLongOrNull() ?: return null
    val words = text.split(" ").filter { it.isNotBlank() }
    if (words.isEmpty()) return null

    val endRaw = line.endTimeMs.toLongOrNull()
    val span =
        if (endRaw != null && endRaw > start) {
            endRaw - start
        } else {
            // No end time: assume a comfortable pace rather than freezing the sweep.
            (words.size * FALLBACK_MS_PER_WORD).coerceAtLeast(FALLBACK_MS_PER_WORD)
        }

    val totalChars = words.sumOf { it.length }.coerceAtLeast(1)
    var cursor = start
    val timings =
        words.map { word ->
            val share = (span * word.length / totalChars).coerceAtLeast(MIN_WORD_MS)
            val timing = WordTiming(text = word, startTimeMs = cursor)
            cursor += share
            timing
        }
    return ParsedRichSyncLine(words = timings, lineStartTimeMs = start, lineEndTimeMs = start + span)
}

// Where the light has got to: every word already sung, plus the part of the current one its own
// duration says has gone by. A word runs until the next one starts; the last runs to the line's end.
private fun sweepX(
    parsed: ParsedRichSyncLine,
    ranges: List<IntRange>,
    layout: TextLayoutResult,
    positionMs: Long,
): Float? {
    val words = parsed.words
    val current = words.indexOfLast { it.startTimeMs <= positionMs }
    if (current < 0) return null
    val range = ranges[current]
    val startMs = words[current].startTimeMs
    val endMs =
        words.getOrNull(current + 1)?.startTimeMs
            ?: parsed.lineEndTimeMs.takeIf { it != Long.MAX_VALUE && it > startMs }
            ?: (startMs + LAST_WORD_FALLBACK_MS)
    val progress = ((positionMs - startMs).toFloat() / (endMs - startMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
    val lastIndex = (layout.layoutInput.text.length).coerceAtLeast(1)
    val left = layout.getHorizontalPosition(range.first.coerceIn(0, lastIndex), usePrimaryDirection = true)
    val right = layout.getHorizontalPosition((range.last + 1).coerceIn(0, lastIndex), usePrimaryDirection = true)
    return left + (right - left) * progress
}

/**
 * The playhead the sweep is drawn from. Positions arrive every 50 ms, a frame or two late and never
 * on a frame. Restarting from each one — rememberSmoothPlayhead, which a word-at-a-time wipe can
 * afford — makes a continuous sweep stall and then jump by that much several times a second. This
 * one keeps its own time on the frame clock and only leans toward the reports, so it moves at a
 * steady speed and never steps back. A report further off than [SWEEP_SNAP_MS] is a seek, and it
 * goes straight there. While [running] is false it simply follows the reports.
 */
@Composable
internal fun rememberSweepPlayhead(
    rawMs: Long,
    running: Boolean,
): State<Long> {
    val playhead = remember { mutableLongStateOf(rawMs) }
    val latestRaw by rememberUpdatedState(rawMs)
    LaunchedEffect(running) {
        if (!running) {
            snapshotFlow { latestRaw }.collect { playhead.longValue = it }
            return@LaunchedEffect
        }
        var reported = latestRaw
        var reportedAt = -1L
        var lastFrame = -1L
        var position = playhead.longValue.toDouble()
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                if (reportedAt < 0L || latestRaw != reported) {
                    reported = latestRaw
                    reportedAt = now
                }
                // Where the last report puts the song by now. Capped, so a player that has stalled
                // (no new report while it buffers) cannot drag the light on without it.
                val target = reported + ((now - reportedAt) / 1_000_000.0).coerceAtMost(SWEEP_MAX_EXTRAPOLATION_MS)
                val step = if (lastFrame < 0L) 0.0 else (now - lastFrame) / 1_000_000.0
                lastFrame = now
                position =
                    if (abs(target - position) > SWEEP_SNAP_MS) {
                        target
                    } else {
                        // Close a share of the gap each frame, in proportion to the frame's length so
                        // 60 Hz and 120 Hz settle alike; max() keeps it from ever running backwards.
                        val advanced = position + step
                        max(position, advanced + (target - advanced) * (step / SWEEP_CATCH_UP_MS).coerceAtMost(1.0))
                    }
                playhead.longValue = position.roundToLong()
            }
        }
    }
    return playhead
}

private fun Line.displayText(): String = words.stripRichSyncTimestamps()

// The unsung part of a word-synced line: present enough to read ahead, clearly not lit yet.
private val UNSUNG = Color.White.copy(alpha = 0.45f)

private val WAITING_DOT_SIZE = 5.dp
private val WAITING_DOT_GAP = 4.dp

// Line change: long enough to read as travel, short enough that fast lines do not queue.
private const val LINE_CHANGE_MS = 340

// A line's end time is often absent; the last word is then given this long to light.
private const val LAST_WORD_FALLBACK_MS = 600L

// How the sweep's own clock tracks the reported position: the time it takes to close most of a
// gap, the largest gap it closes gradually rather than jumping, and how far past the last report
// it will run on its own.
// How quickly the light closes the gap to where the singing actually is. 150 was loose enough
// that the sweep trailed the voice by a visible fraction of a syllable and then hurried to catch
// up, which reads as a stutter on fast lines. 90 keeps it on the singer without ever running
// ahead, because the position still advances at most one frame at a time.
// Used only when a line-synced sheet gives no end time: how long one word is assumed to last.
private const val FALLBACK_MS_PER_WORD = 320L
private const val MIN_WORD_MS = 80L

private const val SWEEP_CATCH_UP_MS = 90.0
private const val SWEEP_SNAP_MS = 500.0
private const val SWEEP_MAX_EXTRAPOLATION_MS = 250.0
