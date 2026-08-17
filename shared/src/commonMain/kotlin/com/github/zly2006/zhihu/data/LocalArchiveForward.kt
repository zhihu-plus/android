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

import com.github.zly2006.zhihu.util.Log
import com.github.zly2006.zhihu.viewmodel.archive.LocalArchiveDao
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

const val LOCAL_ARCHIVE_FORWARD_COOLDOWN_MS = 5 * 60 * 1000L
const val LOCAL_ARCHIVE_AUTO_FORWARD_LIMIT = 20

val sharedLocalArchiveForwardGate = LocalArchiveForwardGate()

class LocalArchiveForwardGate(
    private val cooldownMs: Long = LOCAL_ARCHIVE_FORWARD_COOLDOWN_MS,
) {
    internal val mutex = Mutex()

    @Volatile
    private var unavailableUntilMs: Long = 0

    @Volatile
    var lastReason: String? = null
        private set

    fun canAttempt(
        now: Long,
        force: Boolean,
    ): Boolean {
        if (force) {
            clear()
            return true
        }
        return now >= unavailableUntilMs
    }

    fun markUnavailable(
        now: Long,
        reason: String,
    ) {
        unavailableUntilMs = now + cooldownMs
        lastReason = reason
    }

    fun clear() {
        unavailableUntilMs = 0
        lastReason = null
    }
}

data class LocalArchiveForwardResult(
    val submitted: Int,
    val deleted: Int,
    val pending: Long,
    val skippedUnavailable: Boolean,
    val message: String,
)

fun isArchiveServerUnavailable(error: Throwable): Boolean {
    if (error is CancellationException) throw error
    val message = error.message.orEmpty()
    if (error is IllegalStateException &&
        message.isNotBlank() &&
        !message.contains("token", ignoreCase = true) &&
        !message.contains("unauthorized", ignoreCase = true)
    ) {
        return false
    }
    return true
}

suspend fun forwardPendingLocalArchives(
    dao: LocalArchiveDao,
    client: ArchiveClient,
    deleteAfterSync: Boolean,
    gate: LocalArchiveForwardGate,
    force: Boolean = false,
    now: Long = Clock.System.now().toEpochMilliseconds(),
    limit: Int = Int.MAX_VALUE,
    checkHealth: Boolean = force,
): LocalArchiveForwardResult = gate.mutex.withLock {
    if (!gate.canAttempt(now, force)) {
        return@withLock LocalArchiveForwardResult(
            submitted = 0,
            deleted = 0,
            pending = dao.pendingForwardCount(),
            skippedUnavailable = true,
            message = gate.lastReason?.let { "服务器暂不可用，已暂停自动转发：$it" }
                ?: "服务器暂不可用，已暂停自动转发",
        )
    }
    if (checkHealth) {
        val healthy = runCatching { client.checkHealth() }.getOrElse { error ->
            if (error is CancellationException) throw error
            gate.markUnavailable(now, error.message ?: "健康检查失败")
            return@withLock LocalArchiveForwardResult(
                submitted = 0,
                deleted = 0,
                pending = dao.pendingForwardCount(),
                skippedUnavailable = true,
                message = "服务器不可用，已暂停自动转发：${error.message ?: "健康检查失败"}",
            )
        }
        if (!healthy) {
            gate.markUnavailable(now, "服务器未返回正常状态")
            return@withLock LocalArchiveForwardResult(
                submitted = 0,
                deleted = 0,
                pending = dao.pendingForwardCount(),
                skippedUnavailable = true,
                message = "服务器不可用，已暂停自动转发：未返回正常状态",
            )
        }
    }
    val pending = dao.getPendingForward(limit)
    if (pending.isEmpty()) {
        return@withLock LocalArchiveForwardResult(
            submitted = 0,
            deleted = 0,
            pending = 0,
            skippedUnavailable = false,
            message = "没有待转发的存档",
        )
    }
    var submitted = 0
    var deleted = 0
    for (record in pending) {
        runCatching { client.saveItem(record.toArchiveItem()) }
            .onSuccess {
                submitted += 1
                if (deleteAfterSync) {
                    dao.deleteByNormalizedUrl(record.normalizedUrl)
                    deleted += 1
                } else {
                    dao.markForwarded(record.normalizedUrl, now)
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (isArchiveServerUnavailable(error)) {
                    gate.markUnavailable(now, error.message ?: "提交失败")
                    val remaining = dao.pendingForwardCount()
                    return@withLock LocalArchiveForwardResult(
                        submitted = submitted,
                        deleted = deleted,
                        pending = remaining,
                        skippedUnavailable = true,
                        message = if (submitted == 0) {
                            "服务器不可用，已暂停自动转发：${error.message ?: "未知错误"}"
                        } else {
                            "已转发 $submitted 条后服务器中断，剩余 $remaining 条暂不重试"
                        },
                    )
                }
                Log.w("Archive", "转发单条存档失败，已跳过 ${record.normalizedUrl}", error)
            }
    }
    gate.clear()
    val remaining = dao.pendingForwardCount()
    LocalArchiveForwardResult(
        submitted = submitted,
        deleted = deleted,
        pending = remaining,
        skippedUnavailable = false,
        message = when {
            submitted == 0 -> "没有成功转发的存档，剩余 $remaining 条"
            deleteAfterSync -> "已转发 $submitted 条并删除本地记录，剩余 $remaining 条"
            remaining == 0L -> "已转发 $submitted 条，本地仍保留"
            else -> "已转发 $submitted 条，本地仍保留，剩余 $remaining 条"
        },
    )
}
