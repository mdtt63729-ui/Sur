package com.manikdev.spotify.nightly.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.manikdev.spotify.nightly.ui.icon.SpotifyIcons

/**
 * Audio / video capsule for the player: two icons in one pill, with a slider that moves between
 * them. Audio is the default and is where it opens on every song.
 *
 * The slider is driven by a spring rather than a tween so the move overshoots very slightly and
 * settles - the bouncy feel, without the wobble a low-damping spring would give at this size.
 */
@Composable
internal fun MediaModeCapsule(
    isVideo: Boolean,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    val itemWidth = 44.dp
    val sliderOffset by animateDpAsState(
        targetValue = if (isVideo) itemWidth else 0.dp,
        animationSpec =
            spring(
                dampingRatio = 0.62f,
                stiffness = Spring.StiffnessMediumLow,
            ),
        label = "mediaModeSlider",
    )
    val sliderColor by animateColorAsState(
        targetValue = accentColor.copy(alpha = 0.22f),
        label = "mediaModeSliderColor",
    )

    Row(
        modifier =
            modifier
                .height(36.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(containerColor)
                .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(itemWidth * 2)) {
            // The moving pill. It sits under the icons so the icons stay legible as it passes.
            Box(
                modifier =
                    Modifier
                        .offset(x = sliderOffset)
                        .size(width = itemWidth, height = 32.dp)
                        .clip(CircleShape)
                        .background(sliderColor),
            )
            Row(modifier = Modifier.height(32.dp), verticalAlignment = Alignment.CenterVertically) {
                CapsuleIcon(
                    selected = !isVideo,
                    onClick = { onSelect(false) },
                    contentColor = contentColor,
                    accentColor = accentColor,
                ) {
                    Icon(
                        imageVector = SpotifyIcons.Headphones,
                        contentDescription = "Audio",
                        modifier = Modifier.size(18.dp),
                    )
                }
                CapsuleIcon(
                    selected = isVideo,
                    onClick = { onSelect(true) },
                    contentColor = contentColor,
                    accentColor = accentColor,
                ) {
                    Icon(
                        imageVector = SpotifyIcons.Movie,
                        contentDescription = "Video",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CapsuleIcon(
    selected: Boolean,
    onClick: () -> Unit,
    contentColor: Color,
    accentColor: Color,
    content: @Composable () -> Unit,
) {
    val tint by animateColorAsState(
        targetValue = if (selected) accentColor else contentColor.copy(alpha = 0.75f),
        label = "mediaModeIconTint",
    )
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier =
            Modifier
                .width(44.dp)
                .height(32.dp)
                .clip(CircleShape)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides tint,
        ) { content() }
    }
}
