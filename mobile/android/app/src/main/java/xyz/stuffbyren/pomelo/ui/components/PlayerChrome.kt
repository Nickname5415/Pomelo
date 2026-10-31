/*
 * Copyright (C) 2026 SFG545
 *
 * This file is part of Orchard.
 *
 * Orchard is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * Orchard is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Orchard. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.stuffbyren.pomelo.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import xyz.stuffbyren.pomelo.ui.motion.popOnChange
import xyz.stuffbyren.pomelo.ui.navigation.Routes
import xyz.stuffbyren.pomelo.ui.theme.PomeloSpacing

data class BottomDestination(val route: String, val label: String, val icon: ImageVector)

private val destinations = listOf(
    BottomDestination(Routes.HOME, "Home", Icons.Rounded.Home),
    BottomDestination(Routes.SEARCH, "Search", Icons.Rounded.Search),
    BottomDestination(Routes.LIBRARY, "Library", Icons.Rounded.LibraryMusic),
)

private val settingsDestination = BottomDestination(Routes.SETTINGS, "Settings", Icons.Rounded.Person)

/**
 * Height the mini player and nav bar occupy together. Screens reserve this much bottom content
 * padding so their last row can still be scrolled clear of the floating chrome.
 */
val OrchardChromeHeight = 132.dp

private val BottomBarShape = RoundedCornerShape(PomeloSpacing.bottomBarCorner)
private val BottomBarIndicatorShape = RoundedCornerShape(PomeloSpacing.bottomBarIndicatorCorner)

/** Bottom navigation bar: flat floating pane on surfaceContainer. */
@Composable
fun OrchardBottomBar(currentRoute: String?, onSelect: (String) -> Unit, showSettings: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val settingsReveal by animateFloatAsState(
        targetValue = if (showSettings) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "BottomBarSettingsReveal",
    )
    val settingsVisible = showSettings || settingsReveal > 0.001f
    val allDestinations = if (settingsVisible) destinations + settingsDestination else destinations
    val selectedIndex = remember(currentRoute, allDestinations) {
        allDestinations.indexOfFirst { it.route == currentRoute }.takeIf { it != -1 }
            ?: if (currentRoute == Routes.SETTINGS_HOME_LAYOUT && settingsVisible) allDestinations.lastIndex else null
    }
    var lastKnownIndex by remember { mutableIntStateOf(selectedIndex ?: 0) }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex != null) {
            lastKnownIndex = selectedIndex
        }
    }
    val activeIndex = selectedIndex ?: lastKnownIndex

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = PomeloSpacing.bottomBarOuterPaddingHorizontal,
                vertical = PomeloSpacing.bottomBarOuterPaddingVertical,
            )
            .clip(BottomBarShape)
            .background(scheme.surfaceContainer),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(PomeloSpacing.bottomBarHeight),
        ) {
            val slotWidth = maxWidth / (destinations.size + settingsReveal)
            val indicatorWidth = if (slotWidth - PomeloSpacing.actionButtonSpacing < 72.dp) {
                slotWidth - PomeloSpacing.actionButtonSpacing
            } else {
                72.dp
            }
            val targetOffset = (slotWidth * (activeIndex.coerceAtMost(allDestinations.lastIndex) + 0.5f)) - (indicatorWidth / 2)

            val animatedOffset by animateDpAsState(
                targetValue = targetOffset,
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 380f),
                label = "BottomBarIndicatorX",
            )
            val indicatorAlpha by animateFloatAsState(
                targetValue = if (selectedIndex != null) 1f else 0f,
                animationSpec = tween(180),
                label = "BottomBarIndicatorAlpha",
            )

            if (indicatorAlpha > 0.001f) {
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                animatedOffset.roundToPx(),
                                ((PomeloSpacing.bottomBarHeight - PomeloSpacing.bottomBarIndicatorHeight) / 2).roundToPx(),
                            )
                        }
                        .size(width = indicatorWidth, height = PomeloSpacing.bottomBarIndicatorHeight)
                        .alpha(indicatorAlpha)
                        .background(color = scheme.secondaryContainer, shape = BottomBarIndicatorShape),
                )
            }

            Row(Modifier.fillMaxSize().selectableGroup()) {
                allDestinations.forEach { destination ->
                    val isSettings = destination === settingsDestination
                    val isSelected = currentRoute == destination.route
                    val weight = if (isSettings) settingsReveal.coerceAtLeast(0.001f) else 1f
                    Box(
                        Modifier
                            .weight(weight)
                            .fillMaxHeight()
                            .then(if (isSettings) Modifier.alpha(settingsReveal).clipToBounds() else Modifier),
                    ) {
                        BottomBarItem(destination, isSelected) { onSelect(destination.route) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomBarItem(destination: BottomDestination, isSelected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1.0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f),
        label = "NavIconScale",
    )
    val tint = if (isSelected) scheme.primary else scheme.onSurfaceVariant
    Column(
        Modifier
            .fillMaxSize()
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            destination.icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(PomeloSpacing.chromeCapsuleIconSize)
                .scale(iconScale)
                .popOnChange(isSelected, peak = 1.35f, onlyOn = true),
        )
        Spacer(Modifier.height(PomeloSpacing.explicitBadgeGap))
        Text(
            destination.label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = tint,
            maxLines = 1,
        )
    }
}
