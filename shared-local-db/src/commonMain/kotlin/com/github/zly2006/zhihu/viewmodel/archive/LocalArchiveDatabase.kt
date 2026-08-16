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

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabase.Builder
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import com.github.zly2006.zhihu.data.applyPlatformDriver
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [LocalArchiveRecord::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(LocalArchiveConverters::class)
@ConstructedBy(LocalArchiveDatabaseConstructor::class)
abstract class LocalArchiveDatabase : RoomDatabase() {
    abstract fun localArchiveDao(): LocalArchiveDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object LocalArchiveDatabaseConstructor : RoomDatabaseConstructor<LocalArchiveDatabase> {
    override fun initialize(): LocalArchiveDatabase
}

expect fun getLocalArchiveDatabase(): LocalArchiveDatabase

fun buildLocalArchiveDatabase(
    builder: Builder<LocalArchiveDatabase>,
): LocalArchiveDatabase = builder
    .applyPlatformDriver()
    .setQueryCoroutineContext(Dispatchers.Default)
    .build()
