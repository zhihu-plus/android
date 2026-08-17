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

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 本机问答存档。主键使用规范化 URL，重复阅读同一内容时覆盖正文并保留首次写入时间。
 */
@Entity(tableName = LocalArchiveRecord.TABLE_NAME)
data class LocalArchiveRecord(
    @PrimaryKey val normalizedUrl: String,
    val url: String,
    val type: String,
    val title: String,
    val questionId: String,
    val answerId: String,
    val articleId: String,
    val authorName: String,
    val authorUrl: String,
    val contentHtml: String,
    val contentText: String,
    val images: List<String>,
    val eventType: String,
    val createdAt: Long,
    val updatedAt: Long,
    /**
     * 成功转发到存档服务器的时间。`0` 表示尚未同步；正文被覆盖后会清零以便再次提交。
     */
    val forwardedAt: Long = 0,
) {
    companion object {
        const val TABLE_NAME = "local_archive_records"
    }
}
