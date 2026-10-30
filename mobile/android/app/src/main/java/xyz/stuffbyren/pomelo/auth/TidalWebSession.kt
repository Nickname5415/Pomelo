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

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

private const val TAG = "TidalWebSession"
private const val ANDROID_CLIENT_ID = "6BDSRdpK9hqEBTgU"
private const val ANDROID_CLIENT_SECRET = "xeuPmY7nbpZ9IIbLAcQ93shka1VNheUAqN6IcszjTG8="
private const val REDIRECT_URI = "https://tidal.com/android/login/auth"
private const val PREFS_NAME = "pomelo_tidal_web_session"
private const val KEY_ACCESS_TOKEN = "web_access_token"
private const val KEY_REFRESH_TOKEN = "web_refresh_token"
private const val KEY_EXPIRES_AT = "web_expires_at"

data class WebAuthRequest(
    val authUrl: String,
    val verifier: String,
)

/**
 * Manages an authenticated TIDAL Web session using client 6BDSRdpK9hqEBTgU
 * for full-song audio stream resolution without developer playback restrictions.
 */
class TidalWebSession(
    private val context: Context,
    private val http: OkHttpClient,
) {
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private val mutex = Mutex()

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var expiresAtMs: Long = 0L

    init {
        cachedToken = prefs.getString(KEY_ACCESS_TOKEN, null)
        expiresAtMs = prefs.getLong(KEY_EXPIRES_AT, 0L)
    }

    fun hasToken(): Boolean {
        val now = System.currentTimeMillis()
        if (!cachedToken.isNullOrBlank() && now < expiresAtMs - 60_000L) return true
        return !prefs.getString(KEY_REFRESH_TOKEN, null).isNullOrBlank()
    }

    fun clear() {
        cachedToken = null
        expiresAtMs = 0L
        prefs.edit().clear().apply()
    }

    fun getUserId(): String? {
        val token = cachedToken ?: prefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        return runCatching {
            val parts = token.split(".")
            if (parts.size >= 2) {
                val payload = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
                JSONObject(payload).optString("uid").takeIf { it.isNotBlank() }
            } else null
        }.getOrNull()
    }

    fun getCountryCode(): String? {
        val token = cachedToken ?: prefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        return runCatching {
            val parts = token.split(".")
            if (parts.size >= 2) {
                val payload = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
                JSONObject(payload).optString("cc").takeIf { it.isNotBlank() }
            } else null
        }.getOrNull()
    }

    fun createWebAuthRequest(): WebAuthRequest {
        val verifier = generateCodeVerifier()
        val challenge = generateCodeChallenge(verifier)
        val authUrl = Uri.parse("https://login.tidal.com/authorize").buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("client_id", ANDROID_CLIENT_ID)
            .appendQueryParameter("lang", "EN")
            .appendQueryParameter("appMode", "android")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()
            .toString()
        return WebAuthRequest(authUrl, verifier)
    }

    suspend fun getAccessToken(): String? = mutex.withLock {
        val now = System.currentTimeMillis()
        if (!cachedToken.isNullOrBlank() && now < expiresAtMs - 60_000L) {
            return cachedToken
        }

        val storedRefresh = prefs.getString(KEY_REFRESH_TOKEN, null)
        if (!storedRefresh.isNullOrBlank()) {
            val refreshed = refreshAccessToken(storedRefresh)
            if (!refreshed.isNullOrBlank()) {
                return refreshed
            }
        }

        acquireAccessTokenViaWebAuth()
    }

    private suspend fun refreshAccessToken(refreshToken: String): String? = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", ANDROID_CLIENT_ID)
            .add("client_secret", ANDROID_CLIENT_SECRET)
            .add("refresh_token", refreshToken)
            .build()

        val request = Request.Builder()
            .url("https://auth.tidal.com/v1/oauth2/token")
            .post(body)
            .build()

        runCatching {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Refresh failed: ${response.code} ${response.message}")
                    return@use null
                }
                val payload = response.body.string()
                parseAndStoreToken(payload)
            }
        }.onFailure { Log.w(TAG, "Exception refreshing token", it) }.getOrNull()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun acquireAccessTokenViaWebAuth(): String? {
        val req = createWebAuthRequest()
        val authUrl = req.authUrl
        val verifier = req.verifier

        val codeDeferred = CompletableDeferred<String>()
        val code = try {
            withContext(Dispatchers.Main) {
                val webView = WebView(context.applicationContext)
                webView.settings.javaScriptEnabled = true
                webView.settings.domStorageEnabled = true
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                        Log.d(TAG, "onPageStarted: $url")
                        checkRedirect(view, url)
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        Log.d(TAG, "onPageFinished: $url")
                        checkRedirect(view, url)
                    }

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val url = request.url.toString()
                        Log.d(TAG, "shouldOverrideUrlLoading: $url")
                        return checkRedirect(view, url)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                        Log.d(TAG, "shouldOverrideUrlLoading legacy: $url")
                        return checkRedirect(view, url)
                    }

                    private fun checkRedirect(view: WebView, url: String): Boolean {
                        if (url.startsWith(REDIRECT_URI)) {
                            val uri = Uri.parse(url)
                            val authCode = uri.getQueryParameter("code")
                            if (!authCode.isNullOrBlank()) {
                                codeDeferred.complete(authCode)
                            } else {
                                codeDeferred.completeExceptionally(IllegalStateException("No code in redirect: $url"))
                            }
                            view.destroy()
                            return true
                        }
                        return false
                    }
                }
                webView.loadUrl(authUrl)
            }
            withTimeout(15_000L) { codeDeferred.await() }
        } catch (e: Exception) {
            Log.w(TAG, "Silent web authorization failed or timed out", e)
            return null
        }

        return exchangeCodeForToken(code, verifier)
    }

    suspend fun exchangeCodeForToken(code: String, verifier: String): String? = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", ANDROID_CLIENT_ID)
            .add("client_secret", ANDROID_CLIENT_SECRET)
            .add("code", code)
            .add("code_verifier", verifier)
            .add("redirect_uri", REDIRECT_URI)
            .add("scope", "r_usr w_usr w_sub")
            .build()

        val request = Request.Builder()
            .url("https://auth.tidal.com/v1/oauth2/token")
            .post(body)
            .build()

        runCatching {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body.string()
                    Log.w(TAG, "Code exchange failed: ${response.code} $err")
                    return@use null
                }
                val payload = response.body.string()
                parseAndStoreToken(payload)
            }
        }.onFailure { Log.w(TAG, "Exception exchanging auth code", it) }.getOrNull()
    }

    private fun parseAndStoreToken(jsonString: String): String? {
        val json = JSONObject(jsonString)
        val accessToken = json.optString("access_token", "")
        val refreshToken = json.optString("refresh_token", "")
        val expiresIn = json.optLong("expires_in", 604800L)

        if (accessToken.isNotBlank()) {
            val expiry = System.currentTimeMillis() + (expiresIn * 1000L)
            cachedToken = accessToken
            expiresAtMs = expiry
            prefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putLong(KEY_EXPIRES_AT, expiry)
                .apply()
            Log.i(TAG, "Successfully acquired and cached TIDAL Web access token")
            return accessToken
        }
        return null
    }

    companion object {
        const val REDIRECT_URI = "https://tidal.com/android/login/auth"

        private fun generateCodeVerifier(): String {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }

        private fun generateCodeChallenge(verifier: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
            return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }
    }
}
