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
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

private const val TAG = "TidalAuth"

/**
 * Manages official TIDAL SDK authentication and exposes session state to Pomelo.
 */
class TidalAuthRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) : TidalSessionProvider {

    val redirectUri: String = "xyz.stuffbyren.pomelo://oauth/callback"

    private val authConfig by lazy {
        AuthConfig(
            clientId = BuildConfig.TIDAL_CLIENT_ID.ifBlank { "pomelo-tidal-client-id" },
            clientSecret = BuildConfig.TIDAL_CLIENT_SECRET.ifBlank { null },
            credentialsKey = "pomelo_tidal_credentials",
            scopes = setOf(
                "collection.read",
                "collection.write",
                "entitlements.read",
                "playback",
                "playlists.read",
                "playlists.write",
                "recommendations.read",
                "search.read",
                "search.write",
                "user.read",
            ),
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
                    val token = creds?.token
                    val profile = if (!token.isNullOrBlank()) fetchUserProfile(token) else null
                    val displayName = profile?.first ?: if (userId.isNotBlank()) "TIDAL User ($userId)" else "TIDAL"
                    val countryCode = profile?.second ?: "US"

                    currentSession = TidalSession(
                        userId = userId,
                        displayName = displayName,
                        countryCode = countryCode,
                    )
                    _authState.value = AuthState.SignedIn(
                        userId = userId,
                        displayName = displayName,
                    )
                    Log.i(TAG, "Restored active TIDAL session for user: $userId (country: $countryCode, level=${creds?.level}, grantedScopes=${creds?.grantedScopes}, requestedScopes=${creds?.requestedScopes})")
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
                val token = creds?.token
                val profile = if (!token.isNullOrBlank()) fetchUserProfile(token) else null
                val displayName = profile?.first ?: if (userId.isNotBlank()) "TIDAL User ($userId)" else "TIDAL"
                val countryCode = profile?.second ?: "US"

                currentSession = TidalSession(
                    userId = userId,
                    displayName = displayName,
                    countryCode = countryCode,
                )
                _authState.value = AuthState.SignedIn(
                    userId = userId,
                    displayName = displayName,
                )
                Log.i(TAG, "TIDAL sign-in succeeded: $userId (country: $countryCode, grantedScopes=${creds?.grantedScopes})")
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

    private suspend fun fetchUserProfile(token: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://openapi.tidal.com/v2/users/me")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/vnd.api+json")
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            if (connection.responseCode in 200..299) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val data = json.optJSONObject("data")
                val attributes = data?.optJSONObject("attributes")
                val country = attributes?.optString("country", "US") ?: "US"
                val username = attributes?.optString("username", "") ?: ""
                val firstName = attributes?.optString("firstName", "") ?: ""
                val lastName = attributes?.optString("lastName", "") ?: ""
                val name = if (firstName.isNotBlank() || lastName.isNotBlank()) {
                    "$firstName $lastName".trim()
                } else if (username.isNotBlank()) {
                    username
                } else {
                    "TIDAL User"
                }
                Log.i(TAG, "Fetched TIDAL user profile: name=$name, country=$country")
                return@withContext Pair(name, country)
            } else {
                val err = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Log.w(TAG, "Failed to fetch user profile: ${connection.responseCode} $err")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Exception fetching user profile", e)
        }
        null
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

    @Volatile
    private var clientCredentialsToken: String? = null

    @Volatile
    private var clientCredentialsExpiresAt: Long = 0L

    /**
     * Resolves an active Bearer token for API calls.
     * Prefers authenticated user token from CredentialsProvider,
     * falling back to client credentials for catalog and search operations.
     */
    suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        try {
            val credResult = credentialsProvider.getCredentials()
            if (credResult.isSuccess) {
                val token = credResult.successData?.token
                if (!token.isNullOrBlank()) {
                    return@withContext token
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve credentials from TidalAuth, trying client credentials fallback", e)
        }

        val now = System.currentTimeMillis()
        if (clientCredentialsToken != null && now < clientCredentialsExpiresAt - 60_000L) {
            return@withContext clientCredentialsToken
        }

        fetchClientCredentialsToken()
    }

    private fun fetchClientCredentialsToken(): String? {
        val clientId = BuildConfig.TIDAL_CLIENT_ID
        val clientSecret = BuildConfig.TIDAL_CLIENT_SECRET
        if (clientId.isBlank() || clientSecret.isBlank()) {
            Log.w(TAG, "Cannot fetch client credentials: TIDAL_CLIENT_ID or TIDAL_CLIENT_SECRET is missing")
            return null
        }

        try {
            val url = URL("https://auth.tidal.com/v1/oauth2/token")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connectTimeout = 10_000
                readTimeout = 10_000
            }

            val body = "grant_type=client_credentials&client_id=" +
                URLEncoder.encode(clientId, "UTF-8") +
                "&client_secret=" +
                URLEncoder.encode(clientSecret, "UTF-8")

            connection.outputStream.use { os ->
                os.write(body.toByteArray(Charsets.UTF_8))
            }

            if (connection.responseCode in 200..299) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val token = json.optString("access_token", "")
                val expiresIn = json.optLong("expires_in", 3600L)
                if (token.isNotBlank()) {
                    clientCredentialsToken = token
                    clientCredentialsExpiresAt = System.currentTimeMillis() + (expiresIn * 1000L)
                    Log.i(TAG, "Acquired and cached client credentials access token")
                    return token
                }
            } else {
                val err = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Log.w(TAG, "Client credentials request failed: ${connection.responseCode} $err")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Exception fetching client credentials token", e)
        }
        return null
    }
}
