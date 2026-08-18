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

import com.github.zly2006.zhihu.viewmodel.archive.LocalArchiveDao
import com.github.zly2006.zhihu.viewmodel.archive.LocalArchiveRecord
import com.github.zly2006.zhihu.viewmodel.archive.getLocalArchiveDatabase

/** 阅读页展示本地副本时的来源标签。 */
const val LOCAL_ARCHIVE_SOURCE_LABEL = "本地存档"

fun localArchiveAnswerUrl(
    questionId: Long,
    answerId: Long,
): String = "https://www.zhihu.com/question/$questionId/answer/$answerId"

fun localArchiveArticleUrl(articleId: Long): String = "https://zhuanlan.zhihu.com/p/$articleId"

suspend fun loadLocalArchiveForAnswer(
    answerId: Long,
    questionId: Long? = null,
    dao: LocalArchiveDao = getLocalArchiveDatabase().localArchiveDao(),
): LocalArchiveRecord? {
    if (answerId <= 0L) return null
    val resolvedQuestionId = questionId?.takeIf { it > 0L }
    if (resolvedQuestionId != null) {
        dao.getByNormalizedUrl(localArchiveAnswerUrl(resolvedQuestionId, answerId))?.let { return it }
    }
    return dao.getByAnswerId(answerId.toString())
}

suspend fun loadLocalArchiveForArticle(
    articleId: Long,
    dao: LocalArchiveDao = getLocalArchiveDatabase().localArchiveDao(),
): LocalArchiveRecord? {
    if (articleId <= 0L) return null
    return dao.getByNormalizedUrl(localArchiveArticleUrl(articleId))
        ?: dao.getByArticleId(articleId.toString())
}
