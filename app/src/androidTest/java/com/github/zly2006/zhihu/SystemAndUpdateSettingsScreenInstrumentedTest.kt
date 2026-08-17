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

package com.github.zly2006.zhihu

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.zly2006.zhihu.data.ARCHIVE_SERVER_ENABLED_PREFERENCE_KEY
import com.github.zly2006.zhihu.data.ARCHIVE_SERVER_SAVE_STRATEGY_PREFERENCE_KEY
import com.github.zly2006.zhihu.data.ARCHIVE_SERVER_TOKEN_PREFERENCE_KEY
import com.github.zly2006.zhihu.data.ARCHIVE_SERVER_URL_PREFERENCE_KEY
import com.github.zly2006.zhihu.data.LOCAL_ARCHIVE_ENABLED_PREFERENCE_KEY
import com.github.zly2006.zhihu.data.LOCAL_ARCHIVE_FORWARD_DELETE_PREFERENCE_KEY
import com.github.zly2006.zhihu.data.LOCAL_ARCHIVE_FORWARD_ENABLED_PREFERENCE_KEY
import com.github.zly2006.zhihu.data.LOCAL_ARCHIVE_SAVE_STRATEGY_PREFERENCE_KEY
import com.github.zly2006.zhihu.test.MainActivityComposeRule
import com.github.zly2006.zhihu.test.performVerticalSwipeCycle
import com.github.zly2006.zhihu.test.resetAppPreferences
import com.github.zly2006.zhihu.test.setScreenContent
import com.github.zly2006.zhihu.ui.PREFERENCE_NAME
import com.github.zly2006.zhihu.ui.subscreens.SYSTEM_SETTINGS_ARCHIVE_SERVER_STRATEGY_TAG
import com.github.zly2006.zhihu.ui.subscreens.SYSTEM_SETTINGS_ARCHIVE_TOKEN_TAG
import com.github.zly2006.zhihu.ui.subscreens.SYSTEM_SETTINGS_ARCHIVE_URL_TAG
import com.github.zly2006.zhihu.ui.subscreens.SYSTEM_SETTINGS_LOCAL_ARCHIVE_FORWARD_DELETE_TAG
import com.github.zly2006.zhihu.ui.subscreens.SYSTEM_SETTINGS_LOCAL_ARCHIVE_FORWARD_NOW_TAG
import com.github.zly2006.zhihu.ui.subscreens.SYSTEM_SETTINGS_LOCAL_ARCHIVE_FORWARD_TAG
import com.github.zly2006.zhihu.ui.subscreens.SYSTEM_SETTINGS_LOCAL_ARCHIVE_STRATEGY_TAG
import com.github.zly2006.zhihu.ui.subscreens.SYSTEM_SETTINGS_REMINDER_INTERVAL_TAG
import com.github.zly2006.zhihu.ui.subscreens.SystemAndUpdateSettingsScreen
import com.github.zly2006.zhihu.updater.SchematicVersion
import com.github.zly2006.zhihu.updater.UpdateManager
import com.github.zly2006.zhihu.updater.UpdateManager.UpdateState
import com.github.zly2006.zhihu.util.ContinuousUsageReminderManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemAndUpdateSettingsScreenInstrumentedTest {
    @get:Rule
    val composeRule: MainActivityComposeRule = createAndroidComposeRule<MainActivity>()

    private val preferences: SharedPreferences
        get() = composeRule.activity.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        composeRule.resetAppPreferences()
        UpdateManager.setAutoCheckEnabled(composeRule.activity, true)
        preferences
            .edit()
            .putBoolean(CHECK_NIGHTLY_UPDATES_PREFERENCE_KEY, false)
            .putInt(ContinuousUsageReminderManager.KEY_CONTINUOUS_USAGE_REMINDER_INTERVAL_MINUTES, 0)
            .commit()
        UpdateManager.updateState.value = UpdateState.NoUpdate
        composeRule.waitForIdle()
    }

    @After
    fun tearDown() {
        UpdateManager.updateState.value = UpdateState.NoUpdate
        composeRule.waitForIdle()
    }

    @Test
    fun updateBannerButtonsAndBackNavigationStayDeterministicWithoutNetwork() {
        // This test seeds the banner entirely from the in-memory UpdateManager state flow so the
        // screen renders a fixed "update available" path without touching GitHub or any other
        // network source, then verifies that the skip and reset buttons both produce the exact
        // local state transitions the settings screen is responsible for handling.
        val seededVersion = SchematicVersion.fromString("9.9.9")
        UpdateManager.updateState.value = UpdateState.UpdateAvailable(
            version = seededVersion,
            releaseNotes = "修复若干设置项细节\nhttps://github.com/zhihu-plus/android/pull/321",
            downloadUrl = "https://example.com/app-lite-debug.apk",
            cnDownloadUrl = null,
        )
        val navigator = setUpScreen()
        val scrollContainer = scrollContainer()

        waitUntilDisplayed(hasText("新版本：\n9.9.9"))
        waitUntilDisplayed(hasText("更新内容"))
        waitUntilDisplayed(hasText("修复若干设置项细节", substring = true))

        composeRule.onNodeWithText("跳过此版本").performClick()
        waitUntil(timeoutMillis = 5_000) {
            preferences.getString(SKIPPED_VERSION_PREFERENCE_KEY, null) == seededVersion.toString()
        }
        waitUntil(timeoutMillis = 5_000) { UpdateManager.updateState.value == UpdateState.Latest }
        // 跳过横幅后，检查按钮在存档等设置下方，必须先滚进视口再断言可见。
        scrollContainer.performScrollToNode(hasText("已经是最新版本"))
        composeRule.onNodeWithText("已经是最新版本").assertIsDisplayed()

        // Tapping the "already latest" button must only clear the locally-seeded Latest state back
        // to NoUpdate. The test intentionally does not tap the real network-backed check button.
        composeRule.onNodeWithText("已经是最新版本").performClick()
        waitUntil(timeoutMillis = 5_000) { UpdateManager.updateState.value == UpdateState.NoUpdate }
        scrollContainer.performScrollToNode(hasText("检查更新"))
        composeRule.onNodeWithText("检查更新").assertIsDisplayed()

        // Back navigation is part of the same screen contract, so one final click proves the shared
        // navigator still receives exactly one back event after the banner state changes above.
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.runOnIdle {
            assertEquals(1, navigator.backCount)
        }
    }

    @Test
    fun reminderIntervalDropdownPersistsSelectionAcrossDeterministicScrolls() {
        // This test exercises the lower "continuous usage reminder" section using only seeded local
        // preferences: scroll to the dropdown with semantics-driven scrolling, change the selection,
        // scroll away to a bottom link, and then scroll back to prove that both the visible label and
        // the stored interval remain aligned without depending on account state or live responses.
        setUpScreen()
        val scrollContainer = scrollContainer()

        waitUntilDisplayed(hasText("自动检查更新"))
        scrollContainer.performScrollToNode(hasTestTag(SYSTEM_SETTINGS_REMINDER_INTERVAL_TAG))
        composeRule.onNodeWithText("防沉迷提醒").assertIsDisplayed()
        composeRule.onNodeWithTag(SYSTEM_SETTINGS_REMINDER_INTERVAL_TAG).performClick()
        waitUntilDisplayed(hasText("每 30 分钟"))
        composeRule.onNode(hasText("每 30 分钟"), useUnmergedTree = true).performClick()

        waitUntilIntPreference(
            ContinuousUsageReminderManager.KEY_CONTINUOUS_USAGE_REMINDER_INTERVAL_MINUTES,
            expected = 30,
        )
        composeRule.onNodeWithText("每 30 分钟").assertIsDisplayed()

        scrollContainer.performScrollToNode(hasText("Github issue"))
        composeRule.onNodeWithText("Github issue").assertIsDisplayed()
        scrollContainer.performScrollToNode(hasText("防沉迷提醒"))
        composeRule.onNodeWithText("每 30 分钟").assertIsDisplayed()
        assertEquals(
            30,
            preferences.getInt(
                ContinuousUsageReminderManager.KEY_CONTINUOUS_USAGE_REMINDER_INTERVAL_MINUTES,
                -1,
            ),
        )
    }

    @Test
    fun toggleRowsRemainStableAfterSwipeCycleAndSemanticsScrolls() {
        // This test seeds all toggle-backed preferences to known values, flips each row through the
        // settings screen itself, performs both gesture-based and semantics-based scrolling, and then
        // verifies that the final persisted values still match the exact toggles the user selected.
        UpdateManager.setAutoCheckEnabled(composeRule.activity, false)
        preferences
            .edit()
            .putBoolean(CHECK_NIGHTLY_UPDATES_PREFERENCE_KEY, false)
            .commit()
        setUpScreen()
        val scrollContainer = scrollContainer()

        waitUntilDisplayed(hasText("自动检查更新"))
        assertFalse(UpdateManager.isAutoCheckEnabled(composeRule.activity))
        assertFalse(preferences.getBoolean(CHECK_NIGHTLY_UPDATES_PREFERENCE_KEY, true))

        clickSettingRow("自动检查更新")
        waitUntil(timeoutMillis = 5_000) { UpdateManager.isAutoCheckEnabled(composeRule.activity) }

        clickSettingRow("检查 Nightly 版本更新")
        waitUntilBooleanPreference(CHECK_NIGHTLY_UPDATES_PREFERENCE_KEY, expected = true)

        scrollContainer.performVerticalSwipeCycle()
        scrollContainer.performScrollToNode(hasText("Github issue"))
        composeRule.onNodeWithText("Github issue").assertIsDisplayed()

        scrollContainer.performScrollToNode(hasText("检查 Nightly 版本更新"))
        composeRule.onNodeWithText("检查 Nightly 版本更新").assertIsDisplayed()
        assertTrue(UpdateManager.isAutoCheckEnabled(composeRule.activity))
        assertTrue(preferences.getBoolean(CHECK_NIGHTLY_UPDATES_PREFERENCE_KEY, false))
    }

    @Test
    fun archiveServerSettingsPersistUrlTokenAndEnabledSwitch() {
        setUpScreen()
        val scrollContainer = scrollContainer()

        scrollContainer.performScrollToNode(hasText("启用存档服务器"))
        composeRule.onNodeWithText("启用存档服务器").assertIsDisplayed()
        assertFalse(preferences.getBoolean(ARCHIVE_SERVER_ENABLED_PREFERENCE_KEY, false))

        scrollContainer.performScrollToNode(hasText("存档服务器地址"))
        composeRule.onNodeWithTag(SYSTEM_SETTINGS_ARCHIVE_URL_TAG).performTextInput("http://192.168.0.10:32100")
        composeRule.onNodeWithTag(SYSTEM_SETTINGS_ARCHIVE_TOKEN_TAG).performTextInput("local-dev-token")
        waitUntil(timeoutMillis = 5_000) {
            preferences.getString(ARCHIVE_SERVER_URL_PREFERENCE_KEY, "") == "http://192.168.0.10:32100" &&
                preferences.getString(ARCHIVE_SERVER_TOKEN_PREFERENCE_KEY, "") == "local-dev-token"
        }

        scrollContainer.performScrollToNode(hasText("启用存档服务器"))
        clickSettingRow("启用存档服务器")
        waitUntilBooleanPreference(ARCHIVE_SERVER_ENABLED_PREFERENCE_KEY, expected = true)
    }

    @Test
    fun localArchiveSwitchPersistsWithoutServer() {
        setUpScreen()
        val scrollContainer = scrollContainer()

        scrollContainer.performScrollToNode(hasText("启用本地存档"))
        composeRule.onNodeWithText("启用本地存档").assertIsDisplayed()
        assertFalse(preferences.getBoolean(LOCAL_ARCHIVE_ENABLED_PREFERENCE_KEY, false))

        clickSettingRow("启用本地存档")
        waitUntilBooleanPreference(LOCAL_ARCHIVE_ENABLED_PREFERENCE_KEY, expected = true)

        scrollContainer.performScrollToNode(hasText("导入 / 导出本地存档"))
        composeRule.onNodeWithText("导入 / 导出本地存档").assertIsDisplayed()
        assertFalse(preferences.getBoolean(ARCHIVE_SERVER_ENABLED_PREFERENCE_KEY, false))
    }

    @Test
    fun localAndServerArchiveStrategiesPersistIndependently() {
        setUpScreen()
        val scrollContainer = scrollContainer()

        scrollContainer.performScrollToNode(hasText("启用本地存档"))
        clickSettingRow("启用本地存档")
        waitUntilBooleanPreference(LOCAL_ARCHIVE_ENABLED_PREFERENCE_KEY, expected = true)

        scrollContainer.performScrollToNode(hasTestTag(SYSTEM_SETTINGS_LOCAL_ARCHIVE_STRATEGY_TAG))
        composeRule.onNodeWithText("本地保存策略").assertIsDisplayed()
        composeRule.onNodeWithTag(SYSTEM_SETTINGS_LOCAL_ARCHIVE_STRATEGY_TAG).performClick()
        waitUntilDisplayed(hasText("所有点赞的问答"))
        composeRule.onNode(hasText("所有点赞的问答"), useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 5_000) {
            preferences.getString(LOCAL_ARCHIVE_SAVE_STRATEGY_PREFERENCE_KEY, "") == "voted"
        }

        scrollContainer.performScrollToNode(hasText("存档服务器地址"))
        composeRule.onNodeWithTag(SYSTEM_SETTINGS_ARCHIVE_URL_TAG).performTextInput("http://192.168.0.10:32100")
        composeRule.onNodeWithTag(SYSTEM_SETTINGS_ARCHIVE_TOKEN_TAG).performTextInput("local-dev-token")
        waitUntil(timeoutMillis = 5_000) {
            preferences.getString(ARCHIVE_SERVER_URL_PREFERENCE_KEY, "") == "http://192.168.0.10:32100" &&
                preferences.getString(ARCHIVE_SERVER_TOKEN_PREFERENCE_KEY, "") == "local-dev-token"
        }
        scrollContainer.performScrollToNode(hasText("启用存档服务器"))
        clickSettingRow("启用存档服务器")
        waitUntilBooleanPreference(ARCHIVE_SERVER_ENABLED_PREFERENCE_KEY, expected = true)

        scrollContainer.performScrollToNode(hasTestTag(SYSTEM_SETTINGS_ARCHIVE_SERVER_STRATEGY_TAG))
        composeRule.onNodeWithText("服务器保存策略").assertIsDisplayed()
        composeRule.onNodeWithTag(SYSTEM_SETTINGS_ARCHIVE_SERVER_STRATEGY_TAG).performClick()
        waitUntilDisplayed(hasText("所有收藏的问答"))
        composeRule.onNode(hasText("所有收藏的问答"), useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 5_000) {
            preferences.getString(ARCHIVE_SERVER_SAVE_STRATEGY_PREFERENCE_KEY, "") == "collected"
        }

        assertEquals("voted", preferences.getString(LOCAL_ARCHIVE_SAVE_STRATEGY_PREFERENCE_KEY, ""))
        assertEquals("collected", preferences.getString(ARCHIVE_SERVER_SAVE_STRATEGY_PREFERENCE_KEY, ""))
    }

    @Test
    fun localArchiveForwardSettingsPersistWithoutEnablingRealtimeServer() {
        setUpScreen()
        val scrollContainer = scrollContainer()

        scrollContainer.performScrollToNode(hasText("启用本地存档"))
        clickSettingRow("启用本地存档")
        waitUntilBooleanPreference(LOCAL_ARCHIVE_ENABLED_PREFERENCE_KEY, expected = true)

        scrollContainer.performScrollToNode(hasText("存档服务器地址"))
        composeRule.onNodeWithTag(SYSTEM_SETTINGS_ARCHIVE_URL_TAG).performTextInput("http://192.168.0.10:32100")
        composeRule.onNodeWithTag(SYSTEM_SETTINGS_ARCHIVE_TOKEN_TAG).performTextInput("local-dev-token")
        waitUntil(timeoutMillis = 5_000) {
            preferences.getString(ARCHIVE_SERVER_URL_PREFERENCE_KEY, "") == "http://192.168.0.10:32100" &&
                preferences.getString(ARCHIVE_SERVER_TOKEN_PREFERENCE_KEY, "") == "local-dev-token"
        }

        scrollContainer.performScrollToNode(hasTestTag(SYSTEM_SETTINGS_LOCAL_ARCHIVE_FORWARD_TAG))
        composeRule.onNodeWithText("转发保存到服务器").assertIsDisplayed()
        clickSettingRow("转发保存到服务器")
        waitUntilBooleanPreference(LOCAL_ARCHIVE_FORWARD_ENABLED_PREFERENCE_KEY, expected = true)

        scrollContainer.performScrollToNode(hasTestTag(SYSTEM_SETTINGS_LOCAL_ARCHIVE_FORWARD_DELETE_TAG))
        composeRule.onNodeWithText("转发成功后删除本地记录").assertIsDisplayed()
        clickSettingRow("转发成功后删除本地记录")
        waitUntilBooleanPreference(LOCAL_ARCHIVE_FORWARD_DELETE_PREFERENCE_KEY, expected = true)

        scrollContainer.performScrollToNode(hasTestTag(SYSTEM_SETTINGS_LOCAL_ARCHIVE_FORWARD_NOW_TAG))
        composeRule.onNodeWithText("立即转发").assertIsDisplayed()
        assertFalse(preferences.getBoolean(ARCHIVE_SERVER_ENABLED_PREFERENCE_KEY, false))
    }

    private fun setUpScreen() = composeRule.setScreenContent {
        SystemAndUpdateSettingsScreen()
    }

    private fun scrollContainer() = composeRule.onNode(hasScrollAction())

    private fun clickSettingRow(title: String) {
        val rowMatcher = hasAnyDescendant(hasText(title)) and hasClickAction()
        waitUntilDisplayed(rowMatcher)
        composeRule.onNode(rowMatcher, useUnmergedTree = true).performClick()
    }

    private fun waitUntilDisplayed(matcher: SemanticsMatcher, timeoutMillis: Long = 5_000) {
        waitUntil(timeoutMillis) { isDisplayed(matcher) }
        composeRule.onNode(matcher, useUnmergedTree = true).assertIsDisplayed()
    }

    private fun waitUntilBooleanPreference(key: String, expected: Boolean, timeoutMillis: Long = 5_000) {
        waitUntil(timeoutMillis) { preferences.getBoolean(key, !expected) == expected }
    }

    private fun waitUntilIntPreference(key: String, expected: Int, timeoutMillis: Long = 5_000) {
        waitUntil(timeoutMillis) { preferences.getInt(key, Int.MIN_VALUE) == expected }
    }

    private fun waitUntil(timeoutMillis: Long = 5_000, condition: () -> Boolean) {
        composeRule.waitUntil(timeoutMillis) { condition() }
    }

    private fun isDisplayed(matcher: SemanticsMatcher): Boolean = runCatching {
        composeRule.onNode(matcher, useUnmergedTree = true).assertIsDisplayed()
    }.isSuccess

    private companion object {
        const val CHECK_NIGHTLY_UPDATES_PREFERENCE_KEY = "checkNightlyUpdates"
        const val SKIPPED_VERSION_PREFERENCE_KEY = "skippedVersion"
    }
}
