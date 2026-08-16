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

/**
 * 各厂商系统老年/简易/关怀模式使用的 Settings 键。
 *
 * AOSP 没有统一老年模式 API，只能读取厂商写入的键；键不存在时不得当成已开启。
 */
internal val SYSTEM_ELDERLY_MODE_SETTING_KEYS = listOf(
    "elderly_mode",
    "elder_mode",
    "elderly_care",
    "hw_elderly_mode",
    "senior_mode",
    "care_mode",
    "easy_mode",
    "easy_mode_switch",
    "easy_mode_state",
    "sem_easy_mode",
    "simple_mode",
    "oldman_mode",
    "oplus_easy_mode",
    "color_easy_mode",
    "vivo_easy_mode",
)

fun settingValueEnablesElderlyMode(raw: String?): Boolean {
    val value = raw?.trim()?.lowercase() ?: return false
    return value == "1" || value == "true" || value == "on" || value == "yes"
}

fun resolveElderlyModeEnabled(
    stored: Boolean?,
    systemEnabled: Boolean,
): Boolean = stored ?: systemEnabled

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
    return remember(revision, systemEnabled) {
        settings.getBoolean(ELDERLY_MODE_PREFERENCE_KEY, systemEnabled)
    }
}
