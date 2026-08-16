package com.github.zly2006.zhihu.viewmodel.archive

import com.github.zly2006.zhihu.data.ArchiveClient
import com.github.zly2006.zhihu.data.ArchiveSaveStrategy
import com.github.zly2006.zhihu.data.ArchiveSaveTrigger
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.createArchiveItem
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
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class PersistArchiveStrategyTest {
    @Test
    fun localAndServerStrategiesDecideIndependently() = runTest {
        val database = testDatabase("strategy")
        val serverPosts = AtomicInteger(0)
        val environment = StrategyArchiveEnvironment(
            dao = database.localArchiveDao(),
            client = recordingClient(serverPosts),
            localStrategy = ArchiveSaveStrategy.Voted,
            serverStrategy = ArchiveSaveStrategy.Read,
        )
        val item = requireNotNull(
            createArchiveItem(
                type = "answer",
                title = "策略问答",
                contentHtml = "<p>这是一段足够长的回答正文，用来通过存档服务器要求的三十字以上长度校验。</p>",
                questionId = "11",
                answerId = "22",
            ),
        )

        persistArchive(environment, item, ArchiveSaveTrigger.Read)
        assertEquals(0, database.localArchiveDao().count())
        assertEquals(1, serverPosts.get())

        persistArchive(environment, item, ArchiveSaveTrigger.Voted)
        assertEquals(1, database.localArchiveDao().count())
        assertEquals(1, serverPosts.get())

        persistArchive(environment, item, ArchiveSaveTrigger.Collected)
        assertEquals(1, database.localArchiveDao().count())
        assertEquals(1, serverPosts.get())
        database.close()
    }

    @Test
    fun loadedStrategyAlsoSavesReadTrigger() = runTest {
        val database = testDatabase("loaded-read")
        val serverPosts = AtomicInteger(0)
        val environment = StrategyArchiveEnvironment(
            dao = database.localArchiveDao(),
            client = recordingClient(serverPosts),
            localStrategy = ArchiveSaveStrategy.Loaded,
            serverStrategy = ArchiveSaveStrategy.Collected,
        )
        val item = requireNotNull(
            createArchiveItem(
                type = "article",
                title = "加载策略",
                contentHtml = "<p>这是一段足够长的文章正文，用来通过存档服务器要求的三十字以上长度校验。</p>",
                articleId = "33",
            ),
        )

        persistArchive(environment, item, ArchiveSaveTrigger.Read)
        assertEquals(1, database.localArchiveDao().count())
        assertEquals(0, serverPosts.get())
        database.close()
    }

    @Test
    fun loadedTriggerWritesLocalOnlyWhenServerUsesRead() = runTest {
        val database = testDatabase("loaded-trigger")
        val serverPosts = AtomicInteger(0)
        val environment = StrategyArchiveEnvironment(
            dao = database.localArchiveDao(),
            client = recordingClient(serverPosts),
            localStrategy = ArchiveSaveStrategy.Loaded,
            serverStrategy = ArchiveSaveStrategy.Read,
        )
        val item = requireNotNull(
            createArchiveItem(
                type = "answer",
                title = "预取问答",
                contentHtml = "<p>这是一段足够长的回答正文，用来通过存档服务器要求的三十字以上长度校验。</p>",
                questionId = "44",
                answerId = "55",
            ),
        )

        persistArchive(environment, item, ArchiveSaveTrigger.Loaded)
        assertEquals(1, database.localArchiveDao().count())
        assertEquals(0, serverPosts.get())
        database.close()
    }

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

    private fun testDatabase(name: String): LocalArchiveDatabase =
        getLocalArchiveDatabase(
            createTempDirectory("persist-archive-$name").resolve("local-archive.db").toFile(),
        )
}

private class StrategyArchiveEnvironment(
    private val dao: LocalArchiveDao?,
    private val client: ArchiveClient?,
    private val localStrategy: ArchiveSaveStrategy,
    private val serverStrategy: ArchiveSaveStrategy,
) : ArchiveEnvironment {
    override fun archiveClient(): ArchiveClient? = client

    override fun localArchiveDao(): LocalArchiveDao? = dao

    override fun localArchiveSaveStrategy(): ArchiveSaveStrategy = localStrategy

    override fun archiveServerSaveStrategy(): ArchiveSaveStrategy = serverStrategy
}
