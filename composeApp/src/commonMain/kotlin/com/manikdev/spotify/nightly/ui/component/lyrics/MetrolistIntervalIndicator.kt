package com.manikdev.spotify.nightly.ui.component.lyrics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Metrolist's interval indicator, ported from their
 * `shared/src/commonMain/kotlin/com/metrolist/shared/ui/components/lyrics/IntervalIndicator.kt`.
 *
 * It is the small marker their lyrics show in the silence between two lines: a rounded bar that
 * fills with the song's progress across the gap. Their timings are kept exactly - a 300ms wait
 * before it appears, a 200ms fade in on a cubic bezier, a 500ms hold before the fade out begins,
 * and a close that swells to 1.2 before collapsing over 800ms.
 *
 * This file is self-contained on purpose: it takes the gap and a position lambda and nothing else,
 * so it can be dropped into any lyrics renderer without dragging Metrolist's model along with it.
 */
private val GapEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
private const val GAP_SHOW_DELAY_MS = 300L
private const val GAP_FADE_MS = 200
private const val GAP_HIDE_FADE_DELAY_MS = 500L
private const val GAP_ENDED_MS = 800
private const val GAP_CLOSE_MS = 850L

@Composable
fun MetrolistIntervalIndicator(
    gapStartMs: Long,
    gapEndMs: Long,
    currentPositionMs: () -> Long,
    visible: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val animAlpha = remember { Animatable(0f) }
    val animScale = remember { Animatable(if (visible) 1f else 0f) }
    var open by remember { mutableStateOf(visible) }

    LaunchedEffect(visible) {
        if (visible) {
            open = true
            animScale.snapTo(1f)
            delay(GAP_SHOW_DELAY_MS)
            animAlpha.animateTo(1f, tween(GAP_FADE_MS, easing = GapEase))
        } else {
            coroutineScope {
                if (animAlpha.value > 0f) {
                    launch {
                        delay(GAP_HIDE_FADE_DELAY_MS)
                        animAlpha.animateTo(0f, tween(GAP_FADE_MS, easing = GapEase))
                    }
                }
                launch {
                    animScale.snapTo(1f)
                    animScale.animateTo(
                        targetValue = 0f,
                        animationSpec =
                            keyframes {
                                durationMillis = GAP_ENDED_MS
                                1f at 0 using GapEase
                                1.2f at (GAP_ENDED_MS * 35 / 100) using GapEase
                                0f at GAP_ENDED_MS
                            },
                    )
                }
            }
            delay(GAP_CLOSE_MS - GAP_ENDED_MS)
            open = false
        }
    }

    if (!open) return

    val span = (gapEndMs - gapStartMs).coerceAtLeast(1L)
    val progress =
        ((currentPositionMs() - gapStartMs).toFloat() / span.toFloat()).coerceIn(0f, 1f)

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(24.dp)
                .padding(vertical = 8.dp)
                .graphicsLayer {
                    alpha = animAlpha.value
                    scaleX = animScale.value
                    scaleY = animScale.value
                },
    ) {
        // The track, then the fill that grows with the song.
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(color.copy(alpha = 0.25f), RoundedCornerShape(2.dp)),
        )
        Box(
            Modifier
                .fillMaxWidth(progress)
                .height(4.dp)
                .background(color, RoundedCornerShape(2.dp)),
        )
    }
}
