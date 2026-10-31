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

package xyz.stuffbyren.pomelo.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared layout tokens for the Material 3 Expressive shell (16dp grid). */
object PomeloSpacing {
    val screenHorizontal: Dp = 16.dp
    val listBottomInset: Dp = 120.dp

    val trackRowHeight: Dp = 36.dp
    val trackNumberWidth: Dp = 28.dp
    val trackDividerInsetStart: Dp = screenHorizontal + trackNumberWidth

    val actionButtonHeight: Dp = 45.dp
    val actionButtonSpacing: Dp = 8.dp
    val actionSecondaryWidthShuffle: Dp = 86.dp
    val actionSecondaryWidthAdd: Dp = 82.dp

    val topBarVertical: Dp = 8.dp
    val chromeCircleSize: Dp = 44.dp
    val chromeIconSize: Dp = 22.dp
    val chromeCapsuleIconSize: Dp = 20.dp

    val explicitBadgeGap: Dp = 6.dp
    val durationLeadingGap: Dp = 8.dp
}
