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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface LocalArchiveDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: LocalArchiveRecord)

    @Query("SELECT * FROM ${LocalArchiveRecord.TABLE_NAME} WHERE normalizedUrl = :normalizedUrl")
    suspend fun getByNormalizedUrl(normalizedUrl: String): LocalArchiveRecord?

    @Query(
        """
        SELECT * FROM ${LocalArchiveRecord.TABLE_NAME}
        WHERE answerId = :answerId AND type = 'answer'
        ORDER BY updatedAt DESC
        LIMIT 1
        """,
    )
    suspend fun getByAnswerId(answerId: String): LocalArchiveRecord?

    @Query(
        """
        SELECT * FROM ${LocalArchiveRecord.TABLE_NAME}
        WHERE articleId = :articleId AND type = 'article'
        ORDER BY updatedAt DESC
        LIMIT 1
        """,
    )
    suspend fun getByArticleId(articleId: String): LocalArchiveRecord?

    @Query("SELECT * FROM ${LocalArchiveRecord.TABLE_NAME} ORDER BY updatedAt DESC")
    suspend fun getAll(): List<LocalArchiveRecord>

    @Query("SELECT COUNT(*) FROM ${LocalArchiveRecord.TABLE_NAME}")
    suspend fun count(): Long

    @Query(
        """
        SELECT * FROM ${LocalArchiveRecord.TABLE_NAME}
        WHERE forwardedAt = 0
        ORDER BY updatedAt ASC
        LIMIT :limit
        """,
    )
    suspend fun getPendingForward(limit: Int): List<LocalArchiveRecord>

    @Query(
        """
        SELECT COUNT(*) FROM ${LocalArchiveRecord.TABLE_NAME}
        WHERE forwardedAt = 0
        """,
    )
    suspend fun pendingForwardCount(): Long

    @Query(
        """
        UPDATE ${LocalArchiveRecord.TABLE_NAME}
        SET forwardedAt = :forwardedAt
        WHERE normalizedUrl = :normalizedUrl
        """,
    )
    suspend fun markForwarded(
        normalizedUrl: String,
        forwardedAt: Long,
    )

    @Query("DELETE FROM ${LocalArchiveRecord.TABLE_NAME} WHERE normalizedUrl = :normalizedUrl")
    suspend fun deleteByNormalizedUrl(normalizedUrl: String)

    suspend fun upsertPreservingCreatedAt(record: LocalArchiveRecord) {
        val existing = getByNormalizedUrl(record.normalizedUrl)
        val sameContent = existing != null &&
            existing.copy(createdAt = 0, updatedAt = 0, forwardedAt = 0) ==
            record.copy(createdAt = 0, updatedAt = 0, forwardedAt = 0)
        upsert(
            record.copy(
                createdAt = existing?.createdAt ?: record.createdAt,
                forwardedAt = existing?.takeIf { sameContent }?.forwardedAt ?: 0L,
            ),
        )
    }

    @Transaction
    suspend fun importRecords(records: List<LocalArchiveRecord>) {
        records.forEach { upsertPreservingCreatedAt(it) }
    }
}
