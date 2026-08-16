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

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArchiveClientTest {
    @Test
    fun createArchiveItemBuildsAnswerPayloadFromHtml() {
        val item = createArchiveItem(
            type = "answer",
            title = "什么是存档服务器",
            contentHtml =
                """
                <p>这是一段足够长的回答正文，用来通过存档服务器要求的三十字以上长度校验。</p>
                <img data-original="https://picx.zhimg.com/original.jpg" src="https://picx.zhimg.com/thumb.jpg">
                <img src="//pic1.zhimg.com/relative.jpg">
                """.trimIndent(),
            questionId = "123",
            answerId = "456",
            authorName = "作者甲",
            authorUrl = "/people/author-a",
            authorUrlToken = "author-a",
        )

        assertNotNull(item)
        assertEquals("https://www.zhihu.com/question/123/answer/456", item.url)
        assertEquals(item.url, item.normalizedUrl)
        assertEquals("answer", item.type)
        assertEquals("123", item.questionId)
        assertEquals("456", item.answerId)
        assertEquals("作者甲", item.authorName)
        assertEquals("https://www.zhihu.com/people/author-a", item.authorUrl)
        assertTrue(item.contentText.contains("足够长的回答正文"))
        assertEquals(
            listOf(
                "https://picx.zhimg.com/original.jpg",
                "https://pic1.zhimg.com/relative.jpg",
            ),
            item.images,
        )
        assertEquals("auto", item.eventType)
    }

    @Test
    fun createArchiveItemRejectsShortAnswerAndAcceptsTitledQuestion() {
        assertNull(
            createArchiveItem(
                type = "answer",
                title = "短回答",
                contentHtml = "<p>太短了</p>",
                questionId = "1",
                answerId = "2",
            ),
        )
        val question = createArchiveItem(
            type = "question",
            title = "只有标题的问题",
            contentHtml = "",
            questionId = "99",
            authorUrl = "https://www.zhihu.com/people/asker",
        )
        assertNotNull(question)
        assertEquals("https://www.zhihu.com/question/99", question.url)
        assertEquals("只有标题的问题", question.contentText)
    }

    @Test
    fun saveItemPostsTokenAndSnakeCaseBody() = runTest {
        val client = ArchiveClient(
            httpClient = mockClient { request ->
                assertEquals(HttpMethod.Post, request.method)
                assertEquals("/api/zhihu/items", request.path)
                assertEquals("local-dev-token", request.token)
                assertEquals("https://www.zhihu.com/question/1/answer/2", request.json["url"]!!.jsonPrimitive.content)
                assertEquals("answer", request.json["type"]!!.jsonPrimitive.content)
                assertEquals("1", request.json["question_id"]!!.jsonPrimitive.content)
                assertEquals("2", request.json["answer_id"]!!.jsonPrimitive.content)
                assertEquals("auto", request.json["event_type"]!!.jsonPrimitive.content)
                assertEquals(
                    "https://picx.zhimg.com/a.jpg",
                    request.json["images"]!!
                        .jsonArray
                        .single()
                        .jsonPrimitive.content,
                )
            },
            baseUrl = "http://127.0.0.1:32100/",
            token = "local-dev-token",
        )

        val response = client.saveItem(
            createArchiveItem(
                type = "answer",
                title = "标题",
                contentHtml = "<p>这是一段足够长的回答正文，用来通过存档服务器要求的三十字以上长度校验。</p><img src=\"https://picx.zhimg.com/a.jpg\">",
                questionId = "1",
                answerId = "2",
            )!!,
        )
        assertTrue(response.ok)
        assertEquals(7, response.id)
    }

    @Test
    fun saveItemThrowsWhenServerRejects() = runTest {
        val client = ArchiveClient(
            httpClient = HttpClient(
                MockEngine {
                    respond(
                        content = """{"ok":false,"error":"invalid token"}""",
                        status = HttpStatusCode.Unauthorized,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                },
            ) {
                install(ContentNegotiation) {
                    json(ZhihuJson.json)
                }
            },
            baseUrl = "http://127.0.0.1:32100",
            token = "bad-token",
        )

        val error = assertFailsWith<IllegalStateException> {
            client.saveItem(
                createArchiveItem(
                    type = "article",
                    title = "专栏",
                    contentHtml = "<p>这是一段足够长的文章正文，用来通过存档服务器要求的三十字以上长度校验。</p>",
                    articleId = "88",
                )!!,
            )
        }
        assertEquals("invalid token", error.message)
    }

    @Test
    fun checkHealthDoesNotSendToken() = runTest {
        var sawToken = false
        val client = ArchiveClient(
            httpClient = HttpClient(
                MockEngine { request ->
                    assertEquals(HttpMethod.Get, request.method)
                    assertEquals("/health", request.url.encodedPath)
                    sawToken = request.headers.contains("X-Zhihu-Backup-Token")
                    respond(
                        content = """{"ok":true}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                },
            ) {
                install(ContentNegotiation) {
                    json(ZhihuJson.json)
                }
            },
            baseUrl = "http://127.0.0.1:32100",
            token = "local-dev-token",
        )

        assertTrue(client.checkHealth())
        assertFalse(sawToken)
    }

    @Test
    fun createArchiveClientIgnoresBlankUrl() {
        val httpClient = HttpClient(MockEngine { respond("", HttpStatusCode.OK) })
        assertNull(createArchiveClient(httpClient, "   ", "token"))
        assertNotNull(createArchiveClient(httpClient, " http://127.0.0.1:32100/ ", "token"))
    }

    @Test
    fun createArchiveClientAddsHttpSchemeWhenMissing() = runTest {
        val client = createArchiveClient(
            httpClient = HttpClient(
                MockEngine { request ->
                    assertEquals("http", request.url.protocol.name)
                    assertEquals("192.168.0.10", request.url.host)
                    assertEquals(32100, request.url.port)
                    respond(
                        content = """{"ok":true}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                },
            ) {
                install(ContentNegotiation) {
                    json(ZhihuJson.json)
                }
            },
            baseUrl = "192.168.0.10:32100",
            token = "token",
        )
        assertTrue(assertNotNull(client).checkHealth())
    }

    private fun mockClient(assertBody: (CapturedArchiveRequest) -> Unit): HttpClient = HttpClient(
        MockEngine { request ->
            val bodyText = (request.body as TextContent).text
            assertBody(
                CapturedArchiveRequest(
                    method = request.method,
                    path = request.url.encodedPath,
                    token = request.headers["X-Zhihu-Backup-Token"],
                    json = Json.parseToJsonElement(bodyText).jsonObject,
                ),
            )
            respond(
                content = """{"ok":true,"id":7}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        },
    ) {
        install(ContentNegotiation) {
            json(ZhihuJson.json)
        }
    }
}

private data class CapturedArchiveRequest(
    val method: HttpMethod,
    val path: String,
    val token: String?,
    val json: kotlinx.serialization.json.JsonObject,
)
