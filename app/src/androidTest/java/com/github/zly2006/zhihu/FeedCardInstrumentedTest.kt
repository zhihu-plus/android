package com.github.zly2006.zhihu

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.zly2006.zhihu.data.CommonFeed
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.Person
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.test.MainActivityComposeRule
import com.github.zly2006.zhihu.test.resetAppPreferences
import com.github.zly2006.zhihu.test.setScreenContent
import com.github.zly2006.zhihu.ui.components.FEED_CARD_AUTHOR_TAG
import com.github.zly2006.zhihu.ui.components.FEED_CARD_TITLE_TAG
import com.github.zly2006.zhihu.ui.components.FeedCard
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.github.zly2006.zhihu.navigation.Person as PersonDestination

@RunWith(AndroidJUnit4::class)
class FeedCardInstrumentedTest {
    @get:Rule
    val composeRule: MainActivityComposeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        composeRule.resetAppPreferences()
    }

    @Test
    fun answerCardTitleAndAuthorNavigateIndependentlyFromBody() {
        val navigator = composeRule.setScreenContent {
            FeedCard(item = answerItem())
        }

        composeRule.onNodeWithTag(FEED_CARD_TITLE_TAG).performClick()
        assertEquals(listOf(Question(questionId = 2001, title = "测试问题")), navigator.destinations)

        navigator.reset()
        composeRule.onNodeWithTag(FEED_CARD_AUTHOR_TAG).performClick()
        assertEquals(
            listOf(PersonDestination(id = "author-1", urlToken = "author-token", name = "作者")),
            navigator.destinations,
        )

        navigator.reset()
        composeRule.onNodeWithText("回答摘要").performClick()
        val destination = navigator.destinations.single() as Article
        assertEquals(ArticleType.Answer, destination.type)
        assertEquals(1001L, destination.id)
    }

    private fun answerItem() = FeedDisplayItem(
        title = "测试问题",
        summary = "回答摘要",
        details = "回答 · 1 赞同",
        feed = CommonFeed(target = answerTarget()),
        avatarSrc = "https://example.invalid/avatar.png",
        authorName = "作者",
    )

    private fun answerTarget() = Feed.AnswerTarget(
        id = 1001,
        url = "https://www.zhihu.com/question/2001/answer/1001",
        author = Person(
            id = "author-1",
            url = "https://www.zhihu.com/people/author-token",
            userType = "people",
            urlToken = "author-token",
            name = "作者",
            headline = "",
            avatarUrl = "https://example.invalid/avatar.png",
        ),
        question = Feed.QuestionTarget(
            id = 2001,
            _title = "测试问题",
            url = "https://www.zhihu.com/question/2001",
            type = "question",
        ),
        excerpt = "回答摘要",
    )
}
