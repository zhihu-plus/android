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

    @Query("SELECT * FROM ${LocalArchiveRecord.TABLE_NAME} ORDER BY updatedAt DESC")
    suspend fun getAll(): List<LocalArchiveRecord>

    @Query("SELECT COUNT(*) FROM ${LocalArchiveRecord.TABLE_NAME}")
    suspend fun count(): Long

    suspend fun upsertPreservingCreatedAt(record: LocalArchiveRecord) {
        val existing = getByNormalizedUrl(record.normalizedUrl)
        upsert(record.copy(createdAt = existing?.createdAt ?: record.createdAt))
    }

    @Transaction
    suspend fun importRecords(records: List<LocalArchiveRecord>) {
        records.forEach { upsertPreservingCreatedAt(it) }
    }
}
