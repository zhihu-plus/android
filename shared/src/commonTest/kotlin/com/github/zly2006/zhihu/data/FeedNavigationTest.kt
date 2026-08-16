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

package com.github.zly2006.zhihu.data

import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.Search
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import com.github.zly2006.zhihu.navigation.Person as PersonDestination

class FeedNavigationTest {
    @Test
    fun feedDisplayItemRestoresExplicitNavigationJson() {
        val destination = Search(query = "fixture")
        val item = FeedDisplayItem(
            title = "标题",
            summary = "摘要",
            details = "详情",
            feed = null,
            navDestinationJson = destination.toFeedDisplayItemNavDestinationJson(),
        )

        assertEquals(destination, item.navDestination)
        assertEquals(destination.toFeedDisplayItemNavDestinationJson(), item.stableKey)
    }

    @Test
    fun answerFeedTitleOpensQuestionAndAuthorOpensProfile() {
        val item = FeedDisplayItem(
            title = "测试问题",
            summary = "回答摘要",
            details = "回答 · 1 赞同",
            feed = CommonFeed(target = answerTarget()),
            avatarSrc = "https://example.invalid/avatar.png",
            authorName = "作者",
        )

        assertEquals(Article(type = ArticleType.Answer, id = 1001), item.navDestination)
        assertEquals(Question(questionId = 2001, title = "测试问题"), item.questionDestination)
        assertEquals(PersonDestination(id = "author-1", urlToken = "author-token", name = "作者"), item.authorDestination)
    }

    @Test
    fun questionFeedTitleOpensQuestionAndAuthorOpensProfile() {
        val item = FeedDisplayItem(
            title = "测试问题",
            summary = "问题摘要",
            details = "问题 · 1 关注",
            feed = CommonFeed(
                target = Feed.QuestionTarget(
                    id = 2001,
                    _title = "测试问题",
                    url = "https://www.zhihu.com/question/2001",
                    type = "question",
                    author = feedAuthor(),
                ),
            ),
            avatarSrc = "https://example.invalid/avatar.png",
            authorName = "作者",
        )

        assertEquals(Question(questionId = 2001, title = "测试问题"), item.navDestination)
        assertEquals(Question(questionId = 2001, title = "测试问题"), item.questionDestination)
        assertEquals(PersonDestination(id = "author-1", urlToken = "author-token", name = "作者"), item.authorDestination)
    }

    @Test
    fun articleFeedKeepsTitleOnArticleAndStillOpensAuthor() {
        val item = FeedDisplayItem(
            title = "文章标题",
            summary = "文章摘要",
            details = "文章 · 1 赞同",
            feed = CommonFeed(
                target = Feed.ArticleTarget(
                    id = 3001,
                    url = "https://zhuanlan.zhihu.com/p/3001",
                    author = feedAuthor(),
                    title = "文章标题",
                    excerpt = "文章摘要",
                    content = "<p>正文</p>",
                    voteupCount = 1,
                    commentCount = 0,
                    created = 0,
                    updated = 0,
                ),
            ),
            avatarSrc = "https://example.invalid/avatar.png",
            authorName = "作者",
        )

        assertEquals(Article(type = ArticleType.Article, id = 3001), item.navDestination)
        assertNull(item.questionDestination)
        assertEquals(PersonDestination(id = "author-1", urlToken = "author-token", name = "作者"), item.authorDestination)
    }

    @Test
    fun historyItemWithoutFeedHasNoQuestionOrAuthorShortcut() {
        val item = FeedDisplayItem(
            title = "历史回答",
            summary = "摘要",
            details = "详情",
            feed = null,
            navDestinationJson = Article(type = ArticleType.Answer, id = 9).toFeedDisplayItemNavDestinationJson(),
        )

        assertNull(item.questionDestination)
        assertNull(item.authorDestination)
    }

    private fun answerTarget() = Feed.AnswerTarget(
        id = 1001,
        url = "https://www.zhihu.com/question/2001/answer/1001",
        author = feedAuthor(),
        question = Feed.QuestionTarget(
            id = 2001,
            _title = "测试问题",
            url = "https://www.zhihu.com/question/2001",
            type = "question",
        ),
        excerpt = "回答摘要",
    )

    private fun feedAuthor() = Person(
        id = "author-1",
        url = "https://www.zhihu.com/people/author-token",
        userType = "people",
        urlToken = "author-token",
        name = "作者",
        headline = "",
        avatarUrl = "https://example.invalid/avatar.png",
    )
}
