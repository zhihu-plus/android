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

import com.github.zly2006.zhihu.viewmodel.archive.getLocalArchiveDatabase
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalArchiveLoaderTest {
    @Test
    fun loadLocalArchiveForAnswerPrefersNormalizedUrl() = runTest {
        val database = testDatabase("answer-url")
        val dao = database.localArchiveDao()
        val item = requireNotNull(
            createArchiveItem(
                type = "answer",
                title = "问题标题",
                contentHtml = "<p>这是一段足够长的回答正文，用来通过存档服务器要求的三十字以上长度校验。</p>",
                questionId = "100",
                answerId = "200",
            ),
        )
        dao.upsertPreservingCreatedAt(item.toLocalArchiveRecord(now = 1000))

        val loaded = loadLocalArchiveForAnswer(
            answerId = 200L,
            questionId = 100L,
            dao = dao,
        )
        assertEquals("问题标题", loaded?.title)
        database.close()
    }

    @Test
    fun loadLocalArchiveForAnswerFallsBackToAnswerId() = runTest {
        val database = testDatabase("answer-id")
        val dao = database.localArchiveDao()
        val item = requireNotNull(
            createArchiveItem(
                type = "answer",
                title = "仅按回答 ID 查找",
                contentHtml = "<p>这是一段足够长的回答正文，用来通过存档服务器要求的三十字以上长度校验。</p>",
                questionId = "101",
                answerId = "201",
            ),
        )
        dao.upsertPreservingCreatedAt(item.toLocalArchiveRecord(now = 1000))

        val loaded = loadLocalArchiveForAnswer(answerId = 201L, dao = dao)
        assertEquals("仅按回答 ID 查找", loaded?.title)
        database.close()
    }

    @Test
    fun loadLocalArchiveForArticleUsesNormalizedUrl() = runTest {
        val database = testDatabase("article")
        val dao = database.localArchiveDao()
        val item = requireNotNull(
            createArchiveItem(
                type = "article",
                title = "专栏文章",
                contentHtml = "<p>这是一段足够长的文章正文，用来通过存档服务器要求的三十字以上长度校验。</p>",
                articleId = "88",
            ),
        )
        dao.upsertPreservingCreatedAt(item.toLocalArchiveRecord(now = 1000))

        val loaded = loadLocalArchiveForArticle(articleId = 88L, dao = dao)
        assertEquals("专栏文章", loaded?.title)
        database.close()
    }

    @Test
    fun loadLocalArchiveReturnsNullWhenMissing() = runTest {
        val database = testDatabase("missing")
        val dao = database.localArchiveDao()
        assertNull(loadLocalArchiveForAnswer(answerId = 999L, dao = dao))
        assertNull(loadLocalArchiveForArticle(articleId = 999L, dao = dao))
        database.close()
    }

    private fun testDatabase(name: String) =
        getLocalArchiveDatabase(
            createTempDirectory("local-archive-loader-$name").resolve("local-archive.db").toFile(),
        )
}
