package com.lumiposture.app.presentation.components

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lumiposture.app.domain.model.MetricStatus
import com.lumiposture.app.presentation.theme.GlassCardShape
import com.lumiposture.app.presentation.theme.HudGlassBorder
import com.lumiposture.app.presentation.theme.HudGlassTint
import com.lumiposture.app.presentation.theme.LumiTheme
import com.lumiposture.app.presentation.theme.PillShape
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    shape: Shape = GlassCardShape,
    isDarkOverlay: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LumiTheme.colors
    val tintColor = when {
        isDarkOverlay -> HudGlassTint
        else -> colors.glassTint
    }
    val borderColor = when {
        isDarkOverlay -> HudGlassBorder
        else -> colors.glassBorder
    }
    val fallbackBackground = when {
        isDarkOverlay -> Color(0xD912151C)
        colors.isDark -> Color(0xEB181C26)
        else -> Color(0xF0FFFFFF)
    }

    val blurStyle = remember(tintColor) {
        HazeBlurStyle {
            blurRadius(20.dp)
            colorEffects(listOf(HazeColorEffect.tint(tintColor)))
        }
    }

    val useRealBlur = (hazeState != null) && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
    val baseModifier = if (!colors.isDark && !isDarkOverlay) {
        modifier.shadow(
            elevation = 6.dp,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = 0.06f),
            spotColor = Color.Black.copy(alpha = 0.06f),
        )
    } else {
        modifier
    }

    val surfaceModifier = if (useRealBlur) {
        baseModifier
            .clip(shape)
            .hazeBlur(
                input = HazeInput.Sources(hazeState),
                style = blurStyle,
            )
            .border(0.75.dp, borderColor, shape)
    } else {
        baseModifier
            .clip(shape)
            .background(fallbackBackground, shape)
            .border(0.75.dp, borderColor, shape)
    }

    Box(
        modifier = surfaceModifier.padding(contentPadding),
        content = content,
    )
}

@Composable
fun LumiCard(
    modifier: Modifier = Modifier,
    shape: Shape = GlassCardShape,
    innerPadding: Dp = 20.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LumiTheme.colors
    val cardMod = modifier
        .then(
            if (!colors.isDark) {
                Modifier.shadow(
                    elevation = 4.dp,
                    shape = shape,
                    ambientColor = Color.Black.copy(alpha = 0.04f),
                    spotColor = Color.Black.copy(alpha = 0.04f),
                )
            } else {
                Modifier
            },
        )
        .clip(shape)
        .background(colors.cardSurface, shape)
        .border(0.75.dp, colors.subtleOutline, shape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(innerPadding)

    Column(modifier = cardMod, content = content)
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    containerColor: Color = LumiTheme.colors.primary,
    contentColor: Color = Color.White,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = PillShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.35f),
            disabledContentColor = contentColor.copy(alpha = 0.7f),
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
        modifier = modifier.height(52.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SecondaryPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = LumiTheme.colors
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = PillShape,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = colors.textTertiary.copy(alpha = 0.35f),
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = colors.textPrimary,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        modifier = modifier.height(48.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun <T> PillSegmentedControl(
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    labelProvider: (T) -> String,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    isDarkOverlay: Boolean = false,
) {
    val selectedIndex = options.indexOf(selectedOption).coerceAtLeast(0)
    val colors = LumiTheme.colors

    GlassSurface(
        modifier = modifier.height(46.dp),
        hazeState = hazeState,
        shape = PillShape,
        isDarkOverlay = isDarkOverlay,
        contentPadding = PaddingValues(4.dp),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            val segmentWidth = maxWidth / options.size.coerceAtLeast(1)
            val indicatorOffset by animateDpAsState(
                targetValue = segmentWidth * selectedIndex,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "segmentIndicator",
            )
            val pillColor = if (isDarkOverlay) {
                Color.White.copy(alpha = 0.20f)
            } else if (colors.isDark) {
                Color.White.copy(alpha = 0.14f)
            } else {
                colors.cardSurface
            }

            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .clip(PillShape)
                    .background(pillColor, PillShape),
            )

            Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                options.forEach { option ->
                    val isSelected = option == selectedOption
                    val textColor by animateColorAsState(
                        targetValue = when {
                            isDarkOverlay && isSelected -> Color.White
                            isDarkOverlay -> Color.White.copy(alpha = 0.72f)
                            isSelected -> colors.textPrimary
                            else -> colors.textSecondary
                        },
                        label = "segmentTextColor",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(PillShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onOptionSelected(option) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = labelProvider(option),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            ),
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusPillTag(
    status: MetricStatus,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = LumiTheme.colors
    val accent = when (status) {
        MetricStatus.OPTIMAL -> colors.optimal
        MetricStatus.WARNING -> colors.warning
        MetricStatus.CRITICAL -> colors.critical
    }
    Row(
        modifier = modifier
            .clip(PillShape)
            .background(accent.copy(alpha = 0.12f), PillShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(PillShape)
                .background(accent),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = accent,
            maxLines = 1,
        )
    }
}
