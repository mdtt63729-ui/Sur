package com.manikdev.spotify.nightly.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manikdev.spotify.nightly.update.UpdateHostController
import com.manikdev.spotify.nightly.update.UpdateUiState
import kotlin.math.sin

/**
 * Hosts the in-app update sheet.
 *
 * The sheet is the whole update surface: it appears from the bottom, keeps the app identity and
 * both versions visible at all times, and pins its actions to the bottom so "Update" and
 * "Maybe later" never scroll away while the changelog is being read.
 */
/**
 * Colours for the update sheet: Material blue, in both themes.
 *
 * It used to follow the app's palette (white card with a blue accent in dark, theme primary in
 * light), so the sheet changed colour with whatever theme the user had picked. It is blue now on
 * purpose - the same blue in either theme - so the update surface is recognisably itself.
 */
private data class UpdatePalette(
    val container: Color,
    val onContainer: Color,
    val secondaryText: Color,
    val accent: Color,
    val chipBackground: Color,
    val chipText: Color,
)

@Composable
private fun updatePalette(): UpdatePalette {
    val scheme = MaterialTheme.colorScheme
    // luminance, not isSystemInDarkTheme(): the app has its own theme mode, and this follows it.
    val isDark = scheme.surface.luminance() < 0.5f
    return if (isDark) {
        // Material blue 200 on a deep blue surface: the accent stays legible on dark, and the
        // sheet is still blue rather than a white card.
        UpdatePalette(
            container = Color(0xFF0E1B33),
            onContainer = Color(0xFFE7EEFF),
            secondaryText = Color(0xFFA9B8D6),
            accent = Color(0xFF8AB4FF),
            chipBackground = Color(0xFF1D2F55),
            chipText = Color(0xFFC3D7FF),
        )
    } else {
        // Material blue 700 on a blue-tinted surface, the same family as the dark sheet.
        UpdatePalette(
            container = Color(0xFFEDF3FE),
            onContainer = Color(0xFF0B1B34),
            secondaryText = Color(0xFF41536F),
            accent = Color(0xFF0B57D0),
            chipBackground = Color(0xFFD6E3FF),
            chipText = Color(0xFF0B3E96),
        )
    }
}


/** One slice of the progress capsule: a tinted pill with a flat edge where it was cut. */
@Composable
private fun CapsulePiece(
    text: String,
    shape: RoundedCornerShape,
    palette: UpdatePalette,
    style: androidx.compose.ui.text.TextStyle,
    weight: FontWeight,
    startPadding: androidx.compose.ui.unit.Dp,
    endPadding: androidx.compose.ui.unit.Dp,
) {
    Surface(shape = shape, color = palette.chipBackground) {
        Text(
            text = text,
            style = style,
            fontWeight = weight,
            color = palette.chipText,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(start = startPadding, end = endPadding, top = 6.dp, bottom = 6.dp),
        )
    }
}

/** 1.4 MB/s style, one decimal - enough to read at a glance, no false precision. */
private fun formatSpeed(bytesPerSecond: Long): String {
    val kb = bytesPerSecond / 1024.0
    return when {
        kb < 1024 -> "${kb.toInt()} KB/s"
        else -> "${((kb / 1024.0) * 10).toInt() / 10.0} MB/s"
    }
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return "${(mb * 10).toInt() / 10.0} MB"
}

@Composable
fun UpdateHost(
    controller: UpdateHostController,
    appName: String = "Spotify",
    currentVersion: String = "",
    icon: ImageBitmap? = null,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    if (state is UpdateUiState.Idle) return

    UpdateSheet(
        state = state,
        appName = appName,
        currentVersion = currentVersion,
        icon = icon,
        onDownload = controller::download,
        onInstall = controller::install,
        onDismiss = controller::dismiss,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdateSheet(
    state: UpdateUiState,
    appName: String,
    currentVersion: String,
    icon: ImageBitmap?,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val palette = updatePalette()
    val scope = rememberCoroutineScope()
    // One value drives both the arrival and the departure, so the sheet cannot leave on a
    // different curve from the one it arrived on. Arrival carries a light overshoot; leaving is
    // critically damped, because a bounce on the way out reads as indecision.
    val settle = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        settle.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.72f, stiffness = 240f),
        )
    }
    val close: () -> Unit = {
        scope.launch {
            settle.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 420f),
            )
            onDismiss()
        }
    }
    if (state is UpdateUiState.Checking) {
        ModalBottomSheet(onDismissRequest = close, sheetState = sheetState) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Finding the best build for your device",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Matching the APK to this phone's ABI…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        return
    }

    val info =
        when (state) {
            is UpdateUiState.Available -> state.info
            is UpdateUiState.Downloading -> state.info
            is UpdateUiState.ReadyToInstall -> state.info
            is UpdateUiState.Failed -> state.info
            UpdateUiState.Checking, UpdateUiState.Idle -> null
        } ?: return

    // Reading state: collapsed shows the first few lines, "Read more" opens the rest, and the
    // scrollable area is capped at half the screen so the actions below stay reachable.
    var expanded by remember(info.versionName) { mutableStateOf(false) }
    // 320dp is half of a typical phone viewport; the sheet never grows past this, so the
    // pinned actions stay reachable while the notes scroll.
    val halfScreen = 320.dp

    ModalBottomSheet(
        onDismissRequest = close,
        sheetState = sheetState,
        containerColor = palette.container,
    ) {
        // The content rides the same value as the sheet: fade, a few dp of lift and a hair of
        // scale. Alpha and translation cost no pixels, so nothing here softens what it draws.
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val t = settle.value
                        alpha = t.coerceIn(0f, 1f)
                        translationY = (1f - t) * 28.dp.toPx()
                        val scale = 0.97f + 0.03f * t
                        scaleX = scale
                        scaleY = scale
                    }.padding(horizontal = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The app's own launcher icon, clipped to a rounded square.
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp)),
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp),
                    ) {}
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.onContainer,
                    )
                    // One Text, not three in a Row. Three separate Texts each wrapped on their
                    // own when the space ran out, so "2.23.3 -> 2.23.4" broke across two lines and
                    // the new version split down the middle ("2.2" over "3.4"). A single
                    // AnnotatedString cannot break between the pieces: softWrap is off and it
                    // ellipsises instead.
                    Text(
                        text =
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = palette.secondaryText)) {
                                    append(currentVersion)
                                }
                                withStyle(SpanStyle(color = palette.secondaryText)) {
                                    append("  \u2192  ")
                                }
                                withStyle(SpanStyle(color = palette.accent, fontWeight = FontWeight.SemiBold)) {
                                    append(info.versionName)
                                }
                            },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // A quiet overline, so the block below reads as a changelog rather than as stray text
            // under the version row.
            Text(
                text = "WHAT'S NEW",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = palette.secondaryText,
                modifier = Modifier.padding(bottom = 6.dp),
            )

            Column(
                modifier =
                    Modifier
                        .heightIn(max = if (expanded) halfScreen else 140.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                if (info.notes.isEmpty()) {
                    Text(
                        text = "No changelog was attached to this release.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.secondaryText,
                    )
                }
                info.notes.forEach { line ->
                    val isHeadline = !line.startsWith("•") && !line.startsWith("–") && !line.startsWith("-")
                    Text(
                        text = if (isHeadline) line else line.removePrefix("• ").let { "• $it" },
                        style =
                            if (isHeadline) {
                                MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            } else {
                                MaterialTheme.typography.bodySmall
                            },
                        color = if (isHeadline) palette.onContainer else palette.secondaryText,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }

            if (!expanded && info.notes.size > 2) {
                TextButton(onClick = { expanded = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text("Read more")
                }
            }

            Spacer(Modifier.height(14.dp))

            // Pinned actions. The row crossfades to the progress readout while downloading and
            // never leaves the bottom of the sheet.
            AnimatedContent(
                targetState = state,
                // THE actual cause of the glitch: every progress update produces a new
                // UpdateUiState.Downloading with a different progress, so AnimatedContent saw a
                // new target on every tick and re-ran the fade+slide - the whole actions row
                // crossfaded and slid on each percentage step. The key pins the transition to the
                // kind of state, so progress changes update in place and only a real state change
                // (checking -> downloading -> done) animates.
                contentKey = { it::class },
                transitionSpec = {
                    (fadeIn() + slideInVertically { it / 3 }) togetherWith (fadeOut() + slideOutVertically { -it / 3 })
                },
                label = "update-actions",
            ) { current ->
                when (current) {
                    is UpdateUiState.Downloading -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // A plain, straight bar. The wavy ribbon read as decoration and made
                            // the progress hard to judge; a line and two numbers say everything.
                            // Material 3's own indicator, exactly as the design shows: a plain
                            // bar until a tenth of the way in, the wavy one after. Only the
                            // composable changes at that point - neither is re-created per
                            // percentage step, which is what made the old bar jump.
                            // Smoothed, so a new percentage moves the bar instead of snapping it.
                            // Every raw value used to be handed straight to the indicator, and that
                            // step-per-update is what read as a glitch.
                            val raw = current.progress.coerceIn(0f, 1f)
                            val shown by animateFloatAsState(
                                targetValue = raw,
                                animationSpec = tween(durationMillis = 320, easing = LinearEasing),
                                label = "updateProgress",
                            )
                            // A fixed-height box around both bars: switching composables at 10%
                            // used to re-measure the row and shift everything below it, which is
                            // the jump that read as a glitch. Same height in, same height out.
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier.fillMaxWidth().height(14.dp),
                                contentAlignment = androidx.compose.ui.Alignment.CenterStart,
                            ) {
                            if (shown < 0.10f) {
                                LinearProgressIndicator(
                                    progress = { shown },
                                    // 4dp, matching the stroke the wavy indicator draws. It was 8dp, so the bar
                                    // looked thick for the first 10% and thinned at the swap - the
                                    // change in thickness you saw, not a change in progress.
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                                    color = palette.accent,
                                    trackColor = palette.accent.copy(alpha = 0.18f),
                                    gapSize = 0.dp,
                                    drawStopIndicator = {},
                                )
                            } else {
                                LinearWavyProgressIndicator(
                                    progress = { shown },
                                    // Same height as the straight bar, so the swap at 10% cannot jump, and small
                                    // enough that the default wave reads as fine ripples rather than big swells.
                                    modifier = Modifier.fillMaxWidth().height(8.dp),
                                    color = palette.accent,
                                    trackColor = palette.accent.copy(alpha = 0.18f),
                                )
                            }
                            }
                            Spacer(Modifier.height(10.dp))
                            // Three pieces of one capsule, each cut on its own: the percentage,
                            // the transfer rate, and how much of the file has arrived. The outer
                            // two keep a rounded end and a flat inner one, the middle is flat on
                            // both sides, so the row reads as a single capsule sliced twice.
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CapsulePiece(
                                    text = "${(current.progress * 100).toInt()}%",
                                    shape =
                                        RoundedCornerShape(
                                            topStart = 50.dp,
                                            bottomStart = 50.dp,
                                            topEnd = 0.dp,
                                            bottomEnd = 0.dp,
                                        ),
                                    palette = palette,
                                    style = MaterialTheme.typography.labelLarge,
                                    weight = FontWeight.SemiBold,
                                    startPadding = 14.dp,
                                    endPadding = 10.dp,
                                )
                                if (current.bytesPerSecond > 0) {
                                    Spacer(Modifier.width(6.dp))
                                    CapsulePiece(
                                        text = formatSpeed(current.bytesPerSecond),
                                        shape = RoundedCornerShape(0.dp),
                                        palette = palette,
                                        style = MaterialTheme.typography.labelMedium,
                                        weight = FontWeight.Medium,
                                        startPadding = 10.dp,
                                        endPadding = 10.dp,
                                    )
                                }
                                if (current.totalBytes > 0) {
                                    Spacer(Modifier.width(6.dp))
                                    CapsulePiece(
                                        text =
                                            "${formatBytes(current.bytesDownloaded)} / " +
                                                formatBytes(current.totalBytes),
                                        shape =
                                            RoundedCornerShape(
                                                topStart = 0.dp,
                                                bottomStart = 0.dp,
                                                topEnd = 50.dp,
                                                bottomEnd = 50.dp,
                                            ),
                                        palette = palette,
                                        style = MaterialTheme.typography.labelMedium,
                                        weight = FontWeight.Medium,
                                        startPadding = 10.dp,
                                        endPadding = 14.dp,
                                    )
                                }
                            }
                        }
                    }

                    is UpdateUiState.ReadyToInstall -> {
                        Button(
                            onClick = onInstall,
                            shape = CircleShape,
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = palette.accent),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Install update")
                        }
                    }

                    is UpdateUiState.Failed -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = current.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = onDownload,
                                shape = CircleShape,
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = palette.accent),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Try again")
                            }
                        }
                    }

                    else -> {
                        Button(
                            onClick = onDownload,
                            shape = CircleShape,
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = palette.accent),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Update now")
                        }
                    }
                }
            }

            TextButton(onClick = close, modifier = Modifier.fillMaxWidth()) {
                Text("Maybe later")
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/**
 * Wavy determinate progress: a sine ribbon filled up to [progress].
 *
 * Drawn by hand rather than with the Material wavy indicator so it behaves identically on
 * Android and desktop — the multiplatform material3 artifact does not ship the wavy variants.
 */
@Composable
fun WavyProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    waveColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    val track = trackColor
    val wave = waveColor
    Canvas(modifier = modifier) {
        val amplitude = size.height / 3.2f
        val midY = size.height / 2f
        val wavelength = size.width / 6f
        fun pathFor(upTo: Float): Path {
            val path = Path()
            var x = 0f
            path.moveTo(0f, midY)
            while (x <= upTo) {
                path.lineTo(x, midY + amplitude * sin((x / wavelength) * 2f * Math.PI.toFloat()))
                x += 4f
            }
            return path
        }
        drawPath(pathFor(size.width), color = track, style = Stroke(width = size.height / 3f))
        drawPath(
            path = pathFor(size.width * progress.coerceIn(0f, 1f)),
            brush = Brush.horizontalGradient(listOf(wave, wave.copy(alpha = 0.6f))),
            style = Stroke(width = size.height / 3f),
        )
        drawCircle(
            color = wave,
            radius = size.height / 5f,
            center = Offset(size.width * progress.coerceIn(0f, 1f), midY),
        )
    }
}
