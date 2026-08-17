package com.github.zly2006.zhihu.viewmodel.archive

import com.github.zly2006.zhihu.data.ArchiveClient
import com.github.zly2006.zhihu.data.ArchiveSaveStrategy
import com.github.zly2006.zhihu.data.ArchiveSaveTrigger
import com.github.zly2006.zhihu.data.LocalArchiveForwardGate
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.createArchiveItem
import com.github.zly2006.zhihu.data.forwardPendingLocalArchives
import com.github.zly2006.zhihu.data.isArchiveServerUnavailable
import com.github.zly2006.zhihu.data.toLocalArchiveRecord
import com.github.zly2006.zhihu.viewmodel.ArchiveEnvironment
import com.github.zly2006.zhihu.viewmodel.persistArchive
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalArchiveForwardTest {
    @Test
    fun classifierTreatsNetworkAndTokenAsUnavailableButKeepsItemErrorsRetryable() {
        assertTrue(isArchiveServerUnavailable(RuntimeException("Connection refused")))
        assertTrue(isArchiveServerUnavailable(IllegalStateException("invalid token")))
        assertFalse(isArchiveServerUnavailable(IllegalStateException("content too short")))
    }

    @Test
    fun cooldownBlocksRetryUntilForceOrExpiry() = runTest {
        val gate = LocalArchiveForwardGate(cooldownMs = 1_000)
        assertTrue(gate.canAttempt(now = 10, force = false))
        gate.markUnavailable(now = 10, reason = "offline")
        assertFalse(gate.canAttempt(now = 500, force = false))
        assertTrue(gate.canAttempt(now = 500, force = true))
        gate.markUnavailable(now = 10, reason = "offline")
        assertTrue(gate.canAttempt(now = 1_010, force = false))
    }

    @Test
    fun forwardMarksPendingAndSkipsAlreadySynced() = runTest {
        val database = testDatabase("mark")
        val dao = database.localArchiveDao()
        dao.upsertPreservingCreatedAt(sampleRecord("1", now = 1000))
        dao.upsertPreservingCreatedAt(sampleRecord("2", now = 2000).copy(forwardedAt = 2000))
        val posts = AtomicInteger(0)
        val result = forwardPendingLocalArchives(
            dao = dao,
            client = recordingClient(posts),
            deleteAfterSync = false,
            gate = LocalArchiveForwardGate(),
            now = 3000,
        )
        assertEquals(1, result.submitted)
        assertEquals(0, result.deleted)
        assertEquals(0, result.pending)
        assertEquals(1, posts.get())
        assertEquals(3000, dao.getByNormalizedUrl(sampleRecord("1").normalizedUrl)?.forwardedAt)
        assertEquals(2000, dao.getByNormalizedUrl(sampleRecord("2").normalizedUrl)?.forwardedAt)
        database.close()
    }

    @Test
    fun forwardDeletesLocalWhenConfigured() = runTest {
        val database = testDatabase("delete")
        val dao = database.localArchiveDao()
        dao.upsertPreservingCreatedAt(sampleRecord("1", now = 1000))
        val result = forwardPendingLocalArchives(
            dao = dao,
            client = recordingClient(AtomicInteger(0)),
            deleteAfterSync = true,
            gate = LocalArchiveForwardGate(),
            now = 3000,
        )
        assertEquals(1, result.submitted)
        assertEquals(1, result.deleted)
        assertEquals(0, dao.count())
        database.close()
    }

    @Test
    fun unavailableServerStopsBatchAndDoesNotRetryRemaining() = runTest {
        val database = testDatabase("unavailable")
        val dao = database.localArchiveDao()
        dao.upsertPreservingCreatedAt(sampleRecord("1", now = 1000))
        dao.upsertPreservingCreatedAt(sampleRecord("2", now = 2000))
        val posts = AtomicInteger(0)
        val gate = LocalArchiveForwardGate(cooldownMs = 60_000)
        val first = forwardPendingLocalArchives(
            dao = dao,
            client = failingThenOkClient(posts, failFirst = 1),
            deleteAfterSync = false,
            gate = gate,
            now = 3000,
        )
        assertTrue(first.skippedUnavailable)
        assertEquals(0, first.submitted)
        assertEquals(1, posts.get())
        assertEquals(2, first.pending)

        val second = forwardPendingLocalArchives(
            dao = dao,
            client = recordingClient(posts),
            deleteAfterSync = false,
            gate = gate,
            now = 4000,
        )
        assertTrue(second.skippedUnavailable)
        assertEquals(1, posts.get())
        assertEquals(2, dao.pendingForwardCount())
        database.close()
    }

    @Test
    fun itemErrorSkipsOneRecordAndContinues() = runTest {
        val database = testDatabase("item-error")
        val dao = database.localArchiveDao()
        dao.upsertPreservingCreatedAt(sampleRecord("1", now = 1000))
        dao.upsertPreservingCreatedAt(sampleRecord("2", now = 2000))
        val posts = AtomicInteger(0)
        val result = forwardPendingLocalArchives(
            dao = dao,
            client = itemErrorThenOkClient(posts),
            deleteAfterSync = false,
            gate = LocalArchiveForwardGate(),
            now = 3000,
        )
        assertFalse(result.skippedUnavailable)
        assertEquals(1, result.submitted)
        assertEquals(1, result.pending)
        assertEquals(2, posts.get())
        database.close()
    }

    @Test
    fun contentChangeResetsForwardedAt() = runTest {
        val database = testDatabase("reset")
        val dao = database.localArchiveDao()
        val first = sampleRecord("1", now = 1000).copy(forwardedAt = 1000)
        dao.upsert(first)
        dao.upsertPreservingCreatedAt(first.copy(title = "新标题", updatedAt = 2000, forwardedAt = 9999))
        val stored = requireNotNull(dao.getByNormalizedUrl(first.normalizedUrl))
        assertEquals(0, stored.forwardedAt)
        assertEquals(1000, stored.createdAt)
        assertEquals(1, dao.pendingForwardCount())
        database.close()
    }

    @Test
    fun persistArchiveServerSuccessMarksLocalWithoutSecondPost() = runTest {
        val database = testDatabase("persist-mark")
        val posts = AtomicInteger(0)
        val environment = ForwardArchiveEnvironment(
            dao = database.localArchiveDao(),
            client = recordingClient(posts),
            localStrategy = ArchiveSaveStrategy.Read,
            serverStrategy = ArchiveSaveStrategy.Read,
            forwardEnabled = true,
            forwardClient = recordingClient(posts),
        )
        persistArchive(environment, sampleItem("1"), ArchiveSaveTrigger.Read)
        assertEquals(1, posts.get())
        assertEquals(0, database.localArchiveDao().pendingForwardCount())
        assertEquals(1, database.localArchiveDao().count())
        database.close()
    }

    @Test
    fun persistArchiveForwardsLocalWhenServerPersistIsOff() = runTest {
        val database = testDatabase("persist-forward")
        val posts = AtomicInteger(0)
        val environment = ForwardArchiveEnvironment(
            dao = database.localArchiveDao(),
            client = null,
            localStrategy = ArchiveSaveStrategy.Read,
            serverStrategy = ArchiveSaveStrategy.Read,
            forwardEnabled = true,
            forwardClient = recordingClient(posts),
        )
        persistArchive(environment, sampleItem("1"), ArchiveSaveTrigger.Read)
        assertEquals(1, posts.get())
        assertEquals(0, database.localArchiveDao().pendingForwardCount())
        database.close()
    }

    @Test
    fun persistArchiveServerFailureEntersCooldownAndSkipsForward() = runTest {
        val database = testDatabase("persist-fail")
        val posts = AtomicInteger(0)
        val gate = LocalArchiveForwardGate(cooldownMs = 60_000)
        val environment = ForwardArchiveEnvironment(
            dao = database.localArchiveDao(),
            client = failingThenOkClient(posts, failFirst = 99),
            localStrategy = ArchiveSaveStrategy.Read,
            serverStrategy = ArchiveSaveStrategy.Read,
            forwardEnabled = true,
            forwardClient = recordingClient(posts),
            gate = gate,
        )
        persistArchive(environment, sampleItem("1"), ArchiveSaveTrigger.Read)
        persistArchive(environment, sampleItem("2"), ArchiveSaveTrigger.Read)
        assertEquals(1, posts.get())
        assertEquals(2, database.localArchiveDao().pendingForwardCount())
        database.close()
    }

    private fun sampleItem(id: String) = requireNotNull(
        createArchiveItem(
            type = "answer",
            title = "转发问答 $id",
            contentHtml = "<p>这是一段足够长的回答正文，用来通过存档服务器要求的三十字以上长度校验。</p>",
            questionId = id,
            answerId = id,
        ),
    )

    private fun sampleRecord(
        id: String,
        now: Long = 1000,
    ) = sampleItem(id).toLocalArchiveRecord(now)

    private fun recordingClient(serverPosts: AtomicInteger): ArchiveClient = ArchiveClient(
        httpClient = HttpClient(
            MockEngine {
                serverPosts.incrementAndGet()
                respond(
                    content = """{"ok":true,"id":1}""",
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
        token = "token",
    )

    private fun failingThenOkClient(
        serverPosts: AtomicInteger,
        failFirst: Int,
    ): ArchiveClient = ArchiveClient(
        httpClient = HttpClient(
            MockEngine {
                val count = serverPosts.incrementAndGet()
                if (count <= failFirst) {
                    throw IOException("Connection refused")
                }
                respond(
                    content = """{"ok":true,"id":1}""",
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
        token = "token",
    )

    private fun itemErrorThenOkClient(serverPosts: AtomicInteger): ArchiveClient = ArchiveClient(
        httpClient = HttpClient(
            MockEngine {
                val count = serverPosts.incrementAndGet()
                if (count == 1) {
                    respond(
                        content = """{"ok":false,"error":"content too short"}""",
                        status = HttpStatusCode.BadRequest,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                } else {
                    respond(
                        content = """{"ok":true,"id":1}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            },
        ) {
            install(ContentNegotiation) {
                json(ZhihuJson.json)
            }
        },
        baseUrl = "http://127.0.0.1:32100",
        token = "token",
    )

    private fun testDatabase(name: String): LocalArchiveDatabase =
        getLocalArchiveDatabase(
            createTempDirectory("local-archive-forward-$name").resolve("local-archive.db").toFile(),
        )
}

private class ForwardArchiveEnvironment(
    private val dao: LocalArchiveDao?,
    private val client: ArchiveClient?,
    private val localStrategy: ArchiveSaveStrategy,
    private val serverStrategy: ArchiveSaveStrategy,
    private val forwardEnabled: Boolean = false,
    private val deleteAfterSync: Boolean = false,
    private val forwardClient: ArchiveClient? = null,
    private val gate: LocalArchiveForwardGate = LocalArchiveForwardGate(),
) : ArchiveEnvironment {
    override fun archiveClient(): ArchiveClient? = client

    override fun archiveForwardClient(): ArchiveClient? = forwardClient

    override fun localArchiveDao(): LocalArchiveDao? = dao

    override fun localArchiveSaveStrategy(): ArchiveSaveStrategy = localStrategy

    override fun archiveServerSaveStrategy(): ArchiveSaveStrategy = serverStrategy

    override fun localArchiveForwardEnabled(): Boolean = forwardEnabled

    override fun localArchiveForwardDeleteAfterSync(): Boolean = deleteAfterSync

    override fun localArchiveForwardGate(): LocalArchiveForwardGate = gate
}
