package xyz.stuffbyren.pomelo.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.stuffbyren.pomelo.desktop.auth.AuthStatus
import xyz.stuffbyren.pomelo.desktop.auth.TidalDesktopAuth
import xyz.stuffbyren.pomelo.desktop.ui.PomeloColors
import java.awt.Desktop
import java.net.URI

@Composable
fun DesktopSettingsScreen(
    auth: TidalDesktopAuth,
    modifier: Modifier = Modifier,
) {
    val status by auth.status.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PomeloColors.Background)
            .padding(32.dp)
    ) {
        Text(
            "Settings & Account",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = PomeloColors.TextPrimary,
        )

        Spacer(Modifier.height(24.dp))

        // TIDAL Account Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = PomeloColors.Surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            "TIDAL Account",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = PomeloColors.TextPrimary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Sign in to stream full Lossless FLAC tracks and sync playlists.",
                            fontSize = 13.sp,
                            color = PomeloColors.TextSecondary,
                        )
                    }

                    when (val s = status) {
                        is AuthStatus.Authenticated -> {
                            Button(
                                onClick = { auth.logout() },
                                colors = ButtonDefaults.buttonColors(containerColor = PomeloColors.SurfaceVariant),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text("Sign Out", color = PomeloColors.TextPrimary)
                            }
                        }
                        is AuthStatus.DevicePending -> {}
                        else -> {
                            Button(
                                onClick = { auth.startDeviceLogin() },
                                colors = ButtonDefaults.buttonColors(containerColor = PomeloColors.Primary),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text("Sign In with TIDAL", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                when (val s = status) {
                    is AuthStatus.Authenticated -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = "Connected",
                                tint = PomeloColors.TidalCyan,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Connected • Region: ${s.countryCode} • HiFi Enabled",
                                color = PomeloColors.TidalCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    is AuthStatus.DevicePending -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(PomeloColors.SurfaceVariant)
                                .padding(20.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Log in on your phone or browser:",
                                    color = PomeloColors.TextSecondary,
                                    fontSize = 14.sp,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    s.prompt.userCode,
                                    color = PomeloColors.Primary,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 4.sp,
                                )
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        runCatching {
                                            Desktop.getDesktop().browse(URI(s.prompt.verificationUri))
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PomeloColors.Primary),
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Open verification link in browser")
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    s.prompt.verificationUri,
                                    color = PomeloColors.TextSecondary,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }

                    is AuthStatus.Error -> {
                        Text(
                            s.message,
                            color = Color(0xFFFF5252),
                            fontSize = 13.sp,
                        )
                    }

                    AuthStatus.Unauthenticated -> {
                        Text(
                            "Guest mode active (Client credentials). Full catalog preview enabled.",
                            color = PomeloColors.TextSecondary,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Audio & Linux Pipeline Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = PomeloColors.Surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    "Audio Engine (Linux)",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PomeloColors.TextPrimary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Backend: GStreamer 1.28 & PipeWire / PulseAudio native sink.",
                    fontSize = 13.sp,
                    color = PomeloColors.TextSecondary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Streaming Quality: FLAC Lossless (16/24-bit 44.1kHz / 96kHz).",
                    fontSize = 13.sp,
                    color = PomeloColors.TidalCyan,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
