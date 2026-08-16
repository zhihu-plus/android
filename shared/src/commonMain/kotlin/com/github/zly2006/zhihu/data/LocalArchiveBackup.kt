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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

const val LOCAL_ARCHIVE_BACKUP_VERSION = 1

@Serializable
data class LocalArchiveBackup(
    val version: Int = LOCAL_ARCHIVE_BACKUP_VERSION,
    @SerialName("export_time")
    val exportTime: Long = Clock.System.now().toEpochMilliseconds(),
    val items: List<ArchiveItem> = emptyList(),
)

private val localArchiveBackupJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}

fun ArchiveItem.toLocalArchiveRecord(
    now: Long = Clock.System.now().toEpochMilliseconds(),
): LocalArchiveRecord = LocalArchiveRecord(
    normalizedUrl = normalizedUrl,
    url = url,
    type = type,
    title = title,
    questionId = questionId,
    answerId = answerId,
    articleId = articleId,
    authorName = authorName,
    authorUrl = authorUrl,
    contentHtml = contentHtml,
    contentText = contentText,
    images = images,
    eventType = eventType,
    createdAt = now,
    updatedAt = now,
)

fun LocalArchiveRecord.toArchiveItem(): ArchiveItem = ArchiveItem(
    url = url,
    normalizedUrl = normalizedUrl,
    type = type,
    title = title,
    questionId = questionId,
    answerId = answerId,
    articleId = articleId,
    authorName = authorName,
    authorUrl = authorUrl,
    contentHtml = contentHtml,
    contentText = contentText,
    images = images,
    eventType = eventType,
)

suspend fun encodeLocalArchiveBackup(dao: LocalArchiveDao): String {
    val backup = LocalArchiveBackup(
        items = dao.getAll().map { it.toArchiveItem() },
    )
    return localArchiveBackupJson.encodeToString(LocalArchiveBackup.serializer(), backup)
}

suspend fun importLocalArchiveBackupFromJsonText(
    dao: LocalArchiveDao,
    text: String,
): String {
    val backup = localArchiveBackupJson.decodeFromString(LocalArchiveBackup.serializer(), text)
    val now = Clock.System.now().toEpochMilliseconds()
    val records = backup.items.mapNotNull { item ->
        if (item.normalizedUrl.isBlank() || item.contentText.isBlank()) {
            null
        } else {
            item.toLocalArchiveRecord(now)
        }
    }
    dao.importRecords(records)
    val skipped = backup.items.size - records.size
    val total = dao.count()
    return if (skipped > 0) {
        "导入 ${records.size} 条，跳过 $skipped 条，当前共 $total 条"
    } else {
        "导入 ${records.size} 条，当前共 $total 条"
    }
}
