package xyz.stuffbyren.pomelo.desktop.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.Base64

data class DeviceAuthPrompt(
    val userCode: String,
    val verificationUri: String,
    val expiresIn: Int,
)

sealed class AuthStatus {
    object Unauthenticated : AuthStatus()
    data class DevicePending(val prompt: DeviceAuthPrompt) : AuthStatus()
    data class Authenticated(val userId: String, val countryCode: String) : AuthStatus()
    data class Error(val message: String) : AuthStatus()
}

class TidalDesktopAuth(
    private val http: OkHttpClient,
    private val scope: CoroutineScope,
    private val clientId: String = "ZL6uNG7Udwi7M8C0",
    private val clientSecret: String = "yEBhlvqwZF5ZBD6LExgOBcn3bvZfLlrg1VuRqX4qLu8=",
) {
    private val configFile: File by lazy {
        val dir = File(System.getProperty("user.home"), ".config/pomelo")
        if (!dir.exists()) dir.mkdirs()
        File(dir, "session.json")
    }

    private val _status = MutableStateFlow<AuthStatus>(AuthStatus.Unauthenticated)
    val status: StateFlow<AuthStatus> = _status.asStateFlow()

    @Volatile
    var accessToken: String? = null
        private set

    @Volatile
    var refreshToken: String? = null
        private set

    @Volatile
    var countryCode: String = "US"
        private set

    @Volatile
    var userId: String = ""
        private set

    private var pollJob: Job? = null

    init {
        loadSession()
    }

    private fun loadSession() {
        if (!configFile.exists()) {
            scope.launch { acquireClientCredentials() }
            return
        }
        runCatching {
            val json = JSONObject(configFile.readText())
            accessToken = json.optString("access_token").takeIf { it.isNotBlank() }
            refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() }
            countryCode = json.optString("country_code", "US")
            userId = json.optString("user_id", "")

            if (!accessToken.isNullOrBlank()) {
                _status.value = AuthStatus.Authenticated(userId, countryCode)
            } else if (!refreshToken.isNullOrBlank()) {
                scope.launch { refresh() }
            } else {
                scope.launch { acquireClientCredentials() }
            }
        }.onFailure {
            scope.launch { acquireClientCredentials() }
        }
    }

    private fun saveSession() {
        runCatching {
            val json = JSONObject().apply {
                put("access_token", accessToken.orEmpty())
                put("refresh_token", refreshToken.orEmpty())
                put("country_code", countryCode)
                put("user_id", userId)
            }
            configFile.writeText(json.toString(2))
        }
    }

    suspend fun acquireClientCredentials(): Boolean = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("grant_type", "client_credentials")
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .build()

        val req = Request.Builder()
            .url("https://auth.tidal.com/v1/oauth2/token")
            .post(form)
            .build()

        runCatching {
            http.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val payload = JSONObject(res.body.string())
                    accessToken = payload.getString("access_token")
                    saveSession()
                    true
                } else false
            }
        }.getOrDefault(false)
    }

    fun startDeviceLogin() {
        pollJob?.cancel()
        pollJob = scope.launch(Dispatchers.IO) {
            val form = FormBody.Builder()
                .add("client_id", clientId)
                .add("scope", "r_usr w_usr r_sub")
                .build()

            val req = Request.Builder()
                .url("https://auth.tidal.com/v1/oauth2/device/authorization")
                .post(form)
                .build()

            val prompt = runCatching {
                http.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) {
                        _status.value = AuthStatus.Error("Failed to request device authorization: ${res.code}")
                        return@launch
                    }
                    val json = JSONObject(res.body.string())
                    val deviceCode = json.getString("device_code")
                    val userCode = json.getString("user_code")
                    val uri = json.optString("verification_uri_complete", "https://tidal.com/device")
                    val expiresIn = json.optInt("expires_in", 300)
                    val interval = json.optInt("interval", 5)
                    DeviceAuthPrompt(userCode, uri, expiresIn) to (deviceCode to interval)
                }
            }.getOrElse {
                _status.value = AuthStatus.Error("Network error: ${it.message}")
                return@launch
            }

            val (authPrompt, deviceMeta) = prompt
            val (deviceCode, intervalSec) = deviceMeta
            _status.value = AuthStatus.DevicePending(authPrompt)

            pollDeviceToken(deviceCode, intervalSec)
        }
    }

    private suspend fun pollDeviceToken(deviceCode: String, intervalSec: Int) = withContext(Dispatchers.IO) {
        val pollInterval = (intervalSec.coerceAtLeast(3)) * 1000L
        while (isActive) {
            delay(pollInterval)
            val form = FormBody.Builder()
                .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("device_code", deviceCode)
                .add("scope", "r_usr w_usr r_sub")
                .build()

            val req = Request.Builder()
                .url("https://auth.tidal.com/v1/oauth2/token")
                .post(form)
                .build()

            val finished = runCatching {
                http.newCall(req).execute().use { res ->
                    val text = res.body.string()
                    val json = JSONObject(text)
                    if (res.isSuccessful) {
                        accessToken = json.getString("access_token")
                        refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() }
                        parseTokenPayload(accessToken!!)
                        saveSession()
                        _status.value = AuthStatus.Authenticated(userId, countryCode)
                        true
                    } else {
                        val subStatus = json.optString("sub_status")
                        if (subStatus == "1002" || res.code == 400) {
                            // Authorization pending, keep polling
                            false
                        } else {
                            _status.value = AuthStatus.Error("Authorization failed: ${json.optString("user_message", res.message)}")
                            true
                        }
                    }
                }
            }.getOrElse { false }

            if (finished) break
        }
    }

    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        val token = refreshToken ?: return@withContext false
        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("refresh_token", token)
            .build()

        val req = Request.Builder()
            .url("https://auth.tidal.com/v1/oauth2/token")
            .post(form)
            .build()

        runCatching {
            http.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val json = JSONObject(res.body.string())
                    accessToken = json.getString("access_token")
                    json.optString("refresh_token").takeIf { it.isNotBlank() }?.let { refreshToken = it }
                    parseTokenPayload(accessToken!!)
                    saveSession()
                    _status.value = AuthStatus.Authenticated(userId, countryCode)
                    true
                } else {
                    acquireClientCredentials()
                    false
                }
            }
        }.getOrDefault(false)
    }

    private fun parseTokenPayload(token: String) {
        runCatching {
            val parts = token.split(".")
            if (parts.size >= 2) {
                val decoded = String(Base64.getUrlDecoder().decode(parts[1]))
                val json = JSONObject(decoded)
                userId = json.optString("uid", userId)
                countryCode = json.optString("cc", countryCode)
            }
        }
    }

    fun logout() {
        pollJob?.cancel()
        accessToken = null
        refreshToken = null
        userId = ""
        configFile.delete()
        _status.value = AuthStatus.Unauthenticated
        scope.launch { acquireClientCredentials() }
    }
}
