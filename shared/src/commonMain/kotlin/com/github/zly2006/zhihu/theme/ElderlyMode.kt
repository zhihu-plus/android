/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.zly2006.zhihu.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.github.zly2006.zhihu.platform.rememberSettingsStore

const val ELDERLY_MODE_PREFERENCE_KEY = "elderlyMode"

/**
 * 老年模式开启后的界面文字倍率。
 *
 * 只放大 `sp`，不改 `dp` 密度，避免整页布局被二次缩放后裁切或重叠。
 * 正文仍叠加用户自己的 [com.github.zly2006.zhihu.ui.subscreens.PREF_FONT_SIZE]。
 */
const val ELDERLY_MODE_FONT_SCALE = 1.25f

internal fun settingValueEnablesElderlyMode(raw: String?): Boolean {
    val value = raw?.trim()?.lowercase() ?: return false
    return value == "1" || value == "true" || value == "on" || value == "yes"
}

@Composable
expect fun rememberSystemElderlyModeEnabled(): Boolean

@Composable
fun rememberElderlyModeEnabled(): Boolean {
    val settings = rememberSettingsStore()
    val systemEnabled = rememberSystemElderlyModeEnabled()
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(settings) {
        val unregister = settings.observeKeyChanges { key ->
            if (key == ELDERLY_MODE_PREFERENCE_KEY) {
                revision++
            }
        }
        onDispose { unregister() }
    }
    return remember(revision, systemEnabled, settings) {
        settings.getBoolean(ELDERLY_MODE_PREFERENCE_KEY, systemEnabled)
    }
}
