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

import android.content.ContentResolver
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * 各厂商系统老年/简易/关怀模式使用的 Settings 键。
 *
 * AOSP 没有统一老年模式 API，只能读取厂商写入的键；键不存在或读失败时不得当成已开启。
 */
private val SYSTEM_ELDERLY_MODE_SETTING_KEYS = listOf(
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

fun readSystemElderlyModeEnabled(resolver: ContentResolver): Boolean =
    SYSTEM_ELDERLY_MODE_SETTING_KEYS.any { key ->
        // 必须用 getString。旧 MIUI isSimpleMode 对缺失的 simple_mode 使用 getInt(..., 1)，
        // 键不存在时会当成已开启；getString 在键缺失时返回 null，才能保持“未写入即未开启”。
        settingValueEnablesElderlyMode(safeSettingString { Settings.System.getString(resolver, key) }) ||
            settingValueEnablesElderlyMode(safeSettingString { Settings.Secure.getString(resolver, key) }) ||
            settingValueEnablesElderlyMode(safeSettingString { Settings.Global.getString(resolver, key) })
    }

private fun safeSettingString(read: () -> String?): String? = runCatching { read() }.getOrNull()

@Composable
actual fun rememberSystemElderlyModeEnabled(): Boolean {
    val context = LocalContext.current.applicationContext
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var enabled by remember {
        mutableStateOf(readSystemElderlyModeEnabled(context.contentResolver))
    }
    DisposableEffect(lifecycleOwner, configuration) {
        fun refresh() {
            enabled = readSystemElderlyModeEnabled(context.contentResolver)
        }
        refresh()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return enabled
}
