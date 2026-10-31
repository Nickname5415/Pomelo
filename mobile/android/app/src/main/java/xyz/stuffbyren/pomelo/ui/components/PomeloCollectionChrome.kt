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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xyz.stuffbyren.pomelo.ui.theme.PomeloShapes
import xyz.stuffbyren.pomelo.ui.theme.PomeloSpacing

@Composable
fun PomeloCircleChromeButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = PomeloSpacing.chromeCircleSize,
    iconTintPrimary: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = scheme.surfaceContainer,
        modifier = modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = if (iconTintPrimary) scheme.primary else scheme.onSurface,
                modifier = Modifier.size(PomeloSpacing.chromeIconSize),
            )
        }
    }
}

@Composable
fun PomeloChromeCapsule(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = scheme.surfaceContainer,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.height(PomeloSpacing.chromeCircleSize).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}

@Composable
fun PomeloCapsuleIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
) {
    val scheme = MaterialTheme.colorScheme
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = scheme.primary,
            modifier = Modifier.size(PomeloSpacing.chromeCapsuleIconSize),
        )
    }
}

@Composable
fun PomeloDetailTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = PomeloSpacing.screenHorizontal, vertical = PomeloSpacing.topBarVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PomeloCircleChromeButton(
            onClick = onBack,
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "Back",
        )
        Spacer(Modifier.weight(1f))
        PomeloChromeCapsule(content = actions)
    }
}

@Composable
fun PomeloSecondaryActionButton(
    onClick: () -> Unit,
    enabled: Boolean,
    width: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = PomeloShapes.lg,
        color = scheme.surfaceContainer,
        modifier = modifier.width(width).height(PomeloSpacing.actionButtonHeight),
    ) {
        Box(contentAlignment = Alignment.Center, content = { content() })
    }
}

@Composable
fun PomeloPrimaryActionButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = PomeloShapes.lg,
        color = scheme.primary,
        modifier = modifier.height(PomeloSpacing.actionButtonHeight),
    ) {
        Box(contentAlignment = Alignment.Center, content = { content() })
    }
}

@Composable
fun PomeloCollectionActionRow(
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit,
    primary: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PomeloSpacing.screenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(PomeloSpacing.actionButtonSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Box(Modifier.weight(1f)) { primary() }
        trailing()
    }
}
