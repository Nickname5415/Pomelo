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

package xyz.stuffbyren.pomelo.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import xyz.stuffbyren.pomelo.auth.TidalAuthRepository
import xyz.stuffbyren.pomelo.auth.TidalWebSession

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TidalLoginScreen(
    auth: TidalAuthRepository,
    tidalWebSession: TidalWebSession,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var stage by remember { mutableStateOf(if (auth.isLoggedIn) "web" else "dev") }
    val webAuthRequest = remember { tidalWebSession.createWebAuthRequest() }
    var pendingWebVerifier by remember { mutableStateOf(webAuthRequest.verifier) }

    val initialUrl = remember {
        if (auth.isLoggedIn) {
            webAuthRequest.authUrl
        } else {
            auth.initializeLoginUrl().toString()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (stage == "web") "Connecting TIDAL Player..." else "Sign in to TIDAL") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = settings.userAgentString.replace("; wv", "")
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean {
                                val uri = request?.url ?: return false
                                val urlStr = uri.toString()

                                if (urlStr.startsWith(auth.redirectUri)) {
                                    scope.launch {
                                        val success = auth.handleCallback(uri)
                                        if (success) {
                                            val req = tidalWebSession.createWebAuthRequest()
                                            pendingWebVerifier = req.verifier
                                            stage = "web"
                                            view?.loadUrl(req.authUrl)
                                        } else {
                                            onDismiss()
                                        }
                                    }
                                    return true
                                }

                                if (urlStr.startsWith(TidalWebSession.REDIRECT_URI)) {
                                    val code = uri.getQueryParameter("code")
                                    if (!code.isNullOrBlank()) {
                                        scope.launch {
                                            tidalWebSession.exchangeCodeForToken(code, pendingWebVerifier)
                                            onDismiss()
                                        }
                                    } else {
                                        onDismiss()
                                    }
                                    return true
                                }

                                return false
                            }
                        }
                        loadUrl(initialUrl)
                    }
                },
            )

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
