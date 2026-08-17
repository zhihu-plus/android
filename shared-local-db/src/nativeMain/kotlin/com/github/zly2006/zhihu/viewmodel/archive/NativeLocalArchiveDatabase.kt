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

import androidx.room.InvalidationTracker

actual fun getLocalArchiveDatabase(): LocalArchiveDatabase = emptyLocalArchiveDatabase

private val emptyLocalArchiveDatabase = object : LocalArchiveDatabase() {
    override fun createInvalidationTracker(): InvalidationTracker =
        InvalidationTracker(
            database = this,
            shadowTablesMap = emptyMap(),
            viewTables = emptyMap(),
            tableNames = emptyArray(),
        )

    override fun localArchiveDao(): LocalArchiveDao = emptyLocalArchiveDao
}

private val emptyLocalArchiveDao = object : LocalArchiveDao {
    override suspend fun upsert(record: LocalArchiveRecord) = Unit

    override suspend fun getByNormalizedUrl(normalizedUrl: String): LocalArchiveRecord? = null

    override suspend fun getAll(): List<LocalArchiveRecord> = emptyList()

    override suspend fun count(): Long = 0L

    override suspend fun getPendingForward(limit: Int): List<LocalArchiveRecord> = emptyList()

    override suspend fun pendingForwardCount(): Long = 0L

    override suspend fun markForwarded(
        normalizedUrl: String,
        forwardedAt: Long,
    ) = Unit

    override suspend fun deleteByNormalizedUrl(normalizedUrl: String) = Unit
}
