/*
 * Copyright (C) 2026 Pomelo Project
 *
 * This file is part of Pomelo.
 *
 * Pomelo is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 */

package dev.sfg.orchard.mobile.playback

import android.os.Handler
import androidx.media3.exoplayer.ExoPlayer

/** Lightweight standard crossfade engine placeholder for Pomelo. */
internal class CrossfadeEngine(
    private val handler: Handler,
    private val config: () -> Config,
    private val onMarker: (Any?) -> Unit = {},
    private val onHandoff: (outgoing: ExoPlayer, incoming: ExoPlayer) -> Unit = { _, _ -> },
    private val canCrossfadeTo: (String) -> Boolean = { true },
) {
    data class Config(
        val enabled: Boolean = false,
        val fadeSeconds: Double = 0.0,
    )

    fun start(active: ExoPlayer, standby: ExoPlayer) {}
    fun abort() {}
    fun trimMemory() {}
    fun release() {}
}
