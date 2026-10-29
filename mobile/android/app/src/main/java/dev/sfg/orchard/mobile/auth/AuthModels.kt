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

package dev.sfg.orchard.mobile.auth

/** Authenticated TIDAL session. */
data class TidalSession(
    val userId: String = "",
    val displayName: String = "TIDAL",
    val avatarUrl: String = "",
    val countryCode: String = "US",
)

fun interface TidalSessionProvider {
    fun session(): TidalSession?
}

sealed interface AuthState {
    data object Restoring : AuthState
    data object SignedOut : AuthState
    data object Authorizing : AuthState
    data class SignedIn(
        val displayName: String = "TIDAL",
        val avatarUrl: String = "",
        val userId: String = "",
    ) : AuthState
    data class Error(val message: String) : AuthState
}
