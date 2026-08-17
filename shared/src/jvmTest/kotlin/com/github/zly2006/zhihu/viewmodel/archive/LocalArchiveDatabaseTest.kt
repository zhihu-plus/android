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

package com.github.zly2006.zhihu.viewmodel.archive

import com.github.zly2006.zhihu.data.createArchiveItem
import com.github.zly2006.zhihu.data.encodeLocalArchiveBackup
import com.github.zly2006.zhihu.data.importLocalArchiveBackupFromJsonText
import com.github.zly2006.zhihu.data.toLocalArchiveRecord
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalArchiveDatabaseTest {
    @Test
    fun upsertPreservesCreatedAtAndOverwritesContent() = runTest {
        val database = testDatabase("upsert")
        val dao = database.localArchiveDao()
        val first = requireNotNull(
            createArchiveItem(
                type = "answer",
                title = "旧标题",
                contentHtml = "<p>这是一段足够长的回答正文，用来通过存档服务器要求的三十字以上长度校验。</p>",
                questionId = "1",
                answerId = "2",
            ),
        ).toLocalArchiveRecord(now = 1000)
        dao.upsertPreservingCreatedAt(first)

        val updated = first.copy(
            title = "新标题",
            contentText = "更新后的正文仍然足够长，用来覆盖同一条本地存档。",
            createdAt = 9999,
            updatedAt = 2000,
        )
        dao.upsertPreservingCreatedAt(updated)

        val stored = requireNotNull(dao.getByNormalizedUrl(first.normalizedUrl))
        assertEquals("新标题", stored.title)
        assertEquals("更新后的正文仍然足够长，用来覆盖同一条本地存档。", stored.contentText)
        assertEquals(1000, stored.createdAt)
        assertEquals(2000, stored.updatedAt)
        assertEquals(1, dao.count())
        database.close()
    }

    @Test
    fun upsertPreservesForwardedAtWhenContentUnchanged() = runTest {
        val database = testDatabase("forwarded")
        val dao = database.localArchiveDao()
        val first = requireNotNull(
            createArchiveItem(
                type = "answer",
                title = "已转发",
                contentHtml = "<p>这是一段足够长的回答正文，用来通过存档服务器要求的三十字以上长度校验。</p>",
                questionId = "3",
                answerId = "4",
            ),
        ).toLocalArchiveRecord(now = 1000).copy(forwardedAt = 1500)
        dao.upsert(first)
        dao.upsertPreservingCreatedAt(first.copy(updatedAt = 2000, forwardedAt = 0))

        val stored = requireNotNull(dao.getByNormalizedUrl(first.normalizedUrl))
        assertEquals(1500, stored.forwardedAt)
        assertEquals(1000, stored.createdAt)
        assertEquals(2000, stored.updatedAt)
        assertEquals(0, dao.pendingForwardCount())
        database.close()
    }

    @Test
    fun exportAndImportRoundTripByNormalizedUrl() = runTest {
        val source = testDatabase("export")
        val target = testDatabase("import")
        val item = requireNotNull(
            createArchiveItem(
                type = "article",
                title = "专栏",
                contentHtml = "<p>这是一段足够长的文章正文，用来通过存档服务器要求的三十字以上长度校验。</p>",
                articleId = "88",
            ),
        )
        source.localArchiveDao().upsertPreservingCreatedAt(item.toLocalArchiveRecord(now = 3000))

        val json = encodeLocalArchiveBackup(source.localArchiveDao())
        assertTrue(json.contains("\"normalized_url\""))
        val summary = importLocalArchiveBackupFromJsonText(target.localArchiveDao(), json)
        assertTrue(summary.contains("导入 1 条"))

        val stored = target.localArchiveDao().getAll().single()
        assertEquals(item.normalizedUrl, stored.normalizedUrl)
        assertEquals(item.title, stored.title)
        assertEquals(item.contentText, stored.contentText)
        source.close()
        target.close()
    }

    @Test
    fun importSkipsBlankItemsAndKeepsValidOnes() = runTest {
        val database = testDatabase("skip")
        val summary = importLocalArchiveBackupFromJsonText(
            database.localArchiveDao(),
            """
            {
              "version": 1,
              "export_time": 1,
              "items": [
                {
                  "url": "https://www.zhihu.com/question/1",
                  "normalized_url": "https://www.zhihu.com/question/1",
                  "type": "question",
                  "title": "有效问题",
                  "content_text": "问题正文"
                },
                {
                  "url": "",
                  "normalized_url": "",
                  "type": "answer",
                  "content_text": "没有链接"
                }
              ]
            }
            """.trimIndent(),
        )
        assertTrue(summary.contains("导入 1 条"))
        assertTrue(summary.contains("跳过 1 条"))
        assertEquals(1, database.localArchiveDao().count())
        database.close()
    }

    private fun testDatabase(name: String): LocalArchiveDatabase =
        getLocalArchiveDatabase(
            createTempDirectory("local-archive-$name").resolve("local-archive.db").toFile(),
        )
}
