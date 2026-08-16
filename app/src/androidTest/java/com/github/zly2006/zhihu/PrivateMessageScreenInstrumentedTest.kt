package com.github.zly2006.zhihu

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.zly2006.zhihu.data.MobileNotificationAuthor
import com.github.zly2006.zhihu.data.ZhihuPrivateMessage
import com.github.zly2006.zhihu.navigation.Notification
import com.github.zly2006.zhihu.test.MainActivityComposeRule
import com.github.zly2006.zhihu.test.resetAppPreferences
import com.github.zly2006.zhihu.test.seedViewModel
import com.github.zly2006.zhihu.test.setScreenContent
import com.github.zly2006.zhihu.ui.PRIVATE_MESSAGE_BODY_TAG_PREFIX
import com.github.zly2006.zhihu.ui.PrivateMessageScreen
import com.github.zly2006.zhihu.viewmodel.PrivateMessageViewModel
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivateMessageScreenInstrumentedTest {
    @get:Rule
    val composeRule: MainActivityComposeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        composeRule.resetAppPreferences()
    }

    @Test
    fun incomingMessageTextCanBeLongPressedToSelect() {
        val message = ZhihuPrivateMessage(
            id = "message-copy-1",
            type = "message",
            content = "打开这个链接 https://www.zhihu.com/question/123",
            createdTime = 1_700_000_000,
            sender = MobileNotificationAuthor(
                id = PEER_ID,
                name = "对方",
                urlToken = PEER_ID,
            ),
        )
        val viewModel = composeRule.seedViewModel(key = "private_message_$PEER_ID") {
            PrivateMessageViewModel(PEER_ID)
        }
        composeRule.activity.runOnUiThread {
            viewModel.allData.add(message)
        }
        composeRule.setScreenContent {
            PrivateMessageScreen(
                Notification.Message(
                    peerId = PEER_ID,
                    name = "对方",
                ),
            )
        }

        val bodyTag = "$PRIVATE_MESSAGE_BODY_TAG_PREFIX${message.stableId}"
        composeRule.waitUntil("私信正文未出现", timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(bodyTag).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("打开这个链接 https://www.zhihu.com/question/123").assertIsDisplayed()
        composeRule.onNodeWithTag(bodyTag).performTouchInput { longClick() }
        composeRule.waitUntil("长按后应出现可选中文本", timeoutMillis = 5_000) {
            val range = composeRule
                .onNodeWithTag(bodyTag)
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.TextSelectionRange)
            range != null && range.length > 0
        }
        val selectedRange = composeRule
            .onNodeWithTag(bodyTag)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.TextSelectionRange)
        assertTrue(selectedRange != null && selectedRange.length > 0)
    }

    private companion object {
        const val PEER_ID = "peer-copy"
    }
}
