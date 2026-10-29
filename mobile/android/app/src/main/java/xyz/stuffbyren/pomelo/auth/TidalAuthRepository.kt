/*
 * Copyright (C) 2026 SFG545
 *
 * This file is part of Pomelo.
 *
 * Pomelo is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * Pomelo is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Pomelo. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.stuffbyren.pomelo.auth

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tidal.sdk.auth.CredentialsProvider
import com.tidal.sdk.auth.TidalAuth
import com.tidal.sdk.auth.model.AuthConfig
import com.tidal.sdk.auth.model.LoginConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.stuffbyren.pomelo.BuildConfig

private const val TAG = "TidalAuth"

/**
 * Manages official TIDAL SDK authentication and exposes session state to Pomelo.
 */
class TidalAuthRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) : TidalSessionProvider {

    val redirectUri: String = "${BuildConfig.APPLICATION_ID}://oauth-callback"

    private val authConfig by lazy {
        AuthConfig(
            clientId = BuildConfig.TIDAL_CLIENT_ID.ifBlank { "pomelo-tidal-client-id" },
            clientSecret = BuildConfig.TIDAL_CLIENT_SECRET.ifBlank { null },
            credentialsKey = "pomelo_tidal_credentials",
            scopes = setOf("r_usr", "w_usr"),
        )
    }

    private val tidalAuth: TidalAuth by lazy {
        TidalAuth.getInstance(authConfig, context.applicationContext)
    }

    val credentialsProvider: CredentialsProvider
        get() = tidalAuth.credentialsProvider

    private val _authState = MutableStateFlow<AuthState>(AuthState.Restoring)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    val state: StateFlow<AuthState> get() = authState

    @Volatile
    private var currentSession: TidalSession? = null

    override fun session(): TidalSession? = currentSession

    /**
     * Attempts to restore credentials from local storage on startup.
     */
    suspend fun restore() = withContext(Dispatchers.IO) {
        try {
            if (credentialsProvider.isUserLoggedIn()) {
                val credResult = credentialsProvider.getCredentials()
                if (credResult.isSuccess) {
                    val creds = credResult.successData
                    val userId = creds?.userId ?: ""
                    currentSession = TidalSession(
                        userId = userId,
                        displayName = if (userId.isNotBlank()) "TIDAL User ($userId)" else "TIDAL",
                    )
                    _authState.value = AuthState.SignedIn(
                        userId = userId,
                        displayName = currentSession?.displayName ?: "TIDAL",
                    )
                    Log.i(TAG, "Restored active TIDAL session for user: $userId")
                    return@withContext
                }
            }
            _authState.value = AuthState.SignedOut
            currentSession = null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to restore TIDAL session", e)
            _authState.value = AuthState.SignedOut
            currentSession = null
        }
    }

    /**
     * Generates the OAuth2 Authorization Code URL with PKCE.
     */
    fun initializeLoginUrl(): Uri {
        _authState.value = AuthState.Authorizing
        return tidalAuth.auth.initializeLogin(redirectUri, LoginConfig())
    }

    /**
     * Completes OAuth2 authorization code exchange.
     */
    suspend fun handleCallback(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val uriString = uri.toString()
        if (!uriString.startsWith(redirectUri)) {
            return@withContext false
        }
        try {
            val result = tidalAuth.auth.finalizeLogin(uriString)
            if (result.isSuccess) {
                val credResult = credentialsProvider.getCredentials()
                val creds = credResult.successData
                val userId = creds?.userId ?: ""
                currentSession = TidalSession(
                    userId = userId,
                    displayName = if (userId.isNotBlank()) "TIDAL User ($userId)" else "TIDAL",
                )
                _authState.value = AuthState.SignedIn(
                    userId = userId,
                    displayName = currentSession?.displayName ?: "TIDAL",
                )
                Log.i(TAG, "TIDAL sign-in succeeded: $userId")
                true
            } else {
                _authState.value = AuthState.Error("TIDAL login failed")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finalizing TIDAL login", e)
            _authState.value = AuthState.Error(e.message ?: "Authentication error")
            false
        }
    }

    /**
     * Terminates the active TIDAL session.
     */
    suspend fun logout() = withContext(Dispatchers.IO) {
        try {
            tidalAuth.auth.logout()
        } catch (e: Exception) {
            Log.w(TAG, "Error during logout", e)
        } finally {
            currentSession = null
            _authState.value = AuthState.SignedOut
        }
    }

    /**
     * Resolves an active Bearer token for API calls.
     */
    suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        try {
            val credResult = credentialsProvider.getCredentials()
            if (credResult.isSuccess) {
                credResult.successData?.token
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve access token", e)
            null
        }
    }
}
