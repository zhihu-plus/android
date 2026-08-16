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

fun readSystemElderlyModeEnabled(resolver: ContentResolver): Boolean =
    SYSTEM_ELDERLY_MODE_SETTING_KEYS.any { key ->
        settingValueEnablesElderlyMode(Settings.System.getString(resolver, key)) ||
            settingValueEnablesElderlyMode(Settings.Secure.getString(resolver, key)) ||
            settingValueEnablesElderlyMode(Settings.Global.getString(resolver, key))
    }

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
