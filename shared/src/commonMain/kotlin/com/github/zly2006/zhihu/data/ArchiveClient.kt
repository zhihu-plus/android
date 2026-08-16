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

import com.fleeksoft.ksoup.Ksoup
import com.github.zly2006.zhihu.util.extractImageUrl
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val LOCAL_ARCHIVE_ENABLED_PREFERENCE_KEY = "enableLocalArchive"
const val LOCAL_ARCHIVE_SAVE_STRATEGY_PREFERENCE_KEY = "localArchiveSaveStrategy"
const val ARCHIVE_SERVER_ENABLED_PREFERENCE_KEY = "enableArchiveServer"
const val ARCHIVE_SERVER_SAVE_STRATEGY_PREFERENCE_KEY = "archiveServerSaveStrategy"
const val ARCHIVE_SERVER_URL_PREFERENCE_KEY = "archiveServerUrl"
const val ARCHIVE_SERVER_TOKEN_PREFERENCE_KEY = "archiveServerToken"

enum class ArchiveSaveStrategy(
    val key: String,
) {
    Loaded("loaded"),
    Read("read"),
    Voted("voted"),
    Collected("collected"),
    ;

    fun shouldPersist(trigger: ArchiveSaveTrigger): Boolean = when (trigger) {
        ArchiveSaveTrigger.Loaded -> this == Loaded
        ArchiveSaveTrigger.Read -> this == Read || this == Loaded
        ArchiveSaveTrigger.Voted -> this == Voted
        ArchiveSaveTrigger.Collected -> this == Collected
    }

    companion object {
        val Default = Read

        fun fromKey(raw: String?): ArchiveSaveStrategy = entries.find { it.key == raw } ?: Default
    }
}

enum class ArchiveSaveTrigger {
    Loaded,
    Read,
    Voted,
    Collected,
}

private const val ARCHIVE_TOKEN_HEADER = "X-Zhihu-Backup-Token"
private const val MIN_ARCHIVE_CONTENT_TEXT_LENGTH = 30

class ArchiveClient(
    private val httpClient: HttpClient,
    baseUrl: String,
    private val token: String,
) {
    private val normalizedBaseUrl = baseUrl.trim().trimEnd('/')

    suspend fun saveItem(item: ArchiveItem): ArchiveSaveResponse {
        val response = httpClient
            .post("$normalizedBaseUrl/api/zhihu/items") {
                contentType(ContentType.Application.Json)
                header(ARCHIVE_TOKEN_HEADER, token)
                setBody(item)
            }.body<ArchiveSaveResponse>()
        if (!response.ok) {
            error(response.error ?: "存档失败")
        }
        return response
    }

    suspend fun checkHealth(): Boolean =
        httpClient
            .get("$normalizedBaseUrl/health")
            .body<ArchiveHealthResponse>()
            .ok
}

fun createArchiveClient(
    httpClient: HttpClient,
    baseUrl: String,
    token: String,
): ArchiveClient? {
    val trimmed = baseUrl.trim().trimEnd('/')
    if (trimmed.isBlank()) return null
    val url = if (trimmed.contains("://")) trimmed else "http://$trimmed"
    return ArchiveClient(httpClient, url, token.trim())
}

fun createArchiveItem(content: DataHolder.Content): ArchiveItem? = when (content) {
    is DataHolder.Answer -> createArchiveItem(
        type = "answer",
        title = content.question.title,
        contentHtml = content.content,
        questionId = content.question.id.toString(),
        answerId = content.id.toString(),
        authorName = content.author.name,
        authorUrl = content.author.url,
        authorUrlToken = content.author.urlToken,
    )
    is DataHolder.Article -> createArchiveItem(
        type = "article",
        title = content.title,
        contentHtml = content.content,
        articleId = content.id.toString(),
        authorName = content.author.name,
        authorUrl = content.author.url,
        authorUrlToken = content.author.urlToken,
    )
    is DataHolder.Question -> createArchiveItem(
        type = "question",
        title = content.title,
        contentHtml = content.detail,
        questionId = content.id.toString(),
        authorName = content.author.name,
        authorUrl = content.author.url,
        authorUrlToken = content.author.urlToken,
    )
    else -> null
}

fun createArchiveItem(
    type: String,
    title: String,
    contentHtml: String,
    questionId: String = "",
    answerId: String = "",
    articleId: String = "",
    authorName: String = "",
    authorUrl: String = "",
    authorUrlToken: String = "",
): ArchiveItem? {
    val url = when (type) {
        "answer" -> {
            if (questionId.isBlank() || answerId.isBlank()) return null
            "https://www.zhihu.com/question/$questionId/answer/$answerId"
        }
        "question" -> {
            if (questionId.isBlank()) return null
            "https://www.zhihu.com/question/$questionId"
        }
        "article" -> {
            if (articleId.isBlank()) return null
            "https://zhuanlan.zhihu.com/p/$articleId"
        }
        else -> return null
    }
    val document = Ksoup.parse(contentHtml)
    val contentText = document.text().trim().ifBlank { title.trim() }
    if (contentText.isBlank()) return null
    if (type != "question" && contentText.length <= MIN_ARCHIVE_CONTENT_TEXT_LENGTH) return null

    val images = document
        .select("img")
        .mapNotNull { image ->
            extractImageUrl(image::attr)?.let { src ->
                when {
                    src.startsWith("//") -> "https:$src"
                    src.startsWith("http://") || src.startsWith("https://") -> src
                    else -> null
                }
            }
        }.distinct()

    val resolvedAuthorUrl = when {
        authorUrl.startsWith("http://") || authorUrl.startsWith("https://") -> authorUrl
        authorUrl.startsWith("/") -> "https://www.zhihu.com$authorUrl"
        authorUrlToken.isNotBlank() -> "https://www.zhihu.com/people/$authorUrlToken"
        else -> ""
    }

    return ArchiveItem(
        url = url,
        normalizedUrl = url,
        type = type,
        title = title,
        questionId = questionId,
        answerId = answerId,
        articleId = articleId,
        authorName = authorName,
        authorUrl = resolvedAuthorUrl,
        contentHtml = contentHtml,
        contentText = contentText,
        images = images,
        eventType = "auto",
    )
}

@Serializable
data class ArchiveItem(
    val url: String,
    @SerialName("normalized_url")
    val normalizedUrl: String,
    val type: String,
    val title: String = "",
    @SerialName("question_id")
    val questionId: String = "",
    @SerialName("answer_id")
    val answerId: String = "",
    @SerialName("article_id")
    val articleId: String = "",
    @SerialName("author_name")
    val authorName: String = "",
    @SerialName("author_url")
    val authorUrl: String = "",
    @SerialName("content_html")
    val contentHtml: String = "",
    @SerialName("content_text")
    val contentText: String,
    val images: List<String> = emptyList(),
    @SerialName("event_type")
    val eventType: String = "auto",
)

@Serializable
data class ArchiveSaveResponse(
    val ok: Boolean,
    val id: Long? = null,
    val error: String? = null,
)

@Serializable
data class ArchiveHealthResponse(
    val ok: Boolean,
)
