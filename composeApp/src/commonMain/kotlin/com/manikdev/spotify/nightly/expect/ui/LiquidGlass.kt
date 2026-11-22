package com.manikdev.spotify.nightly.expect.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.sign
import com.kyant.backdrop.backdrops.layerBackdrop as nativeBackdrop

/**
 * The backdrop a glass surface samples from.
 *
 * This used to be an `expect class` with a real Android actual and a no-op JVM one,
 * because desktop was assumed to have no Kyant backend. It does: `io.github.kyant0:backdrop`
 * is a KMP artifact declared in `commonMain`, and its desktop variant ships the same
 * API backed by `SkikoRuntimeShader`. So there is nothing left to abstract over — the
 * whole effect is plain common code, and the alias only survives so the ~20 call sites
 * keep reading `PlatformBackdrop`.
 */
typealias PlatformBackdrop = LayerBackdrop

/** Marks a composable as the source layer that sibling glass surfaces refract. */
fun Modifier.layerBackdrop(backdrop: PlatformBackdrop): Modifier = this.nativeBackdrop(backdrop)

@Composable
fun rememberBackdrop(color: Color): PlatformBackdrop =
    rememberLayerBackdrop {
        drawRect(color)
        drawContent()
    }

/**
 * How many times each glass layer has been drawn.
 *
 * This was one global counter, which quietly stopped throttling the moment a second glass surface
 * was on screen: four surfaces incremented it four times per frame, so "every fifth" arrived every
 * one and a quarter frames and every surface recorded almost every frame. That is why turning
 * liquid glass on made the lag so much worse than the single-surface case it was tuned for.
 * A counter per layer counts that layer's own draws, which is what the cadence was always meant
 * to mean.
 */
private val recordCounters = mutableMapOf<GraphicsLayer, Int>()

fun Modifier.drawBackdropCustomShape(
    backdrop: PlatformBackdrop,
    layer: GraphicsLayer,
    luminanceAnimation: Float,
    shape: Shape,
): Modifier =
    this.drawBackdrop(
        backdrop = backdrop,
        effects = {
            val l = (luminanceAnimation * 2f - 1f).let { sign(it) * it * it }
            vibrancy()
            colorControls(
                // Neutral brightness/contrast: the old curve washed the glass out to white on bright
                // backgrounds ("đục trắng"). Darkening is done in onDrawSurface (keeps "đục đen").
                brightness = 0.05f,
                contrast = 1f,
                // No saturation boost. 1.5 turned whatever bright artwork scrolled under the bar
                // into a saturated smear - a red sleeve became a glowing orange blob inside the
                // nav bar and the search circle. Glass shows what is behind it; it does not make
                // it louder.
                saturation = 1f,
            )
            // Blur is the single most expensive thing on this screen: it runs a render
            // shader over the whole surface every frame, and on 120 Hz panels the 16 dp
            // radius was enough to drop frames while scrolling. A smaller radius plus a
            // tighter lens keeps the look but costs roughly half the fill rate.
            blur(
                if (l > 0f) {
                    lerp(5f.dp.toPx(), 10f.dp.toPx(), l)
                } else {
                    lerp(5f.dp.toPx(), 2f.dp.toPx(), -l)
                },
            )
            // The lens is a per-pixel refraction pass. In high performance mode it is the first
            // thing to go: the blur and the tint still read as glass, and the frame budget stops
            // paying for a magnification nobody notices while scrolling.
            if (!PerformanceSettings.highPerformanceMode) {
                lens(10f.dp.toPx(), size.minDimension / 2f, true)
            }
        },
        onDrawBackdrop = { drawBackdrop ->
            // Recording a GraphicsLayer is the expensive half of this effect and it used to run
            // on every single frame. The glass only needs a fresh sample of what is behind it,
            // and 3 frames of staleness is invisible at 120 Hz while costing a third of the GPU
            // time — this is what made the bottom bar and the mini player stutter while scrolling.
            drawBackdrop()
            // High performance mode halves the recording rate again: a tenth of the frames is
            // still a fresh enough sample behind a blurred surface, and it is the difference
            // between holding 120 and missing it on a mid-range panel.
            // Fewer captures than before (8, or 12 in high-performance mode). A page change is the
            // worst case for this surface: the whole backdrop changes at once while the bar is
            // animating its own shape, so every recorded frame is a full-screen capture plus a
            // blur on top of a transition. A sample two frames older is invisible behind a blurred
            // capsule; the capture it saves is not.
            val every = if (PerformanceSettings.highPerformanceMode) 12 else 8
            val drawn = recordCounters[layer] ?: 0
            recordCounters[layer] = drawn + 1
            if (drawn % every == 0) {
                layer.record { drawBackdrop() }
            }
        },
        shape = { shape },
        onDrawSurface = {
            // The floor is what keeps a bright sleeve from showing through as a blob: at 0.12 a
            // white cover behind the bar left the icons sitting on raw artwork. The ceiling stays
            // where it was, so on a dark page the bar is still the same dark glass.
            val darken = lerp(0.42f, 0.62f, ((luminanceAnimation - 0.3f) / 0.5f).coerceIn(0f, 1f))
            drawRect(Color.Black.copy(alpha = darken))
        },
    )
