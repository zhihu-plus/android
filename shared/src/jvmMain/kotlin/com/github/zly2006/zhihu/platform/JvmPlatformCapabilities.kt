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

package com.github.zly2006.zhihu.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.github.zly2006.zhihu.desktop.DesktopAccountStore
import com.github.zly2006.zhihu.desktop.DesktopPropertiesFile
import com.github.zly2006.zhihu.desktop.copyDesktopPlainText
import com.github.zly2006.zhihu.desktop.desktopZhihuDataDir
import com.github.zly2006.zhihu.desktop.openDesktopExternalUrl
import com.github.zly2006.zhihu.desktop.saveImageToDownloads
import kotlinx.coroutines.launch
import kotlinx.io.files.Path
import java.util.concurrent.CopyOnWriteArrayList

@Composable
actual fun rememberSettingsStore(): SettingsStore = remember { desktopSettingsStore() }

actual fun Modifier.exportTestTagsForUiAutomation(): Modifier = this

@Composable
actual fun rememberAppPrivateDirectory(): Path = remember { Path(desktopZhihuDataDir().absolutePath) }

private val desktopSettingsLock = Any()
private val desktopSettingsKeyListeners = CopyOnWriteArrayList<(String) -> Unit>()
private var cachedDesktopSettingsStore: SettingsStore? = null

/**
 * 桌面设置必须单例。每次新建一份内存 Properties 时，设置页写入后主题层读到的仍是旧副本；
 * 老年模式依赖 observeKeyChanges 立刻改 fontScale，不能各读各的。
 */
fun desktopSettingsStore(): SettingsStore = synchronized(desktopSettingsLock) {
    cachedDesktopSettingsStore ?: createDesktopSettingsStore().also { cachedDesktopSettingsStore = it }
}

private fun createDesktopSettingsStore(): SettingsStore {
    val propertiesFile = DesktopPropertiesFile("settings.properties", "Zhihu++ desktop settings")
    val properties = propertiesFile.properties

    fun persist(
        key: String,
        mutate: () -> Unit,
    ) {
        synchronized(desktopSettingsLock) {
            mutate()
            propertiesFile.save()
        }
        desktopSettingsKeyListeners.forEach { listener -> listener(key) }
    }

    return SettingsStore(
        getBoolean = { key, defaultValue ->
            synchronized(desktopSettingsLock) {
                properties.getProperty(key)?.toBooleanStrictOrNull() ?: defaultValue
            }
        },
        putBoolean = { key, value ->
            persist(key) { properties.setProperty(key, value.toString()) }
        },
        getString = { key, defaultValue ->
            synchronized(desktopSettingsLock) {
                properties.getProperty(key) ?: defaultValue
            }
        },
        putString = { key, value ->
            persist(key) { properties.setProperty(key, value) }
        },
        getStringOrNull = { key ->
            synchronized(desktopSettingsLock) {
                properties.getProperty(key)
            }
        },
        putStringSet = { key, value ->
            persist(key) { properties.setProperty(key, value.joinToString("\u001F")) }
        },
        getStringSet = { key, defaultValue ->
            synchronized(desktopSettingsLock) {
                properties
                    .getProperty(key)
                    ?.split("\u001F")
                    ?.filter { it.isNotEmpty() }
                    ?.toSet() ?: defaultValue
            }
        },
        getInt = { key, defaultValue ->
            synchronized(desktopSettingsLock) {
                properties.getProperty(key)?.toIntOrNull() ?: defaultValue
            }
        },
        putInt = { key, value ->
            persist(key) { properties.setProperty(key, value.toString()) }
        },
        getLong = { key, defaultValue ->
            synchronized(desktopSettingsLock) {
                properties.getProperty(key)?.toLongOrNull() ?: defaultValue
            }
        },
        putLong = { key, value ->
            persist(key) { properties.setProperty(key, value.toString()) }
        },
        getFloat = { key, defaultValue ->
            synchronized(desktopSettingsLock) {
                properties.getProperty(key)?.toFloatOrNull() ?: defaultValue
            }
        },
        putFloat = { key, value ->
            persist(key) { properties.setProperty(key, value.toString()) }
        },
        remove = { key ->
            persist(key) { properties.remove(key) }
        },
        observeKeyChanges = { onChanged ->
            desktopSettingsKeyListeners.add(onChanged)
            val unregister = {
                desktopSettingsKeyListeners.remove(onChanged)
                Unit
            }
            unregister
        },
    )
}

@Composable
actual fun rememberExternalUrlOpener(): (String) -> Unit = remember { { url -> openDesktopExternalUrl(url) } }

@Composable
actual fun rememberSystemUrlOpener(): (String) -> Unit = rememberExternalUrlOpener()

@Composable
actual fun rememberZhihuWebUrlOpener(): (String) -> Unit = rememberExternalUrlOpener()

@Composable
actual fun rememberImagePreviewOpener(): (String) -> Unit = rememberExternalUrlOpener()

@Composable
actual fun rememberImageGalleryOpener(): (List<String>, Int) -> Unit {
    val openExternalUrl = rememberExternalUrlOpener()
    return remember(openExternalUrl) {
        { urls, initialIndex ->
            if (urls.isNotEmpty()) {
                urls[initialIndex.coerceIn(0, urls.lastIndex)].let(openExternalUrl)
            }
        }
    }
}

@Composable
actual fun rememberImageSaver(): (String) -> Unit {
    val scope = rememberCoroutineScope()
    val userMessages = rememberUserMessageSink()
    val store = remember { DesktopAccountStore() }
    return remember(scope, userMessages, store) {
        { imageUrl ->
            scope.launch {
                runCatching {
                    store.saveImageToDownloads(imageUrl, "image")
                }.onSuccess { file ->
                    userMessages.showShortMessage("已保存图片: ${file.absolutePath}")
                }.onFailure { error ->
                    userMessages.showShortMessage("保存失败: ${error.message}")
                }
            }
        }
    }
}

@Composable
actual fun rememberImageSharer(): (String) -> Unit {
    val userMessages = rememberUserMessageSink()
    return remember(userMessages) {
        { imageUrl ->
            runCatching {
                copyDesktopPlainText(imageUrl)
                userMessages.showShortMessage("已复制图片链接")
            }.onFailure { error ->
                userMessages.showShortMessage("分享失败: ${error.message}")
            }
        }
    }
}

@Composable
actual fun rememberPlainTextClipboard(): (label: String, text: String) -> Unit =
    remember { { _, text -> runCatching { copyDesktopPlainText(text) } } }

@Composable
actual fun rememberUserMessageSink(): UserMessageSink = remember {
    UserMessageSink(
        showShortMessage = { message ->
            println(message)
            runCatching {
                ProcessBuilder("terminal-notifier", "-message", message, "-sound", "default")
                    .start()
            }
        },
    )
}

@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) = Unit // TODO: desktop back handler

@Composable
actual fun PlatformPredictiveBackHandler(
    enabled: Boolean,
    onProgress: (Float) -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
) = PlatformBackHandler(enabled = enabled, onBack = onBack)

@Composable
actual fun rememberIsLiteVariant(): Boolean = false

@Composable
actual fun rememberTouchExplorationEnabled(): Boolean = false
