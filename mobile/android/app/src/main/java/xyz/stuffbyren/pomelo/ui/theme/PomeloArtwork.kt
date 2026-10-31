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

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object PomeloArtwork {
    val heroCorner: Dp = PomeloShapes.radiusXl
    val heroShape = RoundedCornerShape(heroCorner)

    val artistShape: Shape = CircleShape

    /** Square tiles: 24dp on large hero art, scaled down for list thumbnails. */
    fun tileCorner(size: Dp): Dp = when {
        size >= 120.dp -> PomeloShapes.radiusXl
        size >= 64.dp -> PomeloShapes.radiusLg
        size >= 44.dp -> PomeloShapes.radiusMd
        else -> PomeloShapes.radiusSm
    }

    fun tileShape(size: Dp): Shape = RoundedCornerShape(tileCorner(size))

    fun tileRadiusInt(size: Dp): Int = tileCorner(size).value.toInt()
}
