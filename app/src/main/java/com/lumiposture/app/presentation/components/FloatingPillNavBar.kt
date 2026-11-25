package com.lumiposture.app.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumiposture.app.presentation.theme.LumiTheme
import com.lumiposture.app.presentation.theme.PillShape
import dev.chrisbanes.haze.HazeState

enum class BottomNavTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME("home", "Beranda", Icons.Rounded.Home),
    HISTORY("history", "Riwayat", Icons.Rounded.History),
    SETTINGS("settings", "Setelan", Icons.Rounded.Tune),
}

@Composable
fun FloatingPillNavBar(
    currentRoute: String?,
    onSelectTab: (BottomNavTab) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val colors = LumiTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            hazeState = hazeState,
            shape = PillShape,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BottomNavTab.entries.forEach { tab ->
                    val selected = currentRoute == tab.route
                    val contentColor by animateColorAsState(
                        targetValue = if (selected) colors.primary else colors.textSecondary,
                        label = "navTabColor",
                    )
                    val pillBg by animateColorAsState(
                        targetValue = if (selected) colors.primary.copy(alpha = 0.12f) else Color.Transparent,
                        label = "navTabBg",
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(PillShape)
                            .background(pillBg, PillShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onSelectTab(tab) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = contentColor,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            ),
                            color = contentColor,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
